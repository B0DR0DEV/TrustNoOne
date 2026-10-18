package ru.bodrodev.trustnoone.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.EntityHitResult;
import ru.bodrodev.trustnoone.TrustNoOneMod;
import ru.bodrodev.trustnoone.config.TrustNoOneConfig;
import ru.bodrodev.trustnoone.network.ConfigSyncPayload;
import ru.bodrodev.trustnoone.entities.CorpseEntity;
import ru.bodrodev.trustnoone.entities.ImposterEntity;
import ru.bodrodev.trustnoone.entities.ModEntities;
import ru.bodrodev.trustnoone.network.ReviveHoldPayload;

/** Client renderer registration and key-hold synchronization for revives. */
public final class TrustNoOneClient implements ClientModInitializer {
    private static boolean lastHolding;
    private static int lastTarget = -1;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.ID, (payload, context) ->
                context.client().execute(() -> TrustNoOneMod.CONFIG = TrustNoOneConfig.fromJson(payload.json())));

        EntityRendererRegistry.register(ModEntities.IMPOSTER,
                context -> new PlayerSkinRenderer<>(context, ImposterEntity::getCopiedPlayerUuid));
        EntityRendererRegistry.register(ModEntities.CORPSE,
                context -> new PlayerSkinRenderer<>(context, CorpseEntity::getVictimUuid));
        HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS,
                Identifier.of(TrustNoOneMod.MOD_ID, "ambient_darkness"),
                (context, tickCounter) -> renderAmbientDarkness(context));
        ClientTickEvents.END_CLIENT_TICK.register(TrustNoOneClient::tickClient);
    }


    /** Adds a steady, low-opacity tint to the world without the pulsing vanilla Darkness effect. */
    private static void renderAmbientDarkness(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        TrustNoOneConfig config = TrustNoOneMod.CONFIG;
        if (client.player == null || client.world == null || config == null
                || !config.darkness.enabled || client.currentScreen != null) {
            return;
        }

        TrustNoOneConfig.Darkness darkness = config.darkness;
        long dayTime = Math.floorMod(client.world.getTimeOfDay(), 24000L);
        boolean night = darkness.darkenNights && dayTime >= 13000L && dayTime < 23000L;
        boolean darkCave = darkness.darkenCaves
                && client.player.getY() < client.world.getSeaLevel() - 5
                && client.world.getLightLevel(client.player.getBlockPos()) <= darkness.lightThreshold;

        int alpha = night ? darkness.nightOverlayAlpha : 0;
        if (darkCave) alpha = Math.max(alpha, darkness.caveOverlayAlpha);
        if (alpha <= 0) return;

        int argb = (Math.min(alpha, 80) & 0xFF) << 24;
        context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), argb);
    }

    private static void tickClient(MinecraftClient client) {
        if (client.player == null || client.world == null || client.getNetworkHandler() == null) {
            lastHolding = false;
            lastTarget = -1;
            return;
        }

        boolean holding = false;
        int targetId = -1;
        if (client.options.useKey.isPressed() && client.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof CorpseEntity corpse) {
            holding = true;
            targetId = corpse.getId();
        }

        if (holding != lastHolding || (holding && targetId != lastTarget)) {
            ClientPlayNetworking.send(new ReviveHoldPayload(targetId, holding));
            lastHolding = holding;
            lastTarget = targetId;
        }
    }
}
