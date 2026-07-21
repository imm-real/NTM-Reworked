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

/** Flamethrower puff with a brief and violently orange career. */
public final class FlamethrowerParticle extends TextureSheetParticle {
    private final float baseScale = 0.5F;
    private final float baseRed;
    private final float baseGreen;
    private final float baseBlue;
    private final float rollDirection;
    private final boolean black;

    private FlamethrowerParticle(ClientLevel level, double x, double y, double z,
                                 double xSpeed, double ySpeed, double zSpeed,
                                 SpriteSet sprites, boolean balefire, boolean black) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        xd = xSpeed + random.nextGaussian() * 0.02D;
        yd = ySpeed;
        zd = zSpeed + random.nextGaussian() * 0.02D;
        lifetime = 20 + random.nextInt(10);
        if (black) {
            baseRed = 1.0F;
            baseGreen = 1.0F;
            baseBlue = 1.0F;
        } else {
            float hue = (balefire ? 65.0F : 15.0F)
                    + random.nextFloat() * (balefire ? 35.0F : 25.0F);
            Color color = Color.getHSBColor(hue / 255.0F, 1.0F, 1.0F);
            baseRed = color.getRed() / 255.0F;
            baseGreen = color.getGreen() / 255.0F;
            baseBlue = color.getBlue() / 255.0F;
        }
        rollDirection = random.nextBoolean() ? 15.0F : -15.0F;
        this.black = black;
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
        xd *= 0.91D;
        yd = yd * 0.91D + 0.01D;
        zd *= 0.91D;
        roll += rollDirection * Mth.DEG_TO_RAD;
        move(xd, yd, zd);
    }

    @Override
    public float getQuadSize(float partialTick) {
        float scaledAge = Mth.clamp((age + partialTick) / lifetime, 0.0F, 1.0F);
        if (black) {
            float add = scaledAge * 2.0F - 0.25F;
            rCol = Mth.clamp(baseRed - add * 0.75F, 0.0F, 1.0F);
            gCol = Mth.clamp(baseGreen - add, 0.0F, 1.0F);
            bCol = Mth.clamp(baseBlue - add * 0.5F, 0.0F, 1.0F);
            alpha = 1.0F - scaledAge;
            return (scaledAge * 1.25F + 0.25F) * baseScale;
        }
        float add = 0.75F - scaledAge;
        rCol = Mth.clamp(baseRed + add, 0.0F, 1.0F);
        gCol = Mth.clamp(baseGreen + add, 0.0F, 1.0F);
        bCol = Mth.clamp(baseBlue + add, 0.0F, 1.0F);
        alpha = (float) Math.sqrt(1.0F - scaledAge) * 0.5F;
        return (scaledAge * 1.25F + 0.25F) * baseScale;
    }

    @Override public int getLightColor(float partialTick) { return 15_728_880; }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final boolean balefire;

        public Provider(SpriteSet sprites, boolean balefire) {
            this(sprites, balefire, false);
        }

        public Provider(SpriteSet sprites, boolean balefire, boolean black) {
            this.sprites = sprites;
            this.balefire = balefire;
            this.black = black;
        }

        private final boolean black;

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new FlamethrowerParticle(level, x, y, z,
                    xSpeed, ySpeed, zSpeed, sprites, balefire, black);
        }
    }
}
