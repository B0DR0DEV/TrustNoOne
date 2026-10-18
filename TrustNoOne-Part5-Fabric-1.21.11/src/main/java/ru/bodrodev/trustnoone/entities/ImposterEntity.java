package ru.bodrodev.trustnoone.entities;

import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.storage.ReadView;
import net.minecraft.world.World;
import net.minecraft.state.property.Properties;
import ru.bodrodev.trustnoone.TrustNoOneMod;

import java.util.UUID;

/**
 * Hostile player-shaped entity. It freezes when a nearby player visibly watches it,
 * then resumes pursuit and gains speed while it remains unobserved.
 */
public final class ImposterEntity extends HostileEntity {
    private static final String UUID_TAG = "trustnoone_copy_uuid:";
    private static final String SKIN_TAG = "trustnoone_skin:";
    private static final TrackedData<String> COPY_UUID = DataTracker.registerData(ImposterEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> COPY_NAME = DataTracker.registerData(ImposterEntity.class, TrackedDataHandlerRegistry.STRING);
    private int unseenTicks;
    private int fakeEatCooldown = 80;
    private int observerWatchedTicks;
    private static final String OBSERVER_TAG = "trustnoone_observer";
    private int fakeEatTicksLeft = 0;
    private boolean beingWatched;

    public ImposterEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createImposterAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.MAX_HEALTH, 24.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.ATTACK_DAMAGE, 3.0)
                .add(EntityAttributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(COPY_UUID, "");
        builder.add(COPY_NAME, "Steve");
    }

    @Override
    protected void readCustomData(ReadView nbt) {
        super.readCustomData(nbt);
        String uuid = getCommandTags().stream().filter(tag -> tag.startsWith(UUID_TAG)).findFirst()
                .map(tag -> tag.substring(UUID_TAG.length())).orElse("");
        String name = getCommandTags().stream().filter(tag -> tag.startsWith(SKIN_TAG)).findFirst()
                .map(tag -> tag.substring(SKIN_TAG.length())).orElse("Steve");
        getDataTracker().set(COPY_UUID, uuid);
        getDataTracker().set(COPY_NAME, name);
        boolean showName = TrustNoOneMod.CONFIG != null && !TrustNoOneMod.CONFIG.names.hideImposterNames;
        setCustomName(showName && !name.isBlank() ? Text.literal(name) : null);
        setCustomNameVisible(showName);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new MeleeAttackGoal(this, 1.12, true));
        goalSelector.add(4, new WanderAroundFarGoal(this, 0.76));
        goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 12.0F));
        targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    public void setCopiedPlayer(UUID uuid, String name) {
        getCommandTags().removeIf(tag -> tag.startsWith(UUID_TAG) || tag.startsWith(SKIN_TAG));
        getCommandTags().add(UUID_TAG + uuid);
        getCommandTags().add(SKIN_TAG + name);
        getDataTracker().set(COPY_UUID, uuid.toString());
        getDataTracker().set(COPY_NAME, name);
        boolean showName = !TrustNoOneMod.CONFIG.names.hideImposterNames;
        setCustomName(showName ? Text.literal(name) : null);
        setCustomNameVisible(showName);
    }

    public UUID getCopiedPlayerUuid() {
        String value = getDataTracker().get(COPY_UUID);
        if (value == null || value.isEmpty()) {
            value = getCommandTags().stream().filter(tag -> tag.startsWith(UUID_TAG)).findFirst()
                    .map(tag -> tag.substring(UUID_TAG.length())).orElse("");
            if (!value.isEmpty()) getDataTracker().set(COPY_UUID, value);
        }
        try { return UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; }
    }

    public String getCopiedName() {
        String value = getDataTracker().get(COPY_NAME);
        if (value == null || value.isEmpty()) {
            value = getCommandTags().stream().filter(tag -> tag.startsWith(SKIN_TAG)).findFirst()
                    .map(tag -> tag.substring(SKIN_TAG.length())).orElse("Steve");
            getDataTracker().set(COPY_NAME, value);
        }
        return value;
    }

    public boolean isBeingWatched() { return beingWatched; }

    @Override
    public void tick() {
        super.tick();
        if (getEntityWorld().isClient()) return;

        if (getCommandTags().contains(OBSERVER_TAG)) {
            tickObserver();
            return;
        }

        PlayerEntity copied = findCopiedPlayer();
        beingWatched = isWatchedByAnyPlayer();
        int huntDelay = TrustNoOneMod.CONFIG.imposter.huntDelayTicks;

        if (beingWatched) {
            // The Mimic freezes the moment someone directly looks at it.
            unseenTicks = 0;
            setAiDisabled(true);
            setTarget(null);
            setVelocity(Vec3d.ZERO);
            if (getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) != null) {
                getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(0.0);
            }
            clearFakeEating();
        } else {
            unseenTicks = Math.min(12000, unseenTicks + 1);
            boolean hunting = unseenTicks >= huntDelay || copied == null;
            if (!hunting) {
                // During the imitation phase it echoes the copied player's facing and motion.
                setAiDisabled(true);
                setTarget(null);
                if (copied != null) {
                    setYaw(copied.getYaw());
                    setPitch(copied.getPitch());
                    setHeadYaw(copied.getHeadYaw());
                    setBodyYaw(copied.getBodyYaw());
                    if (TrustNoOneMod.CONFIG.imposter.copyCrouchingAndSprinting) {
                        setSprinting(copied.isSprinting());
                        setSneaking(copied.isSneaking());
                    }
                    if (TrustNoOneMod.CONFIG.imposter.copyJumping && copied.getVelocity().y > 0.22 && isOnGround()) {
                        jump();
                    }
                    Vec3d sourceVelocity = copied.getVelocity();
                    // Copy horizontal stride and meaningful jump/fall motion while keeping the Mimic's own footing independent.
                    double verticalVelocity = Math.abs(sourceVelocity.y) > 0.03 ? sourceVelocity.y : getVelocity().y;
                    setVelocity(sourceVelocity.x * 0.88, verticalVelocity, sourceVelocity.z * 0.88);
                }
                if (getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) != null) {
                    getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(0.25);
                }
                fakeEatingTick();
            } else {
                // The longer it remains unseen, the faster it becomes and the more aggressively it hunts.
                setAiDisabled(false);
                double progress = Math.min(1.0, (double) (unseenTicks - huntDelay) / 800.0);
                double speed = 0.30 + progress * 0.28;
                if (getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) != null) {
                    getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(speed);
                }
                PlayerEntity nearest = getEntityWorld().getClosestPlayer(this, 32.0);
                if (nearest != null && nearest.isAlive() && !nearest.isSpectator()) setTarget(nearest);
                fakeEatCooldown = Math.max(fakeEatCooldown, 80);
            }
        }

        if (TrustNoOneMod.CONFIG.imposter.openDoors && !beingWatched) tryOpenNearbyHatch();
        if (getEntityWorld().getClosestPlayer(this, TrustNoOneMod.CONFIG.imposter.despawnDistance) == null) {
            discard();
        }
    }

    /** The Observer is a passive silhouette. It remains motionless and disappears once noticed. */
    private void tickObserver() {
        setAiDisabled(true);
        setTarget(null);
        setVelocity(Vec3d.ZERO);
        clearFakeEating();
        if (isWatchedByAnyPlayer()) {
            observerWatchedTicks++;
            if (observerWatchedTicks >= 7) {
                getEntityWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                        SoundCategory.HOSTILE, 0.30F, 1.35F);
                discard();
            }
        } else {
            observerWatchedTicks = 0;
        }
    }

    private void clearFakeEating() {
        fakeEatTicksLeft = 0;
        if (!getMainHandStack().isEmpty()) setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
    }

    private PlayerEntity findCopiedPlayer() {
        UUID uuid = getCopiedPlayerUuid();
        if (uuid == null) return null;
        for (PlayerEntity player : getEntityWorld().getPlayers()) {
            if (player.getUuid().equals(uuid)) return player;
        }
        return null;
    }

    private boolean isWatchedByAnyPlayer() {
        double threshold = TrustNoOneMod.CONFIG.imposter.noticeDot;
        for (PlayerEntity player : getEntityWorld().getPlayers()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            double watchDistance = getCommandTags().contains(OBSERVER_TAG) ? 42.0 : 28.0;
            if (player.squaredDistanceTo(this) > watchDistance * watchDistance || !player.canSee(this)) continue;
            Vec3d toward = getEntityPos().add(0, getHeight() * 0.45, 0).subtract(player.getEyePos()).normalize();
            if (player.getRotationVec(1.0F).dotProduct(toward) >= threshold) return true;
        }
        return false;
    }

    private void tryOpenNearbyHatch() {
        BlockPos center = getBlockPos();
        for (BlockPos pos : BlockPos.iterateOutwards(center, 1, 1, 1)) {
            BlockState state = getEntityWorld().getBlockState(pos);
            if ((state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapdoorBlock
                    || state.getBlock() instanceof FenceGateBlock)
                    && state.contains(Properties.OPEN) && !state.get(Properties.OPEN)) {
                getEntityWorld().setBlockState(pos, state.with(Properties.OPEN, true), Block.NOTIFY_ALL);
                break;
            }
        }
    }

    private void fakeEatingTick() {
        if (fakeEatTicksLeft > 0) {
            fakeEatTicksLeft--;
            if (fakeEatTicksLeft % 7 == 0) swingHand(Hand.MAIN_HAND);
            if (fakeEatTicksLeft == 0) setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            return;
        }
        if (--fakeEatCooldown <= 0) {
            fakeEatCooldown = 100 + random.nextInt(180);
            fakeEatTicksLeft = TrustNoOneMod.CONFIG.imposter.fakeEatDurationTicks;
            setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BREAD));
            swingHand(Hand.MAIN_HAND);
        }
    }
}
