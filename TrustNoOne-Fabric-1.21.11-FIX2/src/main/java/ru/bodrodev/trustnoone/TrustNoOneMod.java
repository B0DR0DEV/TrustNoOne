package ru.bodrodev.trustnoone;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.bodrodev.trustnoone.config.TrustNoOneConfig;
import ru.bodrodev.trustnoone.entities.ModEntities;
import ru.bodrodev.trustnoone.network.ReviveHoldPayload;
import ru.bodrodev.trustnoone.server.HorrorEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Entrypoint for TrustNoOne (horror). */
public final class TrustNoOneMod implements ModInitializer {
    public static final String MOD_ID = "trustnoone";
    public static final Logger LOGGER = LoggerFactory.getLogger("TrustNoOne");
    public static TrustNoOneConfig CONFIG;

    @Override
    public void onInitialize() {
        CONFIG = TrustNoOneConfig.load();
        PayloadTypeRegistry.playC2S().register(ReviveHoldPayload.ID, ReviveHoldPayload.CODEC);
        ModEntities.register();
        registerEyeOfEnderAlternative();
        HorrorEvents.register();
        LOGGER.info("TrustNoOne (horror) is ready. Config: {}", "config/trustnoone.json");
    }

    /** Adds a small optional Ender Eye chance to the vanilla Enderman loot table. */
    private static void registerEyeOfEnderAlternative() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!CONFIG.eyeOfEnder.enabled || !source.isBuiltin()) return;
            var endermanLoot = EntityType.ENDERMAN.getLootTableKey().orElse(null);
            if (endermanLoot == null || !endermanLoot.equals(key)) return;
            tableBuilder.pool(LootPool.builder()
                    .rolls(ConstantLootNumberProvider.create(1.0F))
                    .with(ItemEntry.builder(Items.ENDER_EYE))
                    .conditionally(RandomChanceLootCondition.builder((float) CONFIG.eyeOfEnder.endermanDropChance)));
        });
    }
}
