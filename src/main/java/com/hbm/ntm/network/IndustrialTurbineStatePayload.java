package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.IndustrialTurbineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record IndustrialTurbineStatePayload(BlockPos position, long power, long flywheel,
                                            double spin, int grade, int input, int output,
                                            boolean operational) implements CustomPacketPayload {
    public static final Type<IndustrialTurbineStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "industrial_turbine_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, IndustrialTurbineStatePayload> STREAM_CODEC =
            StreamCodec.ofMember(IndustrialTurbineStatePayload::encode, IndustrialTurbineStatePayload::decode);

    @Override public Type<IndustrialTurbineStatePayload> type() { return TYPE; }

    private void encode(RegistryFriendlyByteBuf buffer) {
        BlockPos.STREAM_CODEC.encode(buffer, position);
        buffer.writeLong(power);
        buffer.writeLong(flywheel);
        buffer.writeDouble(spin);
        buffer.writeByte(grade);
        buffer.writeVarInt(input);
        buffer.writeVarInt(output);
        buffer.writeBoolean(operational);
    }

    private static IndustrialTurbineStatePayload decode(RegistryFriendlyByteBuf buffer) {
        return new IndustrialTurbineStatePayload(BlockPos.STREAM_CODEC.decode(buffer), buffer.readLong(),
                buffer.readLong(), buffer.readDouble(), buffer.readUnsignedByte(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readBoolean());
    }

    public static void handle(IndustrialTurbineStatePayload payload, IPayloadContext context) {
        if (context.player().level().getBlockEntity(payload.position())
                instanceof IndustrialTurbineBlockEntity turbine) {
            turbine.applyClientSnapshot(payload.power(), payload.flywheel(), payload.spin(), payload.grade(),
                    payload.input(), payload.output(), payload.operational());
        }
    }
}
