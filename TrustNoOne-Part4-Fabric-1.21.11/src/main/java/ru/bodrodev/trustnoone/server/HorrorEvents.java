package ru.bodrodev.trustnoone.server;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.boss.WitherEntity;
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
import net.minecraft.util.Formatting;
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
    private static long nextGhostStoryCheckTick = 1200L;
    private static long nextGhostStoryLineTick;
    private static long nextStrangeMessageTick = 800L;
    private static long nextLightningCheckTick = 600L;
    private static int ghostStoryStage;
    private static String ghostStoryName = "Nobody";
    private static boolean applyingExactNightHunterDamage;
    private static final String NIGHT_HUNTER_TAG = "trustnoone_night_hunter";
    private static final String SIN_GOD_TAG = "trustnoone_sin_god";
    private static final Map<UUID, Long> LAST_ESCAPE_PUNISH_TICK = new HashMap<>();
    private static final Map<ServerWorld, WitherEntity> ACTIVE_SIN_GODS = new HashMap<>();

    private static final String[] GHOST_STORY_LINES = {
            "привет... тут кто-нибудь есть?",
            "я только что зашёл, но здесь уже кто-то стоит вдали.",
            "он выглядит точно как я и повторяет мои движения.",
            "я закрыл дверь... но шаги всё ещё идут за мной.",
            "почему в списке игроков написано, что я один?",
            "ОН УЖЕ РЯДОМ. НЕ ОБОРАЧИВАЙТЕСЬ."
    };

    private static final String[] STRANGE_MESSAGES = {
            "⟊⟁⟟ ᚾᛟᚦᛁᚾᚷ ⟟⟁⟊",
            "НЕ_СМОТРИ_НА_НЕГО // 000000",
            "⧫⧫⧫ ᛁ ᛋᛖᛖ ᚤᛟᚢ ⧫⧫⧫",
            "[NULL] ⟊⟒⟟⟒ ⟊⟒⟟⟒ [NULL]",
            "ТВОЁ_ИМЯ_УЖЕ_ЗДЕСЬ",
            "ᚦᛖ ᛞᛟᛟᚱ ᛁᛋ ᛟᛈᛖᚾ",
            "⟁⟟⧫⟟⟁⧫⟟⟁⟟⧫"
    };

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            PersistentStateManager manager = server.getOverworld().getPersistentStateManager();
            roundState = manager.getOrCreate(TrustNoOneWorldState.TYPE);
            roundState.initializeIfNeeded(TrustNoOneMod.CONFIG.lives.startingLives);
            HOLD_TARGETS.clear();
            REVIVE_PROGRESS.clear();
            LAST_REVIVE_HINT.clear();
            serverTicks = 0L;
            nextGhostStoryCheckTick = TrustNoOneMod.CONFIG.ghostPlayer.firstDelayTicks;
            nextGhostStoryLineTick = 0L;
            nextStrangeMessageTick = 800L;
            nextLightningCheckTick = 600L;
            ghostStoryStage = 0;
            ghostStoryName = "Nobody";
            applyingExactNightHunterDamage = false;
            LAST_ESCAPE_PUNISH_TICK.clear();
            ACTIVE_SIN_GODS.clear();
            refreshNameHiding(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            HOLD_TARGETS.clear();
            REVIVE_PROGRESS.clear();
            LAST_REVIVE_HINT.clear();
            ghostStoryStage = 0;
            LAST_ESCAPE_PUNISH_TICK.clear();
            ACTIVE_SIN_GODS.clear();
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
        // Night hunters deal exactly 2 raw damage (one heart) per successful hit.
        // The guard lets the replacement damage pass through this callback only once.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (applyingExactNightHunterDamage) return true;
            if (!(entity instanceof ServerPlayerEntity player)) return true;
            if (!(source.getAttacker() instanceof ZombieEntity hunter)
                    || !hunter.getCommandTags().contains(NIGHT_HUNTER_TAG)) return true;
            if (!(player.getEntityWorld() instanceof ServerWorld world)) return true;
            applyingExactNightHunterDamage = true;
            try {
                player.damage(world, player.getDamageSources().mobAttack(hunter), 2.0F);
            } finally {
                applyingExactNightHunterDamage = false;
            }
            return false;
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
            if (serverTicks % 100L == 0L) applySinConsequences(server);
            int spawnInterval = TrustNoOneMod.CONFIG.imposter.spawnIntervalTicks;
            if (server.getPlayerManager().getCurrentPlayerCount() == 1
                    && TrustNoOneMod.CONFIG.lives.singleplayerTestingMode) {
                spawnInterval = Math.min(spawnInterval, 400);
            }
            if (serverTicks % Math.max(100, spawnInterval) == 0L) trySpawnImposter(server);
            if (serverTicks % Math.max(100, TrustNoOneMod.CONFIG.nightHunters.spawnIntervalTicks) == 0L) {
                trySpawnNightHunter(server);
            }
            handleGhostPlayerStory(server);
            handleStrangeChat(server);
            handleRandomLightning(server);
            if (serverTicks % 20L == 0L) enforceSinGodProximity(server);
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
            MinecraftServer server = attacker.getEntityWorld() instanceof ServerWorld serverWorld
                    ? serverWorld.getServer() : null;
            if (roundState.getSinLevel() == 1 || roundState.getSinLevel() % 5 == 0) {
                if (server != null) broadcast(server, "TrustNoOne: грех мира вырос до " + roundState.getSinLevel() + ".");
            }
            if (server != null && attacker instanceof ServerPlayerEntity sinner) {
                summonOrStrengthenSinGod(server, sinner, roundState.getSinLevel());
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

    private static void summonOrStrengthenSinGod(MinecraftServer server, ServerPlayerEntity sinner, int sins) {
        TrustNoOneConfig.SinGod cfg = TrustNoOneMod.CONFIG.sinGod;
        if (!cfg.enabled || sins < cfg.sinThreshold || sinner.isSpectator()) return;
        ServerWorld world = (ServerWorld) sinner.getEntityWorld();
        WitherEntity god = ACTIVE_SIN_GODS.get(world);
        if (god != null && !god.isAlive()) {
            ACTIVE_SIN_GODS.remove(world);
            god = null;
        }

        if (god == null) {
            god = EntityType.WITHER.create(world, SpawnReason.TRIGGERED);
            if (god == null) return;
            god.getCommandTags().add(SIN_GOD_TAG);
            god.setCustomName(Text.literal("БОГ ГРЕХОВ").formatted(Formatting.DARK_RED, Formatting.BOLD));
            god.setCustomNameVisible(false);
            var maxHealth = god.getAttributeInstance(EntityAttributes.MAX_HEALTH);
            if (maxHealth != null) maxHealth.setBaseValue(Math.min(cfg.maxHealth,
                    cfg.baseHealth + Math.max(0, sins - cfg.sinThreshold) * cfg.healthPerSin));
            god.setHealth((float) Math.min(cfg.maxHealth,
                    cfg.baseHealth + Math.max(0, sins - cfg.sinThreshold) * cfg.healthPerSin));
            // Materialize above the sinner, as if the boss has descended from the storm.
            god.refreshPositionAndAngles(sinner.getX() + 2.0, sinner.getY() + 14.0,
                    sinner.getZ() + 2.0, sinner.getYaw(), 0.0F);
            spawnGodLightning(world, sinner.getBlockPos().offset(sinner.getHorizontalFacing(), 3));
            if (!world.spawnEntity(god)) return;
            ACTIVE_SIN_GODS.put(world, god);
            broadcast(server, "ПЯТЬ ГРЕХОВ... БОГ ГРЕХОВ ПРИШЁЛ ЗА ВАМИ.");
        } else {
            var maxHealth = god.getAttributeInstance(EntityAttributes.MAX_HEALTH);
            if (maxHealth != null) {
                double increased = Math.min(cfg.maxHealth, maxHealth.getBaseValue() + cfg.healthPerSin);
                maxHealth.setBaseValue(increased);
                god.setHealth((float) Math.min(increased, god.getHealth() + cfg.healthPerSin));
            }
            spawnGodLightning(world, sinner.getBlockPos().offset(sinner.getHorizontalFacing(), 3));
            sinner.sendMessage(Text.literal("БОГ ГРЕХОВ СТАЛ СИЛЬНЕЕ. ОН ПОМНИТ ТВОИ ПОСТУПКИ.")
                    .formatted(Formatting.DARK_RED, Formatting.BOLD), false);
            if (god.squaredDistanceTo(sinner) > 24.0 * 24.0) {
                god.refreshPositionAndAngles(sinner.getX() + 2.0, sinner.getY() + 10.0,
                        sinner.getZ() + 2.0, sinner.getYaw(), 0.0F);
            }
        }
        god.setTarget(sinner);
    }

    private static void spawnGodLightning(ServerWorld world, BlockPos position) {
        if (!world.isChunkLoaded(position)) return;
        BlockPos strike = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, position);
        LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
        if (lightning == null) return;
        lightning.setCosmetic(true);
        lightning.refreshPositionAndAngles(strike.getX() + 0.5, strike.getY(), strike.getZ() + 0.5, 0.0F, 0.0F);
        world.spawnEntity(lightning);
    }

    private static void enforceSinGodProximity(MinecraftServer server) {
        TrustNoOneConfig.SinGod cfg = TrustNoOneMod.CONFIG.sinGod;
        if (!cfg.enabled) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) continue;
            if (!(player.getEntityWorld() instanceof ServerWorld world)) continue;
            WitherEntity god = ACTIVE_SIN_GODS.get(world);
            if (god == null || !god.isAlive()) {
                if (god != null) ACTIVE_SIN_GODS.remove(world);
                continue;
            }
            if (god.squaredDistanceTo(player) <= cfg.escapeDistance * cfg.escapeDistance) continue;
            long last = LAST_ESCAPE_PUNISH_TICK.getOrDefault(player.getUuid(), Long.MIN_VALUE / 2);
            if (serverTicks - last < cfg.escapeCooldownTicks) continue;
            LAST_ESCAPE_PUNISH_TICK.put(player.getUuid(), serverTicks);
            Vec3d look = player.getRotationVec(1.0F);
            double x = player.getX() + look.x * 7.0;
            double y = player.getY() + 2.0;
            double z = player.getZ() + look.z * 7.0;
            BlockPos front = BlockPos.ofFloored(x, y, z);
            spawnGodLightning(world, front);
            god.refreshPositionAndAngles(x, y, z, player.getYaw(), 0.0F);
            god.setTarget(player);
            player.sendMessage(Text.literal("ХОТЕЛИ УБЕЖАТЬ ОТ ГРЕХОВ?!")
                    .formatted(Formatting.DARK_RED, Formatting.BOLD), false);
            player.damage(world, player.getDamageSources().mobAttack(god), (float) cfg.escapeDamage);
        }
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

    private static void trySpawnNightHunter(MinecraftServer server) {
        TrustNoOneConfig.NightHunters cfg = TrustNoOneMod.CONFIG.nightHunters;
        if (!cfg.enabled || cfg.spawnChancePercent <= 0) return;

        ServerWorld world = server.getOverworld();
        List<ServerPlayerEntity> candidates = new ArrayList<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isAlive() && !player.isSpectator() && player.getEntityWorld() == world) {
                long time = Math.floorMod(world.getTimeOfDay(), 24000L);
                if (time >= 13000L && time < 23000L) candidates.add(player);
            }
        }
        if (candidates.isEmpty() || world.random.nextInt(100) >= cfg.spawnChancePercent) return;

        ServerPlayerEntity target = candidates.get(world.random.nextInt(candidates.size()));
        int nearbyCount = world.getEntitiesByClass(ZombieEntity.class,
                target.getBoundingBox().expand(64.0), entity -> entity.isAlive()
                        && entity.getCommandTags().contains(NIGHT_HUNTER_TAG)).size();
        if (nearbyCount >= cfg.maxNearby) return;

        int distance = cfg.minDistance + world.random.nextInt(Math.max(1, cfg.maxDistance - cfg.minDistance + 1));
        double angle = world.random.nextDouble() * Math.PI * 2.0;
        BlockPos horizontal = target.getBlockPos().add(
                (int) Math.round(Math.cos(angle) * distance), 0,
                (int) Math.round(Math.sin(angle) * distance));
        if (!world.isChunkLoaded(horizontal)) return;
        BlockPos spawn = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, horizontal);
        if (!world.getBlockState(spawn).isAir()) spawn = spawn.up();
        if (!world.getBlockState(spawn).isAir() || !world.getBlockState(spawn.up()).isAir()) return;

        // A husk and a normal zombie use vanilla pathfinding and the vanilla mob model.
        ZombieEntity hunter = world.random.nextBoolean()
                ? EntityType.HUSK.create(world, SpawnReason.TRIGGERED)
                : EntityType.ZOMBIE.create(world, SpawnReason.TRIGGERED);
        if (hunter == null) return;
        hunter.getCommandTags().add(NIGHT_HUNTER_TAG);
        hunter.setCustomName(Text.literal("Ночной охотник").formatted(Formatting.DARK_RED));
        hunter.setCustomNameVisible(false);
        var maxHealth = hunter.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(cfg.health);
        var speed = hunter.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(cfg.movementSpeed);
        var attack = hunter.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(2.0);
        var followRange = hunter.getAttributeInstance(EntityAttributes.FOLLOW_RANGE);
        if (followRange != null) followRange.setBaseValue(48.0);
        hunter.setHealth((float) cfg.health);
        hunter.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                MathHelper.wrapDegrees(world.random.nextFloat() * 360.0F), 0.0F);
        if (world.spawnEntity(hunter)) {
            hunter.setTarget(target);
            target.sendMessage(Text.literal("Ты слышишь быстрые шаги где-то в темноте...")
                    .formatted(Formatting.DARK_RED), true);
        }
    }

    private static void handleGhostPlayerStory(MinecraftServer server) {
        TrustNoOneConfig.GhostPlayer cfg = TrustNoOneMod.CONFIG.ghostPlayer;
        if (!cfg.enabled) return;
        var random = server.getOverworld().random;

        if (ghostStoryStage > 0) {
            if (serverTicks < nextGhostStoryLineTick) return;
            if (ghostStoryStage <= GHOST_STORY_LINES.length) {
                String line = GHOST_STORY_LINES[ghostStoryStage - 1];
                Formatting color = ghostStoryStage >= 5 ? Formatting.DARK_RED : Formatting.GRAY;
                server.getPlayerManager().broadcast(Text.literal("<" + ghostStoryName + "> " + line).formatted(color), false);
                ghostStoryStage++;
                nextGhostStoryLineTick = serverTicks + 80L + random.nextInt(41);
            } else {
                server.getPlayerManager().broadcast(Text.translatable("multiplayer.player.left", Text.literal(ghostStoryName))
                        .formatted(Formatting.YELLOW), false);
                ghostStoryStage = 0;
                nextGhostStoryCheckTick = serverTicks + randomInterval(random,
                        cfg.minIntervalTicks, cfg.maxIntervalTicks);
            }
            return;
        }

        if (serverTicks < nextGhostStoryCheckTick) return;
        nextGhostStoryCheckTick = serverTicks + randomInterval(random, cfg.minIntervalTicks, cfg.maxIntervalTicks);
        if (random.nextInt(100) >= cfg.chancePercent || server.getPlayerManager().getCurrentPlayerCount() == 0) return;

        String[] names = {"M0ON_404", "Alex_0", "Nobody", "zxc_ghost", "Steve_??", "player_000"};
        ghostStoryName = names[random.nextInt(names.length)];
        server.getPlayerManager().broadcast(Text.translatable("multiplayer.player.joined", Text.literal(ghostStoryName))
                .formatted(Formatting.YELLOW), false);
        ghostStoryStage = 1;
        nextGhostStoryLineTick = serverTicks + 80L;
    }

    private static void handleStrangeChat(MinecraftServer server) {
        TrustNoOneConfig.StrangeChat cfg = TrustNoOneMod.CONFIG.strangeChat;
        if (!cfg.enabled || server.getPlayerManager().getCurrentPlayerCount() == 0
                || serverTicks < nextStrangeMessageTick) return;
        var random = server.getOverworld().random;
        nextStrangeMessageTick = serverTicks + randomInterval(random, cfg.minIntervalTicks, cfg.maxIntervalTicks);
        if (random.nextInt(100) >= cfg.chancePercent) return;
        String message = STRANGE_MESSAGES[random.nextInt(STRANGE_MESSAGES.length)];
        server.getPlayerManager().broadcast(Text.literal(message)
                .formatted(Formatting.DARK_RED, Formatting.OBFUSCATED), false);
    }

    private static void handleRandomLightning(MinecraftServer server) {
        TrustNoOneConfig.RandomLightning cfg = TrustNoOneMod.CONFIG.randomLightning;
        if (!cfg.enabled || serverTicks < nextLightningCheckTick) return;
        var random = server.getOverworld().random;
        nextLightningCheckTick = serverTicks + cfg.intervalTicks + random.nextInt(Math.max(1, cfg.intervalTicks / 2));
        if (random.nextInt(100) >= cfg.chancePercent) return;

        ServerWorld world = server.getOverworld();
        List<ServerPlayerEntity> candidates = new ArrayList<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isAlive() || player.isSpectator() || player.getEntityWorld() != world) continue;
            long time = Math.floorMod(world.getTimeOfDay(), 24000L);
            if (time >= 13000L && time < 23000L) candidates.add(player);
        }
        if (candidates.isEmpty()) return;
        ServerPlayerEntity target = candidates.get(random.nextInt(candidates.size()));
        int distance = 8 + random.nextInt(8);
        double angle = random.nextDouble() * Math.PI * 2.0;
        BlockPos horizontal = target.getBlockPos().add(
                (int) Math.round(Math.cos(angle) * distance), 0,
                (int) Math.round(Math.sin(angle) * distance));
        if (!world.isChunkLoaded(horizontal)) return;
        BlockPos strike = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, horizontal);
        LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
        if (lightning == null) return;
        lightning.setCosmetic(true); // visual flash and sound, no fire, damage or mob ignition
        lightning.refreshPositionAndAngles(strike.getX() + 0.5, strike.getY(), strike.getZ() + 0.5, 0.0F, 0.0F);
        world.spawnEntity(lightning);
    }

    private static int randomInterval(net.minecraft.util.math.random.Random random, int min, int max) {
        return min + random.nextInt(Math.max(1, max - min + 1));
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
