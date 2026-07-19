package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.SawmillBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SawmillStatePayload(BlockPos position, int heat, int progress, boolean hasBlade,
                                  ItemStack input, ItemStack output, ItemStack byproduct)
        implements CustomPacketPayload {
    public static final Type<SawmillStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "sawmill_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SawmillStatePayload> STREAM_CODEC =
            StreamCodec.ofMember(SawmillStatePayload::encode, SawmillStatePayload::decode);

    @Override public Type<SawmillStatePayload> type() { return TYPE; }

    private void encode(RegistryFriendlyByteBuf buffer) {
        BlockPos.STREAM_CODEC.encode(buffer, position);
        buffer.writeInt(heat);
        buffer.writeInt(progress);
        buffer.writeBoolean(hasBlade);
        writeStack(buffer, input);
        writeStack(buffer, output);
        writeStack(buffer, byproduct);
    }

    private static SawmillStatePayload decode(RegistryFriendlyByteBuf buffer) {
        return new SawmillStatePayload(BlockPos.STREAM_CODEC.decode(buffer), buffer.readInt(),
                buffer.readInt(), buffer.readBoolean(), readStack(buffer), readStack(buffer), readStack(buffer));
    }

    private static void writeStack(RegistryFriendlyByteBuf buffer, ItemStack stack) {
        buffer.writeBoolean(!stack.isEmpty());
        if (!stack.isEmpty()) ItemStack.STREAM_CODEC.encode(buffer, stack);
    }

    private static ItemStack readStack(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? ItemStack.STREAM_CODEC.decode(buffer) : ItemStack.EMPTY;
    }

    public static void handle(SawmillStatePayload payload, IPayloadContext context) {
        if (context.player().level().getBlockEntity(payload.position()) instanceof SawmillBlockEntity sawmill) {
            sawmill.applyClientSnapshot(payload.heat(), payload.progress(), payload.hasBlade(),
                    payload.input(), payload.output(), payload.byproduct());
        }
    }
}
