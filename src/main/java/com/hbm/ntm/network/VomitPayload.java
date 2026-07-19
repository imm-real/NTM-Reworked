package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.registry.ModParticles;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.IntUnaryOperator;

public record VomitPayload(int entityId, boolean blood, int count) implements CustomPacketPayload {
    private static IntUnaryOperator clientParticleLimiter = count -> count;
    public static final Type<VomitPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "vomit")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, VomitPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            VomitPayload::entityId,
            ByteBufCodecs.BOOL,
            VomitPayload::blood,
            ByteBufCodecs.VAR_INT,
            VomitPayload::count,
            VomitPayload::new
    );

    @Override
    public Type<VomitPayload> type() {
        return TYPE;
    }

    public static void handle(VomitPayload payload, IPayloadContext context) {
        Player viewer = context.player();
        if (!(viewer.level().getEntity(payload.entityId) instanceof LivingEntity entity)) {
            return;
        }

        int particleCount = Mth.clamp(clientParticleLimiter.applyAsInt(payload.count), 0, 25);
        double x = entity.getX();
        double y = entity.getEyeY();
        double z = entity.getZ();
        var look = entity.getLookAngle();
        var particle = payload.blood ? ModParticles.BLOOD_VOMIT.get() : ModParticles.VOMIT.get();

        for (int i = 0; i < particleCount; i++) {
            double xSpeed = (look.x + viewer.level().random.nextGaussian() * 0.2D) * 0.2D;
            double ySpeed = (look.y + viewer.level().random.nextGaussian() * 0.2D) * 0.2D;
            double zSpeed = (look.z + viewer.level().random.nextGaussian() * 0.2D) * 0.2D;
            // The limiter already ran. Force creation so MINIMAL does not eat all eight particles.
            viewer.level().addParticle(particle, true, x, y, z, xSpeed, ySpeed, zSpeed);
        }
    }

    /** Installed by client bootstrap so this common payload remains dedicated-server safe. */
    public static void installClientParticleLimiter(IntUnaryOperator limiter) {
        clientParticleLimiter = limiter;
    }
}
