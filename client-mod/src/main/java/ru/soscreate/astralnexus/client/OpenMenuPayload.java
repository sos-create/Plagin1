package ru.soscreate.astralnexus.client;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OpenMenuPayload() implements CustomPayload {
    public static final Id<OpenMenuPayload> ID =
            new Id<>(Identifier.of("astralnexus", "open_menu"));
    public static final PacketCodec<RegistryByteBuf, OpenMenuPayload> CODEC =
            PacketCodec.unit(new OpenMenuPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
