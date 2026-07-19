package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.HeatExchangerMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HeatExchangerControlPayload(int amountToCool, int tickDelay) implements CustomPacketPayload {
    public static final Type<HeatExchangerControlPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "heat_exchanger_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HeatExchangerControlPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                buffer.writeVarInt(payload.amountToCool);
                buffer.writeVarInt(payload.tickDelay);
            }, buffer -> new HeatExchangerControlPayload(buffer.readVarInt(), buffer.readVarInt()));

    @Override public Type<HeatExchangerControlPayload> type() { return TYPE; }

    public static void handle(HeatExchangerControlPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof HeatExchangerMenu menu
                    && menu.blockEntity() != null) {
                menu.blockEntity().setCycleControls(payload.amountToCool, payload.tickDelay);
            }
        });
    }
}
