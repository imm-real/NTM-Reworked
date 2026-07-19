package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record GuideBookOpenPayload() implements CustomPacketPayload {
    private static Runnable clientOpener = () -> { };
    public static final Type<GuideBookOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "open_guide_book"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GuideBookOpenPayload> STREAM_CODEC =
            StreamCodec.unit(new GuideBookOpenPayload());

    @Override
    public Type<GuideBookOpenPayload> type() {
        return TYPE;
    }

    public static void handle(GuideBookOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(clientOpener);
    }

    public static void installClientHandler(Runnable opener) {
        clientOpener = opener;
    }
}
