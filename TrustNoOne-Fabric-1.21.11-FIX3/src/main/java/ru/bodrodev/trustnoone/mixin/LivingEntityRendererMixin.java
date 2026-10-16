package ru.bodrodev.trustnoone.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.bodrodev.trustnoone.TrustNoOneMod;
import ru.bodrodev.trustnoone.entities.ImposterEntity;

/** Hides only player/impostor labels and leaves other living-entity labels untouched. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true)
    private void trustnoone$hideNameplates(LivingEntity entity, double squaredDistanceToCamera,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (TrustNoOneMod.CONFIG == null) return;
        if (entity instanceof PlayerEntity && TrustNoOneMod.CONFIG.names.hidePlayerNames) {
            cir.setReturnValue(false);
        } else if (entity instanceof ImposterEntity && TrustNoOneMod.CONFIG.names.hideImposterNames) {
            cir.setReturnValue(false);
        }
    }
}
