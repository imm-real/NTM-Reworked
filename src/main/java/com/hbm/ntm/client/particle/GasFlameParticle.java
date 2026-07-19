package com.hbm.ntm.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

import java.awt.Color;

/** Full-bright turbofan flame fading from yellow panic to red panic. */
public final class GasFlameParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float sourceScale;
    private float colorMod = 1.0F;

    private GasFlameParticle(ClientLevel level, double x, double y, double z,
                             double xSpeed, double ySpeed, double zSpeed,
                             SpriteSet sprites, float sourceScale) {
        // EntitySmokeFX seeded a tiny random drift with zero input, damped it to 10%,
        // then added the supplied gas velocity (with the vertical component at 1.5x).
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.sprites = sprites;
        this.sourceScale = sourceScale;
        this.xd = this.xd * 0.1D + xSpeed;
        this.yd = this.yd * 0.1D + ySpeed * 1.5D;
        this.zd = this.zd * 0.1D + zSpeed;
        this.hasPhysics = false;
        // The old constructor colored its first frame before choosing this random dimmer.
        updateColor();
        this.colorMod = 0.8F + random.nextFloat() * 0.2F;
        this.lifetime = 30 + random.nextInt(13);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        // EntitySmokeFX used post-increment semantics, so age == lifetime still receives
        // its final movement/color update before the following tick removes it.
        if (age++ >= lifetime) {
            remove();
            return;
        }

        // EntitySmokeFX first moved, damped its horizontal speed by 0.96, then
        // ParticleGasFlame applied another 0.75 and restored the old vertical speed.
        double previousYVelocity = yd;
        move(xd, yd + 0.004D, zd);
        xd *= 0.72D;
        yd = previousYVelocity + 0.005D;
        zd *= 0.72D;
        setSpriteFromAge(sprites);
        updateColor();
    }

    private void updateColor() {
        float time = lifetime <= 0 ? 0.0F : (float) age / (float) lifetime;
        Color color = Color.getHSBColor(Math.max((60.0F - time * 100.0F) / 360.0F, 0.0F),
                1.0F - time * 0.25F, 1.0F - time * 0.5F);
        rCol = color.getRed() / 255.0F * colorMod;
        gCol = color.getGreen() / 255.0F * colorMod;
        bCol = color.getBlue() / 255.0F * colorMod;
    }

    @Override
    public float getQuadSize(float partialTick) {
        // EntityFX's quad vertices applied particleScale at one tenth world scale.
        float growth = Mth.clamp((age + partialTick) / lifetime * 32.0F, 0.0F, 1.0F);
        return sourceScale * 0.1F * growth;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0x00F000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final float sourceScale;

        public Provider(SpriteSet sprites, float sourceScale) {
            this.sprites = sprites;
            this.sourceScale = sourceScale;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new GasFlameParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, sourceScale);
        }
    }
}
