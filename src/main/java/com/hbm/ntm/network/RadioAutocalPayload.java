package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.RadioAutocalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RadioAutocalPayload(BlockPos position, String command, String payload)
        implements CustomPacketPayload {
    public static final Type<RadioAutocalPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "radio_autocal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadioAutocalPayload> STREAM_CODEC =
            StreamCodec.of((buffer, value) -> {
                buffer.writeBlockPos(value.position);
                buffer.writeUtf(value.command, 8);
                buffer.writeUtf(value.payload, 1_048_576);
            }, buffer -> new RadioAutocalPayload(buffer.readBlockPos(), buffer.readUtf(8),
                    buffer.readUtf(1_048_576)));

    @Override
    public Type<RadioAutocalPayload> type() {
        return TYPE;
    }

    public static void handle(RadioAutocalPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().getBlockEntity(payload.position)
                    instanceof RadioAutocalBlockEntity autocal && autocal.stillValid(context.player())) {
                autocal.handleCommand(payload.command, payload.payload);
            }
        });
    }
}
