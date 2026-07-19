package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.SteamEngineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SteamEngineStatePayload(BlockPos position, long power, float rotor,
                                      int steam, int spentSteam) implements CustomPacketPayload {
    public static final Type<SteamEngineStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "steam_engine_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SteamEngineStatePayload> STREAM_CODEC =
            StreamCodec.ofMember(SteamEngineStatePayload::encode, SteamEngineStatePayload::decode);

    @Override public Type<SteamEngineStatePayload> type() { return TYPE; }

    private void encode(RegistryFriendlyByteBuf buffer) {
        BlockPos.STREAM_CODEC.encode(buffer, position);
        buffer.writeLong(power);
        buffer.writeFloat(rotor);
        buffer.writeVarInt(steam);
        buffer.writeVarInt(spentSteam);
    }

    private static SteamEngineStatePayload decode(RegistryFriendlyByteBuf buffer) {
        return new SteamEngineStatePayload(BlockPos.STREAM_CODEC.decode(buffer), buffer.readLong(),
                buffer.readFloat(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(SteamEngineStatePayload payload, IPayloadContext context) {
        if (context.player().level().getBlockEntity(payload.position()) instanceof SteamEngineBlockEntity engine) {
            engine.applyClientSnapshot(payload.power(), payload.rotor(), payload.steam(), payload.spentSteam());
        }
    }
}
