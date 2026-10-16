package ru.bodrodev.trustnoone.mixin;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.bodrodev.trustnoone.TrustNoOneMod;

/** Optional removal of sky bodies, controlled from the config. */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererSkyMixin {
    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void trustnoone$hideSky(CallbackInfo ci) {
        if (TrustNoOneMod.CONFIG != null && TrustNoOneMod.CONFIG.darkness.enabled
                && !TrustNoOneMod.CONFIG.darkness.showSunAndMoon) {
            ci.cancel();
        }
    }
}
