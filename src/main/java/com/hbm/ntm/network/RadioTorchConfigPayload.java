package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.RadioTorchMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RadioTorchConfigPayload(boolean polling, boolean customMapping, boolean descending,
                                      String[] channels, String[] names, String[] mapping, byte[] conditions)
        implements CustomPacketPayload {
    public static final Type<RadioTorchConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "radio_torch_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadioTorchConfigPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBoolean(payload.polling); buffer.writeBoolean(payload.customMapping); buffer.writeBoolean(payload.descending);
                writeStrings(buffer, payload.channels, 8, 15); writeStrings(buffer, payload.names, 8, 25);
                writeStrings(buffer, payload.mapping, 16, 32);
                for (int i = 0; i < 16; i++) buffer.writeByte(i < payload.conditions.length ? payload.conditions[i] : 0);
            }, buffer -> {
                boolean polling = buffer.readBoolean(), custom = buffer.readBoolean(), descending = buffer.readBoolean();
                String[] channels = readStrings(buffer, 8, 15), names = readStrings(buffer, 8, 25), mapping = readStrings(buffer, 16, 32);
                byte[] conditions = new byte[16]; for (int i = 0; i < 16; i++) conditions[i] = buffer.readByte();
                return new RadioTorchConfigPayload(polling, custom, descending, channels, names, mapping, conditions);
            });

    private static void writeStrings(RegistryFriendlyByteBuf buffer, String[] values, int count, int max) {
        for (int i = 0; i < count; i++) buffer.writeUtf(i < values.length ? values[i] : "", max);
    }
    private static String[] readStrings(RegistryFriendlyByteBuf buffer, int count, int max) {
        String[] values = new String[count]; for (int i = 0; i < count; i++) values[i] = buffer.readUtf(max); return values;
    }
    @Override public Type<RadioTorchConfigPayload> type() { return TYPE; }
    public static void handle(RadioTorchConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof RadioTorchMenu menu && menu.blockEntity() != null)
                menu.blockEntity().configure(payload.polling, payload.customMapping, payload.descending,
                        payload.channels, payload.names, payload.mapping, payload.conditions);
        });
    }
}
