package ru.bodrodev.trustnoone.entities;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.storage.ReadView;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.World;

import java.util.UUID;

/** A non-hostile, immobile body which links back to the dead player UUID. */
public final class CorpseEntity extends PathAwareEntity {
    private static final String VICTIM_TAG = "trustnoone_victim:";
    private static final String SKIN_TAG = "trustnoone_skin:";
    private static final TrackedData<String> VICTIM_UUID = DataTracker.registerData(CorpseEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> VICTIM_NAME = DataTracker.registerData(CorpseEntity.class, TrackedDataHandlerRegistry.STRING);

    public CorpseEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
        setAiDisabled(true);
        setPersistent();
        setPose(EntityPose.SLEEPING);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(VICTIM_UUID, "");
        builder.add(VICTIM_NAME, "Steve");
    }

    @Override
    protected void readCustomData(ReadView nbt) {
        super.readCustomData(nbt);
        String uuid = getCommandTags().stream().filter(tag -> tag.startsWith(VICTIM_TAG)).findFirst()
                .map(tag -> tag.substring(VICTIM_TAG.length())).orElse("");
        String name = getCommandTags().stream().filter(tag -> tag.startsWith(SKIN_TAG)).findFirst()
                .map(tag -> tag.substring(SKIN_TAG.length())).orElse("Steve");
        getDataTracker().set(VICTIM_UUID, uuid);
        getDataTracker().set(VICTIM_NAME, name);
    }

    public static DefaultAttributeContainer.Builder createCorpseAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.MAX_HEALTH, 1.0);
    }

    public void setVictim(UUID uuid, String playerName) {
        getCommandTags().removeIf(tag -> tag.startsWith(VICTIM_TAG) || tag.startsWith(SKIN_TAG));
        getCommandTags().add(VICTIM_TAG + uuid);
        getCommandTags().add(SKIN_TAG + playerName);
        getDataTracker().set(VICTIM_UUID, uuid.toString());
        getDataTracker().set(VICTIM_NAME, playerName);
    }

    public UUID getVictimUuid() {
        String value = getDataTracker().get(VICTIM_UUID);
        if (value == null || value.isEmpty()) {
            value = getCommandTags().stream().filter(tag -> tag.startsWith(VICTIM_TAG)).findFirst()
                    .map(tag -> tag.substring(VICTIM_TAG.length())).orElse("");
            if (!value.isEmpty()) getDataTracker().set(VICTIM_UUID, value);
        }
        try { return UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; }
    }

    public String getCopiedName() {
        String value = getDataTracker().get(VICTIM_NAME);
        if (value == null || value.isEmpty()) {
            value = getCommandTags().stream().filter(tag -> tag.startsWith(SKIN_TAG)).findFirst()
                    .map(tag -> tag.substring(SKIN_TAG.length())).orElse("Steve");
            getDataTracker().set(VICTIM_NAME, value);
        }
        return value;
    }

    @Override
    public void tick() {
        super.tick();
        setVelocity(0, 0, 0);
        setNoGravity(true);
        setAiDisabled(true);
        getVictimUuid();
    }
}
