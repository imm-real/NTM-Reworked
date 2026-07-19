package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.registry.ModParticles;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record GibletPayload(int entityId) implements CustomPacketPayload {
    public static final Type<GibletPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "giblets")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, GibletPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            GibletPayload::entityId,
            GibletPayload::new
    );

    @Override
    public Type<GibletPayload> type() {
        return TYPE;
    }

    public static void handle(GibletPayload payload, IPayloadContext context) {
        Player viewer = context.player();
        Entity entity = viewer.level().getEntity(payload.entityId);
        if (entity == null) {
            return;
        }

        int widthSegments = (int) (entity.getBbWidth() / 0.25F);
        int heightSegments = (int) (entity.getBbHeight() / 0.25F);
        int count = (int) Math.ceil(widthSegments * 1.5D * heightSegments / 5.0D);
        double velocityMultiplier = viewer.level().random.nextInt(15) == 0 ? 10.0D : 1.0D;
        double x = entity.getX();
        double y = entity.getY() + entity.getBbHeight() * 0.5D;
        double z = entity.getZ();

        entity.setInvisible(true);
        for (int i = 0; i < count; i++) {
            viewer.level().addParticle(
                    ModParticles.GIBLET.get(),
                    x,
                    y,
                    z,
                    viewer.level().random.nextGaussian() * 0.25D * velocityMultiplier,
                    viewer.level().random.nextDouble() * velocityMultiplier,
                    viewer.level().random.nextGaussian() * 0.25D * velocityMultiplier
            );
        }
    }
}
