package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.SolderingStationMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SolderingCollisionPayload() implements CustomPacketPayload {
    public static final Type<SolderingCollisionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "soldering_collision"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SolderingCollisionPayload> STREAM_CODEC =
            StreamCodec.unit(new SolderingCollisionPayload());
    @Override public Type<SolderingCollisionPayload> type() { return TYPE; }
    public static void handle(SolderingCollisionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof SolderingStationMenu menu
                    && menu.blockEntity() != null) menu.blockEntity().toggleCollisionPrevention();
        });
    }
}
