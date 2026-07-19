package com.hbm.ntm.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Stationary green warning visible through ash goggles. */
public final class ChlorineCloudParticle extends TextureSheetParticle {
    private static final float SOURCE_SCALE_FIELD = 7.5F;
    private final SpriteSet sprites;

    private ChlorineCloudParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.sprites = sprites;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.hasPhysics = false;
        this.lifetime = (int) (8.0D / (random.nextDouble() * 0.8D + 0.3D) * 2.5D);

        float tint = random.nextFloat() * 0.1F;
        setColor(0.7F + tint, 0.8F + tint, 0.6F + tint);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (++age >= lifetime) {
            remove();
            return;
        }
        setSpriteFromAge(sprites);
    }

    @Override
    public float getQuadSize(float partialTick) {
        float growth = Mth.clamp((age + partialTick) / lifetime * 32.0F, 0.0F, 1.0F);
        // EntityFX rendered particleScale at one tenth world size in 1.7.10.
        return SOURCE_SCALE_FIELD * 0.1F * growth;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new ChlorineCloudParticle(level, x, y, z, sprites);
        }
    }
}
