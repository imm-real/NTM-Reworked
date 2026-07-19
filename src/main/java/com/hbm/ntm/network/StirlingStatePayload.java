package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.StirlingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record StirlingStatePayload(BlockPos position, long power, int heat, boolean hasCog)
        implements CustomPacketPayload {
    public static final Type<StirlingStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "stirling_state")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, StirlingStatePayload> STREAM_CODEC =
            StreamCodec.ofMember(StirlingStatePayload::encode, StirlingStatePayload::decode);

    @Override
    public Type<StirlingStatePayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        BlockPos.STREAM_CODEC.encode(buffer, position);
        buffer.writeLong(power);
        buffer.writeInt(heat);
        buffer.writeBoolean(hasCog);
    }

    private static StirlingStatePayload decode(RegistryFriendlyByteBuf buffer) {
        return new StirlingStatePayload(
                BlockPos.STREAM_CODEC.decode(buffer),
                buffer.readLong(),
                buffer.readInt(),
                buffer.readBoolean()
        );
    }

    public static void handle(StirlingStatePayload payload, IPayloadContext context) {
        if (context.player().level().getBlockEntity(payload.position()) instanceof StirlingBlockEntity stirling) {
            stirling.applyClientSnapshot(payload.power(), payload.heat(), payload.hasCog());
        }
    }
}
