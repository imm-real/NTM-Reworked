package com.hbm.ntm.weapon;

public enum ChargeThrowerAmmoType implements SednaAmmoType {
    HOOK("ct_hook", 90, Kind.HOOK, 0.0F, 6_000),
    MORTAR("ct_mortar", 91, Kind.MORTAR, 2.5F, 200),
    CHARGED_MORTAR("ct_mortar_charge", 92, Kind.CHARGED_MORTAR, 5.0F, 200);

    public static final double SPEED = 3.0D;
    public static final float GRAVITY = 0.035F;

    private final String serializedName;
    private final int legacyMetadata;
    private final Kind kind;
    private final float damage;
    private final int lifetime;

    ChargeThrowerAmmoType(String serializedName, int legacyMetadata, Kind kind,
                          float damage, int lifetime) {
        this.serializedName = serializedName;
        this.legacyMetadata = legacyMetadata;
        this.kind = kind;
        this.damage = damage;
        this.lifetime = lifetime;
    }

    @Override public String serializedName() { return serializedName; }
    @Override public int legacyMetadata() { return legacyMetadata; }
    @Override public int legacyBulletConfig() { return legacyMetadata; }
    @Override public float damageMultiplier() { return 1.0F; }
    @Override public float spread() { return 0.0F; }
    @Override public int projectiles() { return 1; }
    @Override public float ricochetAngle() { return 0.0F; }
    @Override public int maxRicochets() { return 0; }
    @Override public boolean penetrates() { return kind == Kind.HOOK; }
    @Override public boolean penetrationDamageFalloff() { return false; }
    @Override public double projectileSpeed() { return SPEED; }
    @Override public int projectileLifetime() { return lifetime; }

    public Kind kind() { return kind; }
    public float damage() { return damage; }
    public float gravity() { return GRAVITY; }

    public static ChargeThrowerAmmoType fromLegacyMetadata(int metadata) {
        for (ChargeThrowerAmmoType type : values()) {
            if (type.legacyMetadata == metadata) return type;
        }
        return MORTAR;
    }

    public enum Kind { HOOK, MORTAR, CHARGED_MORTAR }
}
