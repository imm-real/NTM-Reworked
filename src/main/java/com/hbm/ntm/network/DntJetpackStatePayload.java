package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.BiConsumer;

public record DntJetpackStatePayload(boolean jetpackEnabled, boolean hudEnabled) implements CustomPacketPayload {
    private static BiConsumer<Boolean, Boolean> clientHandler = (jetpack, hud) -> { };
    public static final Type<DntJetpackStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "dnt_jetpack_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DntJetpackStatePayload> STREAM_CODEC =
            StreamCodec.ofMember(DntJetpackStatePayload::encode, DntJetpackStatePayload::decode);

    @Override
    public Type<DntJetpackStatePayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(jetpackEnabled);
        buffer.writeBoolean(hudEnabled);
    }

    private static DntJetpackStatePayload decode(RegistryFriendlyByteBuf buffer) {
        return new DntJetpackStatePayload(buffer.readBoolean(), buffer.readBoolean());
    }

    public static void handle(DntJetpackStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientHandler.accept(payload.jetpackEnabled, payload.hudEnabled));
    }

    public static void installClientHandler(BiConsumer<Boolean, Boolean> handler) {
        clientHandler = handler;
    }
}
