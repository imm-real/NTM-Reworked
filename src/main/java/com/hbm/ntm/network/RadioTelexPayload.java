package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.RadioTelexBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RadioTelexPayload(BlockPos position, String command, String[] lines,
                                String txChannel, String rxChannel)
        implements CustomPacketPayload {
    public static final Type<RadioTelexPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "radio_telex"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadioTelexPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                buffer.writeBlockPos(payload.position);
                buffer.writeUtf(payload.command, 8);
                for (int i = 0; i < 5; i++) {
                    buffer.writeUtf(i < payload.lines.length ? payload.lines[i] : "", 33);
                }
                buffer.writeUtf(payload.txChannel, 10);
                buffer.writeUtf(payload.rxChannel, 10);
            }, buffer -> {
                BlockPos position = buffer.readBlockPos();
                String command = buffer.readUtf(8);
                String[] lines = new String[5];
                for (int i = 0; i < 5; i++) lines[i] = buffer.readUtf(33);
                return new RadioTelexPayload(position, command, lines,
                        buffer.readUtf(10), buffer.readUtf(10));
            });

    @Override
    public Type<RadioTelexPayload> type() {
        return TYPE;
    }

    public static void handle(RadioTelexPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().getBlockEntity(payload.position)
                    instanceof RadioTelexBlockEntity telex && telex.stillValid(context.player())) {
                telex.handleCommand(payload.command, payload.lines,
                        payload.txChannel, payload.rxChannel);
            }
        });
    }
}
