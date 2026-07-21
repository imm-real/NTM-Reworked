package com.hbm.ntm.weapon;

public enum AberratorAmmoType implements SednaAmmoType {
    V9("p35_800", 5, false),
    BLACK_LIGHTNING("p35_800_bl", 7, true);

    private final String serializedName;
    private final int legacyMetadata;
    private final boolean blackLightning;

    AberratorAmmoType(String serializedName, int legacyMetadata, boolean blackLightning) {
        this.serializedName = serializedName;
        this.legacyMetadata = legacyMetadata;
        this.blackLightning = blackLightning;
    }

    @Override public String serializedName() { return serializedName; }
    @Override public int legacyMetadata() { return legacyMetadata; }
    @Override public int legacyBulletConfig() { return legacyMetadata; }
    @Override public float damageMultiplier() { return 1.0F; }
    @Override public float spread() { return 0.0F; }
    @Override public int projectiles() { return 1; }
    @Override public float ricochetAngle() { return 5.0F; }
    @Override public int maxRicochets() { return 2; }
    @Override public boolean penetrates() { return false; }
    @Override public boolean penetrationDamageFalloff() { return true; }
    @Override public float armorThresholdNegation() { return 50.0F; }
    @Override public float armorPiercing() { return 0.5F; }
    @Override public float wear() { return 0.0F; }

    public boolean blackLightning() { return blackLightning; }

    public static AberratorAmmoType fromLegacyMetadata(int metadata) {
        return metadata == BLACK_LIGHTNING.legacyMetadata ? BLACK_LIGHTNING : V9;
    }
}
