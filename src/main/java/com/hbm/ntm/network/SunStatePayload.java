package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.BiConsumer;

/** Persistent solar state sync plus the one-shot supernova cinematic flag. */
public record SunStatePayload(boolean destroyed, boolean explode) implements CustomPacketPayload {
    private static BiConsumer<Boolean, Boolean> clientHandler = (destroyed, explode) -> { };
    public static final Type<SunStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "sun_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SunStatePayload> STREAM_CODEC =
            StreamCodec.ofMember(SunStatePayload::encode, SunStatePayload::decode);

    @Override
    public Type<SunStatePayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(destroyed);
        buffer.writeBoolean(explode);
    }

    private static SunStatePayload decode(RegistryFriendlyByteBuf buffer) {
        return new SunStatePayload(buffer.readBoolean(), buffer.readBoolean());
    }

    public static void handle(SunStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientHandler.accept(payload.destroyed, payload.explode));
    }

    public static void installClientHandler(BiConsumer<Boolean, Boolean> handler) {
        clientHandler = handler;
    }
}
