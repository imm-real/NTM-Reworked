package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.SteamCondenserBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SteamCondenserStatePayload(BlockPos position, int spentSteam, int water,
                                         int waterTimer) implements CustomPacketPayload {
    public static final Type<SteamCondenserStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "steam_condenser_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SteamCondenserStatePayload> STREAM_CODEC =
            StreamCodec.ofMember(SteamCondenserStatePayload::encode, SteamCondenserStatePayload::decode);

    @Override public Type<SteamCondenserStatePayload> type() { return TYPE; }

    private void encode(RegistryFriendlyByteBuf buffer) {
        BlockPos.STREAM_CODEC.encode(buffer, position);
        buffer.writeVarInt(spentSteam);
        buffer.writeVarInt(water);
        buffer.writeByte(waterTimer);
    }

    private static SteamCondenserStatePayload decode(RegistryFriendlyByteBuf buffer) {
        return new SteamCondenserStatePayload(BlockPos.STREAM_CODEC.decode(buffer), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readUnsignedByte());
    }

    public static void handle(SteamCondenserStatePayload payload, IPayloadContext context) {
        if (context.player().level().getBlockEntity(payload.position()) instanceof SteamCondenserBlockEntity condenser) {
            condenser.applyClientSnapshot(payload.spentSteam(), payload.water(), payload.waterTimer());
        }
    }
}
