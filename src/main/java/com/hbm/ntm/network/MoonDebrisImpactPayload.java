package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.world.MoonDebrisCraters;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-validated terrain impact reported by the player-local moon-debris renderer. */
public record MoonDebrisImpactPayload(int x, int z, int sizeTenths) implements CustomPacketPayload {
    public static final Type<MoonDebrisImpactPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "moon_debris_impact"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MoonDebrisImpactPayload> STREAM_CODEC =
            StreamCodec.ofMember(MoonDebrisImpactPayload::encode, MoonDebrisImpactPayload::decode);

    public static MoonDebrisImpactPayload at(int x, int z, float size) {
        return new MoonDebrisImpactPayload(x, z, Mth.clamp(Math.round(size * 10.0F), 8, 80));
    }

    @Override
    public Type<MoonDebrisImpactPayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(x);
        buffer.writeInt(z);
        buffer.writeByte(sizeTenths);
    }

    private static MoonDebrisImpactPayload decode(RegistryFriendlyByteBuf buffer) {
        return new MoonDebrisImpactPayload(buffer.readInt(), buffer.readInt(), buffer.readUnsignedByte());
    }

    public static void handle(MoonDebrisImpactPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MoonDebrisCraters.tryCreate(player, payload.x, payload.z, payload.sizeTenths);
            }
        });
    }
}
