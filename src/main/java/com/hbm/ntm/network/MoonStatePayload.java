package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.BiConsumer;

/** Persistent state sync plus a one-shot flag for the destruction cinematic. */
public record MoonStatePayload(boolean destroyed, boolean explode) implements CustomPacketPayload {
    private static BiConsumer<Boolean, Boolean> clientHandler = (destroyed, explode) -> { };
    public static final Type<MoonStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "moon_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MoonStatePayload> STREAM_CODEC =
            StreamCodec.ofMember(MoonStatePayload::encode, MoonStatePayload::decode);

    @Override
    public Type<MoonStatePayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(destroyed);
        buffer.writeBoolean(explode);
    }

    private static MoonStatePayload decode(RegistryFriendlyByteBuf buffer) {
        return new MoonStatePayload(buffer.readBoolean(), buffer.readBoolean());
    }

    public static void handle(MoonStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientHandler.accept(payload.destroyed, payload.explode));
    }

    public static void installClientHandler(BiConsumer<Boolean, Boolean> handler) {
        clientHandler = handler;
    }
}
