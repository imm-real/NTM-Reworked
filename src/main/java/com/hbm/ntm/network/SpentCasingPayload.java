package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.Consumer;

public record SpentCasingPayload(int preset, double x, double y, double z,
                                 double motionX, double motionY, double motionZ,
                                 float yaw, float pitch, float momentumPitch, float momentumYaw)
        implements CustomPacketPayload {
    private static Consumer<SpentCasingPayload> clientHandler = payload -> { };
    public static final Type<SpentCasingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "spent_casing"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpentCasingPayload> STREAM_CODEC =
            StreamCodec.ofMember(SpentCasingPayload::encode, SpentCasingPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(preset);
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeDouble(motionX);
        buffer.writeDouble(motionY);
        buffer.writeDouble(motionZ);
        buffer.writeFloat(yaw);
        buffer.writeFloat(pitch);
        buffer.writeFloat(momentumPitch);
        buffer.writeFloat(momentumYaw);
    }

    private static SpentCasingPayload decode(RegistryFriendlyByteBuf buffer) {
        return new SpentCasingPayload(buffer.readVarInt(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    @Override
    public Type<SpentCasingPayload> type() {
        return TYPE;
    }

    public static void handle(SpentCasingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientHandler.accept(payload));
    }

    public static void installClientHandler(Consumer<SpentCasingPayload> handler) {
        clientHandler = handler;
    }
}
