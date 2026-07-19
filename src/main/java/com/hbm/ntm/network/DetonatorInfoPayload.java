package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.BiConsumer;

/** Source {@code PlayerInformPacket} channel used by Rangefinder and Laser Detonator. */
public record DetonatorInfoPayload(Component message, int durationMillis) implements CustomPacketPayload {
    private static BiConsumer<Component, Integer> clientHandler = (message, duration) -> { };
    public static final Type<DetonatorInfoPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "detonator_info"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DetonatorInfoPayload> STREAM_CODEC =
            StreamCodec.ofMember(DetonatorInfoPayload::encode, DetonatorInfoPayload::decode);

    public DetonatorInfoPayload {
        durationMillis = Math.max(1, durationMillis);
    }

    public static DetonatorInfoPayload sourceDefault(Component message) {
        // PlayerInformPacket with millis=0 delegated to
        // Detonator tooltips get one second to deliver the bad news.
        return new DetonatorInfoPayload(message, 1_000);
    }

    @Override
    public Type<DetonatorInfoPayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        ComponentSerialization.STREAM_CODEC.encode(buffer, message);
        buffer.writeVarInt(durationMillis);
    }

    private static DetonatorInfoPayload decode(RegistryFriendlyByteBuf buffer) {
        return new DetonatorInfoPayload(ComponentSerialization.STREAM_CODEC.decode(buffer), buffer.readVarInt());
    }

    public static void handle(DetonatorInfoPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientHandler.accept(payload.message, payload.durationMillis));
    }

    public static void installClientHandler(BiConsumer<Component, Integer> handler) {
        clientHandler = handler;
    }
}
