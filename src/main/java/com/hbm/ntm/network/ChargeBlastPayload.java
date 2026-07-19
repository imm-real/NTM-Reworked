package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ChargeBlastPayload(double x, double y, double z, boolean large) implements CustomPacketPayload {
    public static final Type<ChargeBlastPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "charge_blast")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ChargeBlastPayload> STREAM_CODEC =
            StreamCodec.ofMember(ChargeBlastPayload::encode, ChargeBlastPayload::decode);

    @Override
    public Type<ChargeBlastPayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeBoolean(large);
    }

    private static ChargeBlastPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ChargeBlastPayload(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readBoolean());
    }

    public static void handle(ChargeBlastPayload payload, IPayloadContext context) {
        Player viewer = context.player();
        double distance = Math.sqrt(viewer.distanceToSqr(payload.x, payload.y, payload.z));
        float range = payload.large ? 150.0F : 200.0F;
        var sound = payload.large
                ? (distance <= range * 0.4F ? ModSounds.EXPLOSION_LARGE_NEAR.get() : ModSounds.EXPLOSION_LARGE_FAR.get())
                : (distance <= range * 0.4F ? ModSounds.EXPLOSION_SMALL_NEAR.get() : ModSounds.EXPLOSION_SMALL_FAR.get());
        viewer.level().playLocalSound(payload.x, payload.y, payload.z, sound, SoundSource.BLOCKS,
                payload.large ? 16.0F : 8.0F, 0.9F + viewer.level().random.nextFloat() * 0.2F, false);

        viewer.level().addParticle(ParticleTypes.EXPLOSION_EMITTER, payload.x, payload.y, payload.z,
                1.0D, 0.0D, 0.0D);
        int clouds = payload.large ? 10 : 15;
        double speed = payload.large ? 0.25D : 0.5D;
        for (int i = 0; i < clouds; i++) {
            viewer.level().addParticle(
                    ParticleTypes.POOF,
                    payload.x,
                    payload.y,
                    payload.z,
                    viewer.level().random.nextGaussian() * speed,
                    viewer.level().random.nextDouble() * speed * 2.0D,
                    viewer.level().random.nextGaussian() * speed
            );
        }
    }
}
