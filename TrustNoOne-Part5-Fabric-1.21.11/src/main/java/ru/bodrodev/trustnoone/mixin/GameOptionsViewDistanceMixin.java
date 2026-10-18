package ru.bodrodev.trustnoone.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.bodrodev.trustnoone.TrustNoOneMod;

/** Shortens view distance at night/in dark caves to make darkness meaningful without a monochrome filter. */
@Mixin(GameOptions.class)
public abstract class GameOptionsViewDistanceMixin {
    @Inject(method = "getClampedViewDistance", at = @At("RETURN"), cancellable = true)
    private void trustnoone$limitVisibility(CallbackInfoReturnable<Integer> cir) {
        if (TrustNoOneMod.CONFIG == null || !TrustNoOneMod.CONFIG.darkness.enabled) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        var cfg = TrustNoOneMod.CONFIG.darkness;
        long time = Math.floorMod(client.world.getTimeOfDay(), 24000L);
        boolean night = cfg.darkenNights && time >= 13000L && time < 23000L;
        boolean cave = cfg.darkenCaves && client.world.getLightLevel(client.player.getBlockPos()) <= cfg.lightThreshold;
        if (night || cave) cir.setReturnValue(Math.min(cir.getReturnValue(), cfg.visibilityRadiusChunks));
    }
}
