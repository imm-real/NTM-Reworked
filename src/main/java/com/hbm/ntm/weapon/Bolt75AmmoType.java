package com.hbm.ntm.weapon;

public enum Bolt75AmmoType implements SednaAmmoType {
    STANDARD("b75", 38, 1.0F, 0.0F),
    INCENDIARY("b75_inc", 39, 0.8F, 0.1F),
    EXPLOSIVE("b75_exp", 40, 1.5F, -0.25F);

    private final String serializedName;
    private final int legacyMetadata;
    private final float damageMultiplier;
    private final float armorPiercing;

    Bolt75AmmoType(String serializedName, int legacyMetadata, float damageMultiplier,
                   float armorPiercing) {
        this.serializedName = serializedName;
        this.legacyMetadata = legacyMetadata;
        this.damageMultiplier = damageMultiplier;
        this.armorPiercing = armorPiercing;
    }

    @Override public String serializedName() { return serializedName; }
    @Override public int legacyMetadata() { return legacyMetadata; }
    @Override public int legacyBulletConfig() { return legacyMetadata; }
    @Override public float damageMultiplier() { return damageMultiplier; }
    @Override public float spread() { return 0.0F; }
    @Override public int projectiles() { return 1; }
    @Override public float ricochetAngle() { return 5.0F; }
    @Override public int maxRicochets() { return 2; }
    @Override public boolean penetrates() { return false; }
    @Override public boolean penetrationDamageFalloff() { return true; }
    @Override public float armorPiercing() { return armorPiercing; }
    @Override public float impactExplosionRadius() {
        return switch (this) {
            case STANDARD -> 2.0F;
            case EXPLOSIVE -> 5.0F;
            case INCENDIARY -> 0.0F;
        };
    }
    @Override public boolean explodesBeforeDirectHit() { return this != INCENDIARY; }
    @Override public double impactExplosionRange() { return impactExplosionRadius(); }
    @Override public boolean tinyImpactExplosion() { return this == STANDARD; }
    @Override public int phosphorusTicks() { return this == INCENDIARY ? 300 : 0; }
    @Override public boolean phosphorusOnImpact() { return this == INCENDIARY; }

    @Override
    public int tracerDarkColor() {
        return this == EXPLOSIVE ? 0xFF9E082E : 0xFFFF6A00;
    }

    @Override
    public int tracerLightColor() {
        return this == EXPLOSIVE ? 0xFFFF8A79 : 0xFFFFE28D;
    }

    public static Bolt75AmmoType fromLegacyMetadata(int metadata) {
        return switch (metadata) {
            case 39 -> INCENDIARY;
            case 40 -> EXPLOSIVE;
            default -> STANDARD;
        };
    }
}
