package com.hbm.ntm.weapon;

public enum FollyAmmoType implements SednaAmmoType {
    SILVER_BULLET("folly_sm", 0),
    NUCLEAR_SILVER_BULLET("folly_nuke", 1);

    private final String serializedName;
    private final int legacyMetadata;

    FollyAmmoType(String serializedName, int legacyMetadata) {
        this.serializedName = serializedName;
        this.legacyMetadata = legacyMetadata;
    }

    @Override public String serializedName() { return serializedName; }
    @Override public int legacyMetadata() { return legacyMetadata; }
    @Override public int legacyBulletConfig() { return legacyMetadata; }
    @Override public float damageMultiplier() { return 1.0F; }
    @Override public float spread() { return 0.0F; }
    @Override public int projectiles() { return 1; }
    @Override public float ricochetAngle() { return 5.0F; }
    @Override public int maxRicochets() { return 2; }
    @Override public boolean penetrates() { return this == SILVER_BULLET; }
    @Override public boolean penetrationDamageFalloff() { return false; }
    @Override public double projectileSpeed() { return this == NUCLEAR_SILVER_BULLET ? 4.0D : 2.0D; }
    @Override public int projectileLifetime() { return this == NUCLEAR_SILVER_BULLET ? 600 : 100; }
    @Override public boolean spectral() { return this == SILVER_BULLET; }

    public static FollyAmmoType fromLegacyMetadata(int metadata) {
        return metadata == NUCLEAR_SILVER_BULLET.legacyMetadata
                ? NUCLEAR_SILVER_BULLET : SILVER_BULLET;
    }
}
