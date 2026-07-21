package com.hbm.ntm.weapon;

public enum Shotgun10GaugeAmmoType implements SednaAmmoType {
    BUCKSHOT("g10", 78, 1.0F / 10.0F, 0.035F, 10, 15.0F, 2,
            5.0F, 0.0F, false, true, 1.0F, 0.0F),
    SHRAPNEL("g10_shrapnel", 79, 1.0F / 10.0F, 0.035F, 10, 90.0F, 15,
            5.0F, 0.0F, false, true, 1.0F, 0.0F),
    DEPLETED_URANIUM("g10_du", 80, 1.0F / 4.0F, 0.035F, 10, 15.0F, 2,
            10.0F, 0.2F, true, false, 1.0F, 0.0F),
    SLUG("g10_slug", 81, 1.0F, 0.0F, 1, 15.0F, 2,
            10.0F, 0.1F, true, true, 1.0F, 0.0F),
    EXPLOSIVE("g10_explosive", 84, 1.0F / 4.0F, 0.035F, 10, 5.0F, 2,
            0.0F, 0.0F, false, true, 3.0F, 1.5F);

    private final String serializedName;
    private final int legacyMetadata;
    private final float damageMultiplier;
    private final float spread;
    private final int projectiles;
    private final float ricochetAngle;
    private final int maxRicochets;
    private final float thresholdNegation;
    private final float armorPiercing;
    private final boolean penetrates;
    private final boolean penetrationDamageFalloff;
    private final float wear;
    private final float explosionRadius;

    Shotgun10GaugeAmmoType(String serializedName, int legacyMetadata, float damageMultiplier,
                           float spread, int projectiles, float ricochetAngle, int maxRicochets,
                           float thresholdNegation, float armorPiercing, boolean penetrates,
                           boolean penetrationDamageFalloff, float wear, float explosionRadius) {
        this.serializedName = serializedName;
        this.legacyMetadata = legacyMetadata;
        this.damageMultiplier = damageMultiplier;
        this.spread = spread;
        this.projectiles = projectiles;
        this.ricochetAngle = ricochetAngle;
        this.maxRicochets = maxRicochets;
        this.thresholdNegation = thresholdNegation;
        this.armorPiercing = armorPiercing;
        this.penetrates = penetrates;
        this.penetrationDamageFalloff = penetrationDamageFalloff;
        this.wear = wear;
        this.explosionRadius = explosionRadius;
    }

    @Override public String serializedName() { return serializedName; }
    @Override public int legacyMetadata() { return legacyMetadata; }
    @Override public int legacyBulletConfig() { return legacyMetadata; }
    @Override public float damageMultiplier() { return damageMultiplier; }
    @Override public float spread() { return spread; }
    @Override public int projectiles() { return projectiles; }
    @Override public float ricochetAngle() { return ricochetAngle; }
    @Override public int maxRicochets() { return maxRicochets; }
    @Override public boolean penetrates() { return penetrates; }
    @Override public boolean penetrationDamageFalloff() { return penetrationDamageFalloff; }
    @Override public float armorThresholdNegation() { return thresholdNegation; }
    @Override public float armorPiercing() { return armorPiercing; }
    @Override public float wear() { return wear; }
    @Override public float impactExplosionRadius() { return explosionRadius; }
    @Override public double impactExplosionRange() { return explosionRadius; }
    @Override public boolean explodesBeforeDirectHit() { return this == EXPLOSIVE; }
    @Override public boolean tinyImpactExplosion() { return this == EXPLOSIVE; }

    @Override
    public int tracerDarkColor() {
        return switch (this) {
            case DEPLETED_URANIUM -> 0xFF5CCD41;
            case EXPLOSIVE -> 0xFFD8CA00;
            default -> SednaAmmoType.super.tracerDarkColor();
        };
    }

    @Override
    public int tracerLightColor() {
        return switch (this) {
            case DEPLETED_URANIUM -> 0xFFE9FF8D;
            case EXPLOSIVE -> 0xFFFFF19D;
            default -> SednaAmmoType.super.tracerLightColor();
        };
    }

    @Override public boolean tracerFullbright() { return this == EXPLOSIVE; }

    public static Shotgun10GaugeAmmoType fromLegacyMetadata(int metadata) {
        for (Shotgun10GaugeAmmoType type : values()) {
            if (type.legacyMetadata == metadata) return type;
        }
        return BUCKSHOT;
    }
}
