package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.DfcEmitterBlockEntity;
import com.hbm.ntm.blockentity.DfcStabilizerBlockEntity;
import com.hbm.ntm.inventory.DfcMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DfcControlPayload(int watts, boolean toggle) implements CustomPacketPayload {
    public static final Type<DfcControlPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "dfc_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DfcControlPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.watts);
                buffer.writeBoolean(payload.toggle);
            }, buffer -> new DfcControlPayload(buffer.readVarInt(), buffer.readBoolean()));

    @Override public Type<DfcControlPayload> type() { return TYPE; }

    public static void handle(DfcControlPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().containerMenu instanceof DfcMenu menu) || menu.blockEntity() == null) return;
            if (menu.blockEntity() instanceof DfcEmitterBlockEntity emitter) {
                emitter.setControl(payload.watts, payload.toggle);
            } else if (!payload.toggle() && menu.blockEntity() instanceof DfcStabilizerBlockEntity stabilizer) {
                stabilizer.setControl(payload.watts);
            }
        });
    }
}
