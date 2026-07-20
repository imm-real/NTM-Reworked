package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.GasTurbineBlockEntity;
import com.hbm.ntm.inventory.GasTurbineMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record GasTurbineControlPayload(GasTurbineBlockEntity.Control control, int value)
        implements CustomPacketPayload {
    public static final Type<GasTurbineControlPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "gas_turbine_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GasTurbineControlPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                buffer.writeEnum(payload.control);
                buffer.writeVarInt(payload.value);
            }, buffer -> new GasTurbineControlPayload(
                    buffer.readEnum(GasTurbineBlockEntity.Control.class), buffer.readVarInt()));

    @Override public Type<GasTurbineControlPayload> type() { return TYPE; }

    public static void handle(GasTurbineControlPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof GasTurbineMenu menu
                    && menu.blockEntity() != null) menu.blockEntity().setControl(payload.control, payload.value);
        });
    }
}
