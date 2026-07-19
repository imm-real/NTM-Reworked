package com.hbm.ntm.weapon;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** Nine ordinary 12-gauge loads occupying IDs 41 through 49. */
public enum Shotgun12GaugeAmmoType implements SednaAmmoType {
    BLACK_POWDER_BUCKSHOT("g12_bp", 41, 43, 0.75F / 8.0F, 0.035F, 8, 15.0F,
            1.25F, 0.0F, 0.0F, true, 0.0F, 0),
    BLACK_POWDER_MAGNUM("g12_bp_magnum", 42, 44, 0.75F / 4.0F, 0.035F, 4, 25.0F,
            1.25F, 0.0F, 0.0F, true, 0.0F, 0),
    BLACK_POWDER_SLUG("g12_bp_slug", 43, 45, 0.75F, 0.01F, 1, 5.0F,
            1.25F, 0.0F, 0.0F, true, 0.0F, 0),
    BUCKSHOT("g12", 44, 46, 1.0F / 8.0F, 0.035F, 8, 15.0F,
            1.25F, 2.0F, 0.0F, false, 0.0F, 0),
    SLUG("g12_slug", 45, 47, 1.0F, 0.0F, 1, 25.0F,
            1.5F, 4.0F, 0.15F, false, 0.0F, 0),
    FLECHETTE("g12_flechette", 46, 48, 1.0F / 8.0F, 0.025F, 8, 5.0F,
            1.25F, 5.0F, 0.2F, false, 0.0F, 0),
    MAGNUM("g12_magnum", 47, 49, 2.0F / 4.0F, 0.015F, 4, 15.0F,
            1.25F, 4.0F, 0.0F, false, 0.0F, 0),
    EXPLOSIVE("g12_explosive", 48, 50, 2.5F, 0.0F, 1, 15.0F,
            1.25F, 0.0F, 0.0F, false, 2.0F, 0),
    PHOSPHORUS("g12_phosphorus", 49, 51, 1.0F / 8.0F, 0.015F, 8, 15.0F,
            1.25F, 0.0F, 0.0F, false, 0.0F, 300);

    private final String serializedName;
    private final int legacyMetadata;
    private final int legacyBulletConfig;
    private final float damageMultiplier;
    private final float spread;
    private final int projectiles;
    private final float ricochetAngle;
    private final float headshotMultiplier;
    private final float thresholdNegation;
    private final float armorPiercing;
    private final boolean blackPowder;
    private final float explosionRadius;
    private final int phosphorusTicks;

    Shotgun12GaugeAmmoType(String serializedName, int legacyMetadata, int legacyBulletConfig,
                           float damageMultiplier, float spread, int projectiles,
                           float ricochetAngle, float headshotMultiplier,
                           float thresholdNegation, float armorPiercing, boolean blackPowder,
                           float explosionRadius, int phosphorusTicks) {
        this.serializedName = serializedName;
        this.legacyMetadata = legacyMetadata;
        this.legacyBulletConfig = legacyBulletConfig;
        this.damageMultiplier = damageMultiplier;
        this.spread = spread;
        this.projectiles = projectiles;
        this.ricochetAngle = ricochetAngle;
        this.headshotMultiplier = headshotMultiplier;
        this.thresholdNegation = thresholdNegation;
        this.armorPiercing = armorPiercing;
        this.blackPowder = blackPowder;
        this.explosionRadius = explosionRadius;
        this.phosphorusTicks = phosphorusTicks;
    }

    @Override public String serializedName() { return serializedName; }
    @Override public int legacyMetadata() { return legacyMetadata; }
    @Override public int legacyBulletConfig() { return legacyBulletConfig; }
    @Override public float damageMultiplier() { return damageMultiplier; }
    @Override public float spread() { return spread; }
    @Override public int projectiles() { return projectiles; }
    @Override public float ricochetAngle() { return ricochetAngle; }
    @Override public int maxRicochets() { return 2; }
    @Override public boolean penetrates() { return false; }
    @Override public boolean penetrationDamageFalloff() { return true; }
    @Override public float headshotMultiplier() { return headshotMultiplier; }
    @Override public float armorThresholdNegation() { return thresholdNegation; }
    @Override public float armorPiercing() { return armorPiercing; }
    @Override public boolean blackPowder() { return blackPowder; }
    @Override public float impactExplosionRadius() { return explosionRadius; }
    @Override public int phosphorusTicks() { return phosphorusTicks; }
    @Override public int tracerDarkColor() {
        return switch (this) {
            case FLECHETTE -> 0xFF8C8C8C;
            case EXPLOSIVE -> 0xFF9E082E;
            case PHOSPHORUS -> 0xFFFF6A00;
            default -> SednaAmmoType.super.tracerDarkColor();
        };
    }
    @Override public int tracerLightColor() {
        return switch (this) {
            case FLECHETTE -> 0xFFCACACA;
            case EXPLOSIVE -> 0xFFFF8A79;
            case PHOSPHORUS -> 0xFFFFE28D;
            default -> SednaAmmoType.super.tracerLightColor();
        };
    }

    public static Shotgun12GaugeAmmoType fromStack(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(SednaAmmoType.TYPE_KEY)) {
            String name = tag.getString(SednaAmmoType.TYPE_KEY);
            for (Shotgun12GaugeAmmoType type : values()) {
                if (type.serializedName.equals(name)) return type;
            }
        }
        CustomModelData modelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return fromLegacyMetadata(modelData == null ? BUCKSHOT.legacyMetadata : modelData.value());
    }

    public static Shotgun12GaugeAmmoType fromLegacyMetadata(int metadata) {
        for (Shotgun12GaugeAmmoType type : values()) if (type.legacyMetadata == metadata) return type;
        return BUCKSHOT;
    }

    public static Shotgun12GaugeAmmoType fromLegacyBulletConfig(int config) {
        for (Shotgun12GaugeAmmoType type : values()) if (type.legacyBulletConfig == config) return type;
        return BUCKSHOT;
    }
}
