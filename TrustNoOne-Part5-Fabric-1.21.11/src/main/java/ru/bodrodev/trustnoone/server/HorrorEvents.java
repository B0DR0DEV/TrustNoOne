package ru.bodrodev.trustnoone.server;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
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
    private static long nextObserverCheckTick = 1800L;
    private static long nextFootstepsCheckTick = 1200L;
    private static long nextHomeHauntingCheckTick = 4200L;
    private static long nextFakeFriendCheckTick = 2400L;
    private static long nextHomeStoryLineTick;
    private static long nextFakeFriendLineTick;
    private static int homeStoryStage;
    private static int fakeFriendStage;
    private static String fakeFriendName = "Steve";
    private static UUID footstepsTarget;
    private static int footstepsRemaining;
    private static long nextFootstepTick;
    private static int ghostStoryStage;
    private static String ghostStoryName = "Nobody";
    private static boolean applyingExactNightHunterDamage;
    private static final String NIGHT_HUNTER_TAG = "trustnoone_night_hunter";
    private static final String SIN_GOD_TAG = "trustnoone_sin_god";
    private static final Map<UUID, Long> LAST_ESCAPE_PUNISH_TICK = new HashMap<>();
    private static final Map<ServerWorld, WitherEntity> ACTIVE_SIN_GODS = new HashMap<>();
    private static final Map<WitherEntity, Long> PRE_OMEN_EXPIRY = new HashMap<>();
    private static final String OBSERVER_TAG = "trustnoone_observer";

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
            nextObserverCheckTick = 1800L;
            nextFootstepsCheckTick = 1200L;
            nextHomeHauntingCheckTick = 4200L;
            nextFakeFriendCheckTick = 2400L;
            nextHomeStoryLineTick = 0L;
            nextFakeFriendLineTick = 0L;
            homeStoryStage = 0;
            fakeFriendStage = 0;
            fakeFriendName = "Steve";
            footstepsTarget = null;
            footstepsRemaining = 0;
            nextFootstepTick = 0L;
            ghostStoryStage = 0;
            ghostStoryName = "Nobody";
            applyingExactNightHunterDamage = false;
            LAST_ESCAPE_PUNISH_TICK.clear();
            ACTIVE_SIN_GODS.clear();
            PRE_OMEN_EXPIRY.clear();
            refreshNameHiding(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            HOLD_TARGETS.clear();
            REVIVE_PROGRESS.clear();
            LAST_REVIVE_HINT.clear();
            ghostStoryStage = 0;
            LAST_ESCAPE_PUNISH_TICK.clear();
            ACTIVE_SIN_GODS.clear();
            PRE_OMEN_EXPIRY.clear();
            footstepsTarget = null;
            footstepsRemaining = 0;
            homeStoryStage = 0;
            fakeFriendStage = 0;
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
            handleObserver(server);
            handleMimicFootsteps(server);
            handleHomeHaunting(server);
            handleFakeFriendMessages(server);
            if (serverTicks % 20L == 0L) enforceSinGodProximity(server);
        }
        // Temporary pre-boss silhouettes must still despawn even if the round ends.
        tickWitherOmen(server);
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
            int sins = roundState.getSinLevel();
            if (sins == 1 || sins % 5 == 0) {
                if (server != null) broadcast(server, "TrustNoOne: грех мира вырос до " + sins + ".");
            }
            if (server != null && attacker instanceof ServerPlayerEntity sinner) {
                triggerGodPremonition(server, sinner, sins);
                summonOrStrengthenSinGod(server, sinner, sins);
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
        clearWitherOmen(world);
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

    /** Rare player-shaped observer. It never attacks and vanishes after being watched briefly. */
    private static void handleObserver(MinecraftServer server) {
        TrustNoOneConfig.Observer cfg = TrustNoOneMod.CONFIG.observer;
        if (!cfg.enabled || server.getPlayerManager().getCurrentPlayerCount() == 0
                || serverTicks < nextObserverCheckTick) return;
        var random = server.getOverworld().random;
        nextObserverCheckTick = serverTicks + cfg.intervalTicks + random.nextInt(Math.max(1, cfg.intervalTicks / 2));
        if (random.nextInt(100) >= cfg.chancePercent) return;

        List<ServerPlayerEntity> candidates = new ArrayList<>();
        ServerWorld world = server.getOverworld();
        long time = Math.floorMod(world.getTimeOfDay(), 24000L);
        boolean night = time >= 13000L && time < 23000L;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getEntityWorld() != world || !player.isAlive() || player.isSpectator()) continue;
            if (night || world.getLightLevel(player.getBlockPos()) <= 8) candidates.add(player);
        }
        if (candidates.isEmpty()) return;
        ServerPlayerEntity target = candidates.get(random.nextInt(candidates.size()));
        if (!world.getEntitiesByClass(ImposterEntity.class, target.getBoundingBox().expand(48.0), Entity::isAlive).isEmpty()) return;
        List<ServerPlayerEntity> skinCandidates = new ArrayList<>();
        for (ServerPlayerEntity player : candidates) {
            if (!player.getUuid().equals(target.getUuid())) skinCandidates.add(player);
        }
        ServerPlayerEntity skinSource = skinCandidates.isEmpty()
                ? target : skinCandidates.get(random.nextInt(skinCandidates.size()));

        int distance = cfg.minDistance + random.nextInt(Math.max(1, cfg.maxDistance - cfg.minDistance + 1));
        double yaw = Math.toRadians(target.getYaw());
        // Spawn behind the player, so turning around is what reveals the silhouette.
        BlockPos horizontal = target.getBlockPos().add(
                (int) Math.round(Math.sin(yaw) * distance), 0,
                (int) Math.round(-Math.cos(yaw) * distance));
        if (!world.isChunkLoaded(horizontal)) return;
        BlockPos spawn = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, horizontal);
        if (!world.getBlockState(spawn).isAir()) spawn = spawn.up();
        if (!world.getBlockState(spawn).isAir() || !world.getBlockState(spawn.up()).isAir()) return;

        ImposterEntity observer = ModEntities.IMPOSTER.create(world, SpawnReason.TRIGGERED);
        if (observer == null) return;
        observer.setCopiedPlayer(skinSource.getUuid(), skinSource.getName().getString());
        observer.getCommandTags().add(OBSERVER_TAG);
        observer.setAiDisabled(true);
        observer.setInvulnerable(true);
        observer.setCustomName(null);
        observer.setCustomNameVisible(false);
        observer.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                target.getYaw() + 180.0F, 0.0F);
        world.spawnEntity(observer);
    }

    /** Footsteps echo several blocks behind a moving player and immediately stop with their movement. */
    private static void handleMimicFootsteps(MinecraftServer server) {
        TrustNoOneConfig.MimicFootsteps cfg = TrustNoOneMod.CONFIG.mimicFootsteps;
        if (!cfg.enabled) {
            footstepsTarget = null;
            footstepsRemaining = 0;
            return;
        }
        if (footstepsTarget != null && serverTicks >= nextFootstepTick && footstepsRemaining > 0) {
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(footstepsTarget);
            if (target == null || !target.isAlive() || target.isSpectator()
                    || target.getEntityWorld() != server.getOverworld()
                    || target.getVelocity().horizontalLengthSquared() < 0.003) {
                footstepsTarget = null;
                footstepsRemaining = 0;
            } else {
                ServerWorld world = server.getOverworld();
                double yaw = Math.toRadians(target.getYaw());
                double side = world.random.nextBoolean() ? 1.1 : -1.1;
                double x = target.getX() + Math.sin(yaw) * 4.0 - Math.cos(yaw) * side;
                double z = target.getZ() - Math.cos(yaw) * 4.0 - Math.sin(yaw) * side;
                double y = target.getY();
                if (world.isChunkLoaded(BlockPos.ofFloored(x, y, z))) {
                    Vec3d soundPos = new Vec3d(x, y + 0.5, z);
                    var hit = world.raycast(new RaycastContext(target.getEyePos(), soundPos,
                            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, target));
                    float volume = hit.getType() == HitResult.Type.BLOCK ? 0.72F : 0.38F;
                    world.playSound(null, x, y, z, SoundEvents.BLOCK_STONE_STEP, SoundCategory.HOSTILE,
                            volume, 0.62F + world.random.nextFloat() * 0.28F);
                }
                footstepsRemaining--;
                nextFootstepTick = serverTicks + 6L + world.random.nextInt(6);
                if (footstepsRemaining <= 0) footstepsTarget = null;
            }
        }

        if (serverTicks < nextFootstepsCheckTick) return;
        var random = server.getOverworld().random;
        nextFootstepsCheckTick = serverTicks + cfg.checkIntervalTicks
                + random.nextInt(Math.max(1, cfg.checkIntervalTicks / 2));
        if (footstepsTarget != null || random.nextInt(100) >= cfg.chancePercent) return;
        List<ServerPlayerEntity> candidates = new ArrayList<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getEntityWorld() != server.getOverworld() || !player.isAlive() || player.isSpectator()) continue;
            long time = Math.floorMod(server.getOverworld().getTimeOfDay(), 24000L);
            boolean night = time >= 13000L && time < 23000L;
            boolean cave = player.getY() < server.getOverworld().getSeaLevel() - 5
                    && server.getOverworld().getLightLevel(player.getBlockPos()) <= 8;
            if ((night || cave) && player.getVelocity().horizontalLengthSquared() >= 0.003) candidates.add(player);
        }
        if (candidates.isEmpty()) return;
        ServerPlayerEntity chosen = candidates.get(random.nextInt(candidates.size()));
        footstepsTarget = chosen.getUuid();
        footstepsRemaining = cfg.minSteps + random.nextInt(Math.max(1, cfg.maxSteps - cfg.minSteps + 1));
        nextFootstepTick = serverTicks + 4L;
    }

    /** Home events require a bed nearby, so random messages do not fire in the middle of nowhere. */
    private static void handleHomeHaunting(MinecraftServer server) {
        TrustNoOneConfig.HomeHaunting cfg = TrustNoOneMod.CONFIG.homeHaunting;
        if (!cfg.enabled || server.getPlayerManager().getCurrentPlayerCount() == 0) return;
        var random = server.getOverworld().random;
        if (homeStoryStage > 0) {
            if (serverTicks < nextHomeStoryLineTick) return;
            if (homeStoryStage == 1) {
                broadcast(server, "[UNKNOWN] Почему вы оставили дверь открытой.");
                homeStoryStage = 2;
                nextHomeStoryLineTick = serverTicks + 60L;
            } else if (homeStoryStage == 2) {
                broadcast(server, "[UNKNOWN] Я всё ещё внутри.");
                homeStoryStage = 0;
                nextHomeStoryLineTick = 0L;
            }
            return;
        }
        if (serverTicks < nextHomeHauntingCheckTick) return;
        long time = Math.floorMod(server.getOverworld().getTimeOfDay(), 24000L);
        if (time < 13000L || time >= 23000L) {
            nextHomeHauntingCheckTick = serverTicks + 400L;
            return;
        }
        nextHomeHauntingCheckTick = serverTicks + cfg.intervalTicks
                + random.nextInt(Math.max(1, cfg.intervalTicks / 2));
        if (random.nextInt(100) >= cfg.chancePercent) return;
        List<ServerPlayerEntity> candidates = new ArrayList<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getEntityWorld() == server.getOverworld() && player.isAlive() && !player.isSpectator()
                    && findNearbyBlock(server.getOverworld(), player.getBlockPos(), cfg.radius, BedBlock.class) != null) {
                candidates.add(player);
            }
        }
        if (candidates.isEmpty()) return;
        ServerPlayerEntity target = candidates.get(random.nextInt(candidates.size()));
        ServerWorld world = server.getOverworld();
        BlockPos door = findNearbyClosedDoor(world, target.getBlockPos(), cfg.radius);
        if (door != null) {
            BlockState state = world.getBlockState(door);
            if (cfg.openNearbyDoor && state.contains(Properties.OPEN) && !state.get(Properties.OPEN)) {
                world.setBlockState(door, state.with(Properties.OPEN, true), Block.NOTIFY_ALL);
                world.playSound(null, door, SoundEvents.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, 0.8F, 0.72F);
            } else {
                world.playSound(null, door, SoundEvents.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, 0.55F, 0.68F);
            }
        } else {
            world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_WOODEN_DOOR_OPEN,
                    SoundCategory.BLOCKS, 0.35F, 0.65F);
        }
        broadcast(server, "[UNKNOWN] Я заходил к вам домой.");
        homeStoryStage = 1;
        nextHomeStoryLineTick = serverTicks + 65L;
    }

    private static BlockPos findNearbyBlock(ServerWorld world, BlockPos center, int radius,
                                             Class<? extends Block> blockType) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (!world.isChunkLoaded(pos)) continue;
                    if (blockType.isInstance(world.getBlockState(pos).getBlock())) return pos.toImmutable();
                }
            }
        }
        return null;
    }

    private static BlockPos findNearbyClosedDoor(ServerWorld world, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (!world.isChunkLoaded(pos)) continue;
                    BlockState state = world.getBlockState(pos);
                    if (state.getBlock() instanceof DoorBlock && state.contains(Properties.OPEN)
                            && !state.get(Properties.OPEN)) return pos.toImmutable();
                }
            }
        }
        return null;
    }

    /** Fake role-play chat that uses an online friend's visible name, then contradicts itself. */
    private static void handleFakeFriendMessages(MinecraftServer server) {
        TrustNoOneConfig.FakeFriendMessages cfg = TrustNoOneMod.CONFIG.fakeFriendMessages;
        if (!cfg.enabled || server.getPlayerManager().getCurrentPlayerCount() < 2) return;
        var random = server.getOverworld().random;
        if (fakeFriendStage > 0) {
            if (serverTicks < nextFakeFriendLineTick) return;
            if (fakeFriendStage == 1) {
                server.getPlayerManager().broadcast(Text.literal("[UNKNOWN] " + fakeFriendName
                        + " не отправлял это сообщение.").formatted(Formatting.DARK_RED, Formatting.ITALIC), false);
                fakeFriendStage = 2;
                nextFakeFriendLineTick = serverTicks + 55L;
            } else {
                server.getPlayerManager().broadcast(Text.literal("<" + fakeFriendName + "> Я НИЧЕГО НЕ ПИСАЛ.")
                        .formatted(Formatting.GRAY, Formatting.ITALIC), false);
                fakeFriendStage = 0;
                nextFakeFriendLineTick = 0L;
            }
            return;
        }
        if (serverTicks < nextFakeFriendCheckTick) return;
        nextFakeFriendCheckTick = serverTicks + cfg.intervalTicks
                + random.nextInt(Math.max(1, cfg.intervalTicks / 2));
        if (random.nextInt(100) >= cfg.chancePercent) return;
        List<ServerPlayerEntity> candidates = new ArrayList<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isAlive() && !player.isSpectator()) candidates.add(player);
        }
        if (candidates.size() < 2) return;
        fakeFriendName = candidates.get(random.nextInt(candidates.size())).getName().getString();
        server.getPlayerManager().broadcast(Text.literal("<" + fakeFriendName
                + "> ребят, кто сейчас стоит у меня за спиной?").formatted(Formatting.GRAY, Formatting.ITALIC), false);
        fakeFriendStage = 1;
        nextFakeFriendLineTick = serverTicks + 55L;
    }

    /** Each sin before the fifth one escalates the foreshadowing of the Sin God. */
    private static void triggerGodPremonition(MinecraftServer server, ServerPlayerEntity sinner, int sins) {
        TrustNoOneConfig.GodPremonitions cfg = TrustNoOneMod.CONFIG.godPremonitions;
        int threshold = TrustNoOneMod.CONFIG.sinGod.sinThreshold;
        if (!cfg.enabled || sins >= threshold || sinner.isSpectator()) return;
        ServerWorld world = (ServerWorld) sinner.getEntityWorld();
        if (sins == 1) {
            sinner.sendMessage(Text.literal("В темноте что-то услышало тебя.")
                    .formatted(Formatting.DARK_RED, Formatting.ITALIC), false);
        } else if (sins == 2) {
            sinner.sendMessage(Text.literal("Гром прокатился там, где нет облаков...")
                    .formatted(Formatting.DARK_RED, Formatting.ITALIC), false);
            spawnDistantPremonitionLightning(world, sinner);
        } else if (sins == 3) {
            broadcast(server, "[UNKNOWN] ГРЕХИ НЕ ОСТАЮТСЯ БЕЗ СВИДЕТЕЛЯ.");
            spawnDistantPremonitionLightning(world, sinner);
        } else if (sins == 4) {
            broadcast(server, "Облака складываются в знакомую форму...");
            spawnDistantPremonitionLightning(world, sinner);
            spawnWitherOmen(world, sinner, cfg.omenDurationTicks);
        }
    }

    private static void spawnDistantPremonitionLightning(ServerWorld world, ServerPlayerEntity sinner) {
        int distance = 28 + world.random.nextInt(12);
        double angle = world.random.nextDouble() * Math.PI * 2.0;
        BlockPos horizontal = sinner.getBlockPos().add(
                (int) Math.round(Math.cos(angle) * distance), 0,
                (int) Math.round(Math.sin(angle) * distance));
        if (world.isChunkLoaded(horizontal)) spawnGodLightning(world, horizontal);
    }

    private static void spawnWitherOmen(ServerWorld world, ServerPlayerEntity sinner, int durationTicks) {
        double angle = world.random.nextDouble() * Math.PI * 2.0;
        int distance = 30 + world.random.nextInt(12);
        BlockPos horizontal = sinner.getBlockPos().add(
                (int) Math.round(Math.cos(angle) * distance), 0,
                (int) Math.round(Math.sin(angle) * distance));
        if (!world.isChunkLoaded(horizontal)) return;
        BlockPos ground = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, horizontal);
        WitherEntity omen = EntityType.WITHER.create(world, SpawnReason.TRIGGERED);
        if (omen == null) return;
        omen.getCommandTags().add("trustnoone_wither_omen");
        omen.setAiDisabled(true);
        omen.setInvulnerable(true);
        omen.setNoGravity(true);
        omen.setCustomName(null);
        omen.setCustomNameVisible(false);
        omen.setVelocity(Vec3d.ZERO);
        omen.refreshPositionAndAngles(ground.getX() + 0.5, ground.getY() + 10.0,
                ground.getZ() + 0.5, sinner.getYaw(), 0.0F);
        if (world.spawnEntity(omen)) PRE_OMEN_EXPIRY.put(omen, serverTicks + durationTicks);
    }

    private static void clearWitherOmen(ServerWorld world) {
        Iterator<Map.Entry<WitherEntity, Long>> iterator = PRE_OMEN_EXPIRY.entrySet().iterator();
        while (iterator.hasNext()) {
            WitherEntity omen = iterator.next().getKey();
            if (omen.getEntityWorld() == world) {
                omen.discard();
                iterator.remove();
            }
        }
    }

    private static void tickWitherOmen(MinecraftServer server) {
        Iterator<Map.Entry<WitherEntity, Long>> iterator = PRE_OMEN_EXPIRY.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<WitherEntity, Long> entry = iterator.next();
            WitherEntity omen = entry.getKey();
            if (omen.getEntityWorld() instanceof ServerWorld omenWorld && omen.isAlive()) {
                // WitherEntity has special attack logic outside its normal AI goals. Remove every
                // skull owned by this harmless omen before it can reach a player or a build.
                for (WitherSkullEntity skull : omenWorld.getEntitiesByClass(WitherSkullEntity.class,
                        omen.getBoundingBox().expand(80.0), projectile -> projectile.getOwner() == omen)) {
                    skull.discard();
                }
            }
            if (!omen.isAlive() || serverTicks >= entry.getValue()) {
                omen.discard();
                iterator.remove();
            }
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
