package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.PumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PumpStatePayload(BlockPos position, boolean isOn, boolean onGround, int water,
                               int steam, int spentSteam, long power) implements CustomPacketPayload {
    public static final Type<PumpStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "pump_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PumpStatePayload> STREAM_CODEC =
            StreamCodec.ofMember(PumpStatePayload::encode, PumpStatePayload::decode);

    @Override public Type<PumpStatePayload> type() { return TYPE; }

    private void encode(RegistryFriendlyByteBuf buffer) {
        BlockPos.STREAM_CODEC.encode(buffer, position);
        buffer.writeBoolean(isOn);
        buffer.writeBoolean(onGround);
        buffer.writeVarInt(water);
        buffer.writeVarInt(steam);
        buffer.writeVarInt(spentSteam);
        buffer.writeVarLong(power);
    }

    private static PumpStatePayload decode(RegistryFriendlyByteBuf buffer) {
        return new PumpStatePayload(BlockPos.STREAM_CODEC.decode(buffer), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarLong());
    }

    public static void handle(PumpStatePayload payload, IPayloadContext context) {
        if (context.player().level().getBlockEntity(payload.position()) instanceof PumpBlockEntity pump) {
            pump.applyClientSnapshot(payload.isOn(), payload.onGround(), payload.water(), payload.steam(),
                    payload.spentSteam(), payload.power());
        }
    }
}
