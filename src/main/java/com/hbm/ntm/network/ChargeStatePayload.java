package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.ChargeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ChargeStatePayload(BlockPos position, int timer, boolean started) implements CustomPacketPayload {
    public static final Type<ChargeStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "charge_state")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ChargeStatePayload> STREAM_CODEC =
            StreamCodec.ofMember(ChargeStatePayload::encode, ChargeStatePayload::decode);

    @Override
    public Type<ChargeStatePayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        BlockPos.STREAM_CODEC.encode(buffer, position);
        buffer.writeInt(timer);
        buffer.writeBoolean(started);
    }

    private static ChargeStatePayload decode(RegistryFriendlyByteBuf buffer) {
        return new ChargeStatePayload(
                BlockPos.STREAM_CODEC.decode(buffer),
                buffer.readInt(),
                buffer.readBoolean()
        );
    }

    public static void handle(ChargeStatePayload payload, IPayloadContext context) {
        if (context.player().level().getBlockEntity(payload.position()) instanceof ChargeBlockEntity charge) {
            charge.applyClientState(payload.timer(), payload.started());
        }
    }
}
