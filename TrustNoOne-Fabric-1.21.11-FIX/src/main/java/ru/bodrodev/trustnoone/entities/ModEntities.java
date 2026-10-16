package ru.bodrodev.trustnoone.entities;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import ru.bodrodev.trustnoone.TrustNoOneMod;

/** Registration point for the two persistent entities used by the round. */
public final class ModEntities {
    public static final EntityType<ImposterEntity> IMPOSTER = register("imposter", EntityType.Builder
            .create(ImposterEntity::new, SpawnGroup.MONSTER)
            .dimensions(0.6F, 1.8F).maxTrackingRange(12).trackingTickInterval(2));

    public static final EntityType<CorpseEntity> CORPSE = register("corpse", EntityType.Builder
            .create(CorpseEntity::new, SpawnGroup.MISC)
            .dimensions(0.6F, 0.35F).maxTrackingRange(12).trackingTickInterval(2));

    private static <T extends net.minecraft.entity.Entity> EntityType<T> register(
            String path, EntityType.Builder<T> builder) {
        Identifier id = Identifier.of(TrustNoOneMod.MOD_ID, path);
        return Registry.register(Registries.ENTITY_TYPE, id,
                builder.build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, id)));
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(IMPOSTER, ImposterEntity.createImposterAttributes());
        FabricDefaultAttributeRegistry.register(CORPSE, CorpseEntity.createCorpseAttributes());
        TrustNoOneMod.LOGGER.info("Registered TrustNoOne entities");
    }

    private ModEntities() { }
}
