package com.hbm.ntm.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

import java.awt.Color;

public final class BlackPowderSmokeParticle extends TextureSheetParticle {
    private final float baseScale;
    private final float hue;

    private BlackPowderSmokeParticle(ClientLevel level, double x, double y, double z,
                                     double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        xd = xSpeed;
        yd = ySpeed;
        zd = zSpeed;
        lifetime = 30 + random.nextInt(15);
        baseScale = 0.25F * 0.9F + random.nextFloat() * 0.2F;
        hue = 20.0F + random.nextFloat() * 20.0F;
        hasPhysics = false;
        pickSprite(sprites);
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
        oRoll = roll;
        float scaledAge = (float) age / lifetime;
        roll += (1.0F - scaledAge) * (getParticleGroup().hashCode() % 2 == 0 ? 1.0F : -1.0F);
        xd *= 0.65D;
        yd *= 0.65D;
        zd *= 0.65D;
        move(xd, yd, zd);
    }

    @Override
    public float getQuadSize(float partialTick) {
        float scaledAge = (age + partialTick) / lifetime;
        Color color = Color.getHSBColor(hue / 255.0F, Math.max(1.0F - scaledAge * 4.0F, 0.0F),
                Mth.clamp(1.25F - scaledAge * 2.0F, 0.7F, 1.0F));
        rCol = color.getRed() / 255.0F;
        gCol = color.getGreen() / 255.0F;
        bCol = color.getBlue() / 255.0F;
        alpha = (float) Math.pow(1.0F - Math.min(scaledAge, 1.0F), 0.25D) * 0.25F;
        return (0.25F + scaledAge + (age + partialTick) * 0.025F) * baseScale;
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
        public net.minecraft.client.particle.Particle createParticle(SimpleParticleType type, ClientLevel level,
                                                                      double x, double y, double z,
                                                                      double xSpeed, double ySpeed, double zSpeed) {
            return new BlackPowderSmokeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
