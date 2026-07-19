package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.FluidIdentifierMenu;
import com.hbm.ntm.item.FluidIdentifierItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FluidIdentifierSelectPayload(FluidIdentifierItem.Selection selection, boolean primary)
        implements CustomPacketPayload {
    public static final Type<FluidIdentifierSelectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "fluid_identifier_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FluidIdentifierSelectPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                buffer.writeEnum(payload.selection);
                buffer.writeBoolean(payload.primary);
            }, buffer -> new FluidIdentifierSelectPayload(
                    buffer.readEnum(FluidIdentifierItem.Selection.class), buffer.readBoolean()));

    @Override public Type<FluidIdentifierSelectPayload> type() { return TYPE; }

    public static void handle(FluidIdentifierSelectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FluidIdentifierMenu menu
                    && menu.setSelection(payload.selection, payload.primary)) {
                context.player().getInventory().setChanged();
            }
        });
    }
}
