package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.item.SednaGunItem;
import com.hbm.ntm.weapon.GunInput;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record GunInputPayload(GunInput input) implements CustomPacketPayload {
    public static final Type<GunInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "gun_input")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, GunInputPayload> STREAM_CODEC =
            StreamCodec.ofMember(GunInputPayload::encode, GunInputPayload::decode);

    @Override
    public Type<GunInputPayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeByte(input.ordinal());
    }

    private static GunInputPayload decode(RegistryFriendlyByteBuf buffer) {
        return new GunInputPayload(GunInput.byId(buffer.readUnsignedByte()));
    }

    public static void handle(GunInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SednaGunItem.handleInput(player, payload.input);
            }
        });
    }
}
