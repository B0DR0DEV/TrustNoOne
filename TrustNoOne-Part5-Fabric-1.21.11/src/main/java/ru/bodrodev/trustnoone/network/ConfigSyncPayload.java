package ru.bodrodev.trustnoone.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import ru.bodrodev.trustnoone.TrustNoOneMod;

/** Server-authoritative configuration for client visuals in multiplayer/LAN. */
public record ConfigSyncPayload(String json) implements CustomPayload {
    public static final Id<ConfigSyncPayload> ID =
            new Id<>(Identifier.of(TrustNoOneMod.MOD_ID, "config_sync"));

    public static final PacketCodec<RegistryByteBuf, ConfigSyncPayload> CODEC = new PacketCodec<>() {
        @Override
        public ConfigSyncPayload decode(RegistryByteBuf buf) {
            return new ConfigSyncPayload(buf.readString(32767));
        }

        @Override
        public void encode(RegistryByteBuf buf, ConfigSyncPayload payload) {
            buf.writeString(payload.json(), 32767);
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
