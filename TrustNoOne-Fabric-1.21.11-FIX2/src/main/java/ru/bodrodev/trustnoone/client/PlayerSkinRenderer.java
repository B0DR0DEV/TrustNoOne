package ru.bodrodev.trustnoone.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Identifier;
import net.minecraft.entity.LivingEntity;

import java.util.UUID;
import java.util.function.Function;

/** Renders player-shaped entities with the cached skin of the player they copied. */
public final class PlayerSkinRenderer<T extends LivingEntity> extends LivingEntityRenderer<T, PlayerEntityRenderState, PlayerEntityModel> {
    private static final Identifier DEFAULT_SKIN = Identifier.of("minecraft", "textures/entity/player/wide/steve.png");
    private final Function<T, UUID> copiedUuid;

    public PlayerSkinRenderer(EntityRendererFactory.Context context, Function<T, UUID> copiedUuid) {
        super(context, new PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER), false), 0.45F);
        this.copiedUuid = copiedUuid;
        // Render the bread/item temporarily placed in the imposter's hand.
        this.addFeature(new HeldItemFeatureRenderer<>(this));
    }

    @Override
    public PlayerEntityRenderState createRenderState() {
        return new PlayerEntityRenderState();
    }

    @Override
    public void updateRenderState(T entity, PlayerEntityRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) return;
        UUID sourceUuid = copiedUuid.apply(entity);
        if (sourceUuid == null) return;
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(sourceUuid);
        if (entry != null) {
            SkinTextures textures = entry.getSkinTextures();
            state.skinTextures = textures;
        }
    }

    @Override
    public Identifier getTexture(PlayerEntityRenderState state) {
        return state.skinTextures == null ? DEFAULT_SKIN : state.skinTextures.body().texturePath();
    }

}
