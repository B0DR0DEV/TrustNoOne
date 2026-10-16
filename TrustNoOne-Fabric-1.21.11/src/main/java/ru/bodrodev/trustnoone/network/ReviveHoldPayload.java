package ru.bodrodev.trustnoone.network;

import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.util.Identifier;
import ru.bodrodev.trustnoone.TrustNoOneMod;

/** Client-to-server updates for the press-and-hold revive interaction. */
public record ReviveHoldPayload(int targetEntityId, boolean holding) implements CustomPayload {
    public static final Id<ReviveHoldPayload> ID = new Id<>(Identifier.of(TrustNoOneMod.MOD_ID, "revive_hold"));
    public static final PacketCodec<RegistryByteBuf, ReviveHoldPayload> CODEC = new PacketCodec<>() {
        @Override
        public ReviveHoldPayload decode(RegistryByteBuf buf) {
            return new ReviveHoldPayload(buf.readVarInt(), buf.readBoolean());
        }
        @Override
        public void encode(RegistryByteBuf buf, ReviveHoldPayload value) {
            buf.writeVarInt(value.targetEntityId());
            buf.writeBoolean(value.holding());
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
