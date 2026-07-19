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

/** Sends the small boom, the distant boom and a cloud of temporary vanilla poofs. */
public record MineBlastPayload(double x, double y, double z, int cloudCount, float cloudSpeed)
        implements CustomPacketPayload {
    public static final Type<MineBlastPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "mine_blast")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MineBlastPayload> STREAM_CODEC =
            StreamCodec.ofMember(MineBlastPayload::encode, MineBlastPayload::decode);

    @Override
    public Type<MineBlastPayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeVarInt(cloudCount);
        buffer.writeFloat(cloudSpeed);
    }

    private static MineBlastPayload decode(RegistryFriendlyByteBuf buffer) {
        return new MineBlastPayload(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readVarInt(), buffer.readFloat());
    }

    public static void handle(MineBlastPayload payload, IPayloadContext context) {
        Player viewer = context.player();
        double distance = Math.sqrt(viewer.distanceToSqr(payload.x, payload.y, payload.z));
        // Source ExplosionSmallCreator: 200-block range, near variant within 0.4 of it.
        var sound = distance <= 200.0D * 0.4D
                ? ModSounds.EXPLOSION_SMALL_NEAR.get() : ModSounds.EXPLOSION_SMALL_FAR.get();
        viewer.level().playLocalSound(payload.x, payload.y, payload.z, sound, SoundSource.BLOCKS,
                8.0F, 0.9F + viewer.level().random.nextFloat() * 0.2F, false);

        viewer.level().addParticle(ParticleTypes.EXPLOSION_EMITTER, payload.x, payload.y, payload.z,
                1.0D, 0.0D, 0.0D);
        double speed = payload.cloudSpeed * 0.5D;
        for (int i = 0; i < payload.cloudCount; i++) {
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
