package com.hbm.ntm.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public final class BlackPowderSparkParticle extends TextureSheetParticle {
    private BlackPowderSparkParticle(ClientLevel level, double x, double y, double z,
                                     double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        xd = xSpeed;
        yd = ySpeed;
        zd = zSpeed;
        float color = random.nextFloat() * 0.1F + 0.2F;
        rCol = color + 0.7F;
        gCol = color + 0.5F;
        bCol = color;
        quadSize *= random.nextFloat() * 0.6F + 0.5F;
        lifetime = 16 + random.nextInt(5);
        gravity = 0.01F;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        yd -= gravity;
        move(xd, yd, zd);
        xd *= 0.95D;
        yd *= 0.95D;
        zd *= 0.95D;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 15728880;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public net.minecraft.client.particle.Particle createParticle(SimpleParticleType type, ClientLevel level,
                                                                      double x, double y, double z,
                                                                      double xSpeed, double ySpeed, double zSpeed) {
            return new BlackPowderSparkParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
