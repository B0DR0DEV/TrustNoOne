package ru.bodrodev.trustnoone.server;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.GameMode;
import net.minecraft.world.PersistentStateManager;
import ru.bodrodev.trustnoone.TrustNoOneMod;
import ru.bodrodev.trustnoone.config.TrustNoOneConfig;
import ru.bodrodev.trustnoone.entities.CorpseEntity;
import ru.bodrodev.trustnoone.entities.ImposterEntity;
import ru.bodrodev.trustnoone.entities.ModEntities;
import ru.bodrodev.trustnoone.network.ReviveHoldPayload;
import ru.bodrodev.trustnoone.network.ConfigSyncPayload;
import ru.bodrodev.trustnoone.world.TrustNoOneWorldState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative event handlers: shared lives, revives, sins, darkness and spawning. */
public final class HorrorEvents {
    private static final Map<UUID, Integer> HOLD_TARGETS = new HashMap<>();
    private static final Map<UUID, Integer> REVIVE_PROGRESS = new HashMap<>();
    private static final Map<UUID, Long> LAST_REVIVE_HINT = new HashMap<>();
    private static TrustNoOneWorldState roundState;
    private static long serverTicks;

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            PersistentStateManager manager = server.getOverworld().getPersistentStateManager();
            roundState = manager.getOrCreate(TrustNoOneWorldState.TYPE);
            roundState.initializeIfNeeded(TrustNoOneMod.CONFIG.lives.startingLives);
            HOLD_TARGETS.clear();
            REVIVE_PROGRESS.clear();
            LAST_REVIVE_HINT.clear();
            serverTicks = 0L;
            refreshNameHiding(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            HOLD_TARGETS.clear();
            REVIVE_PROGRESS.clear();
            LAST_REVIVE_HINT.clear();
            roundState = null;
        });
        ServerPlayerEvents.JOIN.register(HorrorEvents::onPlayerJoin);
        ServerPlayerEvents.LEAVE.register(player -> {
            HOLD_TARGETS.remove(player.getUuid());
            REVIVE_PROGRESS.remove(player.getUuid());
            LAST_REVIVE_HINT.remove(player.getUuid());
        });
        ServerPlayNetworking.registerGlobalReceiver(ReviveHoldPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (!payload.holding() || payload.targetEntityId() < 0) {
                HOLD_TARGETS.remove(player.getUuid());
                REVIVE_PROGRESS.remove(player.getUuid());
            } else {
                Integer oldTarget = HOLD_TARGETS.put(player.getUuid(), payload.targetEntityId());
                if (oldTarget == null || oldTarget != payload.targetEntityId()) {
                    REVIVE_PROGRESS.remove(player.getUuid());
                }
                player.sendMessage(Text.literal("TrustNoOne: удерживай ПКМ на теле 2 секунды, не отводя взгляд."), true);
            }
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                entity instanceof CorpseEntity ? ActionResult.SUCCESS : ActionResult.PASS);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity dead) onPlayerDeath(dead);
            else onPossibleSin(entity, source);
        });
        ServerTickEvents.END_SERVER_TICK.register(HorrorEvents::onServerTick);
    }

    private static void onPlayerJoin(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server != null) {
            applyNameHiding(server, player);
            ServerPlayNetworking.send(player, new ConfigSyncPayload(TrustNoOneMod.CONFIG.toJson()));
            if (roundState != null && roundState.isGameOver()) {
                player.changeGameMode(GameMode.SPECTATOR);
                player.sendMessage(Text.literal("TrustNoOne: раунд завершён. Создайте новый мир для новой игры."), false);
            } else if (roundState != null) {
                player.sendMessage(Text.literal("TrustNoOne: общие жизни — " + roundState.getLives()), false);
            }
        }
    }

    private static void onServerTick(MinecraftServer server) {
        if (roundState == null) return;
        serverTicks++;
        if (!roundState.isGameOver()) {
            processRevives(server);
            if (serverTicks % 20L == 0L) applyDarkness(server);
            if (serverTicks % 100L == 0L) applySinConsequences(server);
            int spawnInterval = TrustNoOneMod.CONFIG.imposter.spawnIntervalTicks;
            if (server.getPlayerManager().getCurrentPlayerCount() == 1
                    && TrustNoOneMod.CONFIG.lives.singleplayerTestingMode) {
                spawnInterval = Math.min(spawnInterval, 400);
            }
            if (serverTicks % Math.max(100, spawnInterval) == 0L) trySpawnImposter(server);
        }
        if (serverTicks % 200L == 0L && TrustNoOneMod.CONFIG.names.hidePlayerNames) refreshNameHiding(server);
    }

    private static void onPlayerDeath(ServerPlayerEntity dead) {
        if (roundState == null || roundState.isGameOver()) return;
        ServerWorld world = (ServerWorld) dead.getEntityWorld();
        CorpseEntity corpse = ModEntities.CORPSE.create(world, SpawnReason.TRIGGERED);
        if (corpse != null) {
            corpse.setVictim(dead.getUuid(), dead.getName().getString());
            corpse.refreshPositionAndAngles(dead.getX(), dead.getY(), dead.getZ(), dead.getYaw(), 0.0F);
            world.spawnEntity(corpse);
        }

        MinecraftServer server = world.getServer();
        roundState.setLives(roundState.getLives() - 1);
        broadcast(server, "TrustNoOne: осталось общих жизней — " + roundState.getLives());
        List<ServerPlayerEntity> partners = new ArrayList<>(server.getPlayerManager().getPlayerList());
        partners.removeIf(player -> player.getUuid().equals(dead.getUuid()) || !player.isAlive());
        for (ServerPlayerEntity partner : partners) {
            float loss = TrustNoOneMod.CONFIG.lives.partnerHealthLoss;
            if (loss > 0.0F) partner.damage((ServerWorld) partner.getEntityWorld(), partner.getDamageSources().magic(), loss);
            partner.sendMessage(Text.literal("Ты чувствуешь, как общая жизнь уходит вместе с напарником."), true);
            break;
        }

        if (roundState.getLives() <= 0) endRound(server);
    }

    private static void endRound(MinecraftServer server) {
        if (roundState == null || roundState.isGameOver()) return;
        roundState.setLives(0);
        roundState.setGameOver(true);
        broadcast(server, "TrustNoOne: доверие исчерпано. Раунд завершён.");
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            HOLD_TARGETS.remove(player.getUuid());
            REVIVE_PROGRESS.remove(player.getUuid());
            LAST_REVIVE_HINT.remove(player.getUuid());
            player.changeGameMode(GameMode.SPECTATOR);
        }
        // Deliberately do not delete world files: an accidental death must not destroy a save.
    }

    private static void processRevives(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Integer>> iterator = HOLD_TARGETS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            ServerPlayerEntity helper = server.getPlayerManager().getPlayer(entry.getKey());
            if (helper == null || !helper.isAlive() || helper.isSpectator()) {
                REVIVE_PROGRESS.remove(entry.getKey());
                LAST_REVIVE_HINT.remove(entry.getKey());
                iterator.remove();
                continue;
            }

            Entity target = ((ServerWorld) helper.getEntityWorld()).getEntityById(entry.getValue());
            if (!(target instanceof CorpseEntity corpse)) {
                REVIVE_PROGRESS.remove(helper.getUuid());
                sendReviveHint(helper, "Наведи прицел на тело и удерживай ПКМ.");
                continue;
            }

            UUID victimId = corpse.getVictimUuid();
            boolean soloTest = server.getPlayerManager().getCurrentPlayerCount() == 1
                    && TrustNoOneMod.CONFIG.lives.singleplayerTestingMode;
            if (victimId != null && victimId.equals(helper.getUuid()) && !soloTest) {
                REVIVE_PROGRESS.remove(helper.getUuid());
                sendReviveHint(helper, "Своё тело должен воскресить напарник.");
                continue;
            }

            if (!isValidReviveView(helper, corpse)) {
                REVIVE_PROGRESS.remove(helper.getUuid());
                sendReviveHint(helper, "Подойди ближе и смотри прямо на тело, не отпуская ПКМ.");
                continue;
            }

            int progress = REVIVE_PROGRESS.merge(helper.getUuid(), 1, Integer::sum);
            int needed = TrustNoOneMod.CONFIG.lives.reviveTicks;
            if (progress % 10 == 0 && progress < needed) {
                helper.sendMessage(Text.literal("Воскрешение… " + Math.min(99, progress * 100 / needed) + "%"), true);
            }
            if (progress >= needed) {
                revivePlayer(server, helper, corpse);
                REVIVE_PROGRESS.remove(helper.getUuid());
                LAST_REVIVE_HINT.remove(helper.getUuid());
                iterator.remove();
            }
        }
        REVIVE_PROGRESS.keySet().removeIf(uuid -> !HOLD_TARGETS.containsKey(uuid));
        LAST_REVIVE_HINT.keySet().removeIf(uuid -> !HOLD_TARGETS.containsKey(uuid));
    }

    /** Server-side validation is deliberately a little forgiving because the corpse is very low to the ground. */
    private static boolean isValidReviveView(ServerPlayerEntity player, CorpseEntity corpse) {
        if (!player.getEntityWorld().equals(corpse.getEntityWorld()) || player.squaredDistanceTo(corpse) > 16.0) {
            return false;
        }
        Vec3d targetPoint = corpse.getEntityPos().add(0.0, Math.max(0.18, corpse.getHeight() * 0.7), 0.0);
        Vec3d direction = targetPoint.subtract(player.getEyePos()).normalize();
        return player.getRotationVec(1.0F).dotProduct(direction) >= TrustNoOneMod.CONFIG.lives.reviveLookDot;
    }

    private static void sendReviveHint(ServerPlayerEntity player, String message) {
        long previous = LAST_REVIVE_HINT.getOrDefault(player.getUuid(), Long.MIN_VALUE / 2);
        if (serverTicks - previous >= 20L) {
            player.sendMessage(Text.literal("TrustNoOne: " + message), true);
            LAST_REVIVE_HINT.put(player.getUuid(), serverTicks);
        }
    }

    private static void revivePlayer(MinecraftServer server, ServerPlayerEntity helper, CorpseEntity corpse) {
        UUID victimId = corpse.getVictimUuid();
        if (victimId == null) {
            helper.sendMessage(Text.literal("Не удалось определить, кому принадлежит тело."), true);
            return;
        }
        boolean soloTest = server.getPlayerManager().getCurrentPlayerCount() == 1
                && TrustNoOneMod.CONFIG.lives.singleplayerTestingMode;
        if (helper.getUuid().equals(victimId) && !soloTest) {
            helper.sendMessage(Text.literal("Воскресить себя может только одиночный тестовый режим; в кооперативе нужен напарник."), true);
            return;
        }
        ServerPlayerEntity victim = server.getPlayerManager().getPlayer(victimId);
        if (victim == null) {
            helper.sendMessage(Text.literal("Напарник должен быть подключён, чтобы его воскресить."), true);
            return;
        }
        ServerPlayerEntity revived = victim;
        if (!victim.isAlive() || victim.getHealth() <= 0.0F) {
            revived = server.getPlayerManager().respawnPlayer(victim, false, Entity.RemovalReason.KILLED);
        }
        revived.refreshPositionAndAngles(corpse.getX(), corpse.getY(), corpse.getZ(), corpse.getYaw(), 0.0F);
        revived.setVelocity(Vec3d.ZERO);
        revived.setPose(EntityPose.STANDING);
        revived.setHealth(TrustNoOneMod.CONFIG.lives.reviveHealth);
        corpse.discard();
        broadcast(server, "TrustNoOne: напарник вернулся. Не теряйте друг друга.");
    }

    private static void onPossibleSin(Entity victim, DamageSource source) {
        if (!TrustNoOneMod.CONFIG.sins.enabled || roundState == null) return;
        Entity attacker = source.getAttacker();
        if (attacker instanceof ProjectileEntity projectile && projectile.getOwner() != null) {
            attacker = projectile.getOwner();
        }
        if (!(attacker instanceof PlayerEntity)) return;
        if (victim instanceof AnimalEntity || victim instanceof VillagerEntity || victim instanceof IronGolemEntity) {
            roundState.addSin();
            if (roundState.getSinLevel() == 1 || roundState.getSinLevel() % 5 == 0) {
                MinecraftServer server = attacker.getEntityWorld() instanceof ServerWorld serverWorld
                        ? serverWorld.getServer() : null;
                if (server != null) broadcast(server, "TrustNoOne: грех мира вырос до " + roundState.getSinLevel() + ".");
            }
        }
    }

    private static void applyDarkness(MinecraftServer server) {
        TrustNoOneConfig.Darkness cfg = TrustNoOneMod.CONFIG.darkness;
        if (!cfg.enabled) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            ServerWorld world = (ServerWorld) player.getEntityWorld();
            long dayTime = Math.floorMod(world.getTimeOfDay(), 24000L);
            boolean night = cfg.darkenNights && dayTime >= 13000L && dayTime < 23000L;
            boolean cave = cfg.darkenCaves && world.getLightLevel(player.getBlockPos()) <= cfg.lightThreshold;
            if (night || cave) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS,
                        45 + cfg.intensity * 35, 0, true, false, false));
            }
        }
    }

    private static void applySinConsequences(MinecraftServer server) {
        if (!TrustNoOneMod.CONFIG.sins.enabled || roundState == null || roundState.getSinLevel() <= 0) return;
        TrustNoOneConfig.Sins cfg = TrustNoOneMod.CONFIG.sins;
        int sins = roundState.getSinLevel();
        int buffLevel = sins / cfg.mobBuffThreshold;
        if (buffLevel > 0) {
            for (ServerWorld world : server.getWorlds()) {
                for (ServerPlayerEntity player : world.getPlayers()) {
                    for (HostileEntity hostile : world.getEntitiesByClass(HostileEntity.class,
                            player.getBoundingBox().expand(48.0), entity -> entity.isAlive())) {
                        hostile.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH,
                                140, Math.min(2, buffLevel - 1), true, false, false));
                        if (buffLevel >= 2) hostile.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,
                                140, Math.min(1, buffLevel - 2), true, false, false));
                    }
                }
            }
        }
        if (sins >= cfg.unluckThreshold) {
            int unluckLevel = Math.min(2, (sins - cfg.unluckThreshold) / Math.max(1, cfg.unluckThreshold));
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.isAlive()) player.addStatusEffect(new StatusEffectInstance(StatusEffects.UNLUCK,
                        140, unluckLevel, true, false, false));
            }
        }
        if (sins >= cfg.armorWearThreshold && serverTicks % cfg.armorWearIntervalTicks < 100L) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (!player.isAlive() || player.isCreative()) continue;
                damageArmorPiece(player, EquipmentSlot.HEAD);
                damageArmorPiece(player, EquipmentSlot.CHEST);
                damageArmorPiece(player, EquipmentSlot.LEGS);
                damageArmorPiece(player, EquipmentSlot.FEET);
            }
        }
    }

    private static void damageArmorPiece(ServerPlayerEntity player, EquipmentSlot slot) {
        ItemStack stack = player.getEquippedStack(slot);
        if (!stack.isEmpty() && stack.isDamageable()) stack.damage(1, player, slot);
    }

    private static void trySpawnImposter(MinecraftServer server) {
        TrustNoOneConfig.Imposter cfg = TrustNoOneMod.CONFIG.imposter;
        int playerCount = server.getPlayerManager().getCurrentPlayerCount();
        boolean soloTest = playerCount == 1 && TrustNoOneMod.CONFIG.lives.singleplayerTestingMode;
        if (!cfg.enabled || cfg.spawnChancePercent <= 0 || (playerCount < 2 && !soloTest)) return;
        int effectiveChance = soloTest ? Math.max(cfg.spawnChancePercent, 35) : cfg.spawnChancePercent;
        if (server.getOverworld().random.nextInt(100) >= effectiveChance) return;

        List<ServerPlayerEntity> candidates = new ArrayList<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isAlive() && !player.isSpectator()) candidates.add(player);
        }
        if (candidates.isEmpty()) return;
        ServerPlayerEntity source = candidates.get(server.getOverworld().random.nextInt(candidates.size()));
        ServerWorld world = (ServerWorld) source.getEntityWorld();
        if (!world.getEntitiesByClass(ImposterEntity.class, source.getBoundingBox().expand(64.0), e -> e.isAlive()).isEmpty()) return;

        int distance = cfg.minDistance + world.random.nextInt(Math.max(1, cfg.maxDistance - cfg.minDistance + 1));
        double angle = world.random.nextDouble() * Math.PI * 2.0;
        BlockPos horizontal = source.getBlockPos().add((int) Math.round(Math.cos(angle) * distance), 0,
                (int) Math.round(Math.sin(angle) * distance));
        if (!world.isChunkLoaded(horizontal)) return;
        BlockPos spawn = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, horizontal);
        if (!world.getBlockState(spawn).isAir()) spawn = spawn.up();
        if (!world.getBlockState(spawn).isAir() || !world.getBlockState(spawn.up()).isAir()) return;

        ImposterEntity imposter = ModEntities.IMPOSTER.create(world, SpawnReason.TRIGGERED);
        if (imposter == null) return;
        imposter.setCopiedPlayer(source.getUuid(), source.getName().getString());
        imposter.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                MathHelper.wrapDegrees(world.random.nextFloat() * 360.0F), 0.0F);
        world.spawnEntity(imposter);
    }

    private static void refreshNameHiding(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) applyNameHiding(server, player);
    }

    private static void applyNameHiding(MinecraftServer server, ServerPlayerEntity player) {
        Scoreboard scoreboard = server.getScoreboard();
        String teamName = "trustnoone_hidden";
        Team team = scoreboard.getTeam(teamName);
        if (TrustNoOneMod.CONFIG.names.hidePlayerNames) {
            if (team == null) team = scoreboard.addTeam(teamName);
            team.setNameTagVisibilityRule(AbstractTeam.VisibilityRule.NEVER);
            scoreboard.addScoreHolderToTeam(player.getName().getString(), team);
        } else if (team != null) {
            scoreboard.removeScoreHolderFromTeam(player.getName().getString(), team);
        }
    }

    private static void broadcast(MinecraftServer server, String message) {
        server.getPlayerManager().broadcast(Text.literal(message), false);
    }

    private HorrorEvents() { }
}
