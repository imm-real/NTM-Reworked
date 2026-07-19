package com.hbm.ntm.weapon;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** Six 7.62 mm loads from the XFactory762mm cabinet. */
public enum SevenSixTwoAmmoType implements SednaAmmoType {
    SOFT_POINT("r762_sp", 28, 1.0F, false, true, 1.25F, 0.0F, 0.0F, 1.0F, 0.0F),
    FULL_METAL_JACKET("r762_fmj", 29, 0.8F, false, true, 1.25F, 5.0F, 0.1F, 1.0F, 0.0F),
    HOLLOW_POINT("r762_jhp", 30, 1.5F, false, true, 1.5F, 0.0F, -0.25F, 1.0F, 0.0F),
    ARMOR_PIERCING("r762_ap", 31, 1.25F, true, false, 1.25F, 12.5F, 0.15F, 1.0F, 0.0F),
    DEPLETED_URANIUM("r762_du", 32, 1.5F, true, false, 1.25F, 15.0F, 0.25F, 1.0F, 0.0F),
    HIGH_EXPLOSIVE("r762_he", 82, 1.75F, false, true, 1.25F, 0.0F, 0.0F, 3.0F, 1.5F);

    private final String serializedName;
    private final int legacyMetadata;
    private final float damageMultiplier;
    private final boolean penetrates;
    private final boolean penetrationDamageFalloff;
    private final float headshotMultiplier;
    private final float thresholdNegation;
    private final float armorPiercing;
    private final float wear;
    private final float explosionRadius;

    SevenSixTwoAmmoType(String serializedName, int legacyMetadata, float damageMultiplier,
                        boolean penetrates, boolean penetrationDamageFalloff,
                        float headshotMultiplier, float thresholdNegation, float armorPiercing,
                        float wear, float explosionRadius) {
        this.serializedName = serializedName;
        this.legacyMetadata = legacyMetadata;
        this.damageMultiplier = damageMultiplier;
        this.penetrates = penetrates;
        this.penetrationDamageFalloff = penetrationDamageFalloff;
        this.headshotMultiplier = headshotMultiplier;
        this.thresholdNegation = thresholdNegation;
        this.armorPiercing = armorPiercing;
        this.wear = wear;
        this.explosionRadius = explosionRadius;
    }

    @Override public String serializedName() { return serializedName; }
    @Override public int legacyMetadata() { return legacyMetadata; }
    @Override public int legacyBulletConfig() { return legacyMetadata; }
    @Override public float damageMultiplier() { return damageMultiplier; }
    @Override public float spread() { return 0.0F; }
    @Override public int projectiles() { return 1; }
    @Override public float ricochetAngle() { return 5.0F; }
    @Override public int maxRicochets() { return 2; }
    @Override public boolean penetrates() { return penetrates; }
    @Override public boolean penetrationDamageFalloff() { return penetrationDamageFalloff; }
    @Override public float headshotMultiplier() { return headshotMultiplier; }
    @Override public float armorThresholdNegation() { return thresholdNegation; }
    @Override public float armorPiercing() { return armorPiercing; }
    @Override public float wear() { return wear; }
    @Override public float impactExplosionRadius() { return explosionRadius; }
    @Override public int tracerDarkColor() {
        return switch (this) {
            case ARMOR_PIERCING -> 0xFFFF6A00;
            case DEPLETED_URANIUM -> 0xFF5CCD41;
            case HIGH_EXPLOSIVE -> 0xFFD8CA00;
            default -> SednaAmmoType.super.tracerDarkColor();
        };
    }
    @Override public int tracerLightColor() {
        return switch (this) {
            case ARMOR_PIERCING -> 0xFFFFE28D;
            case DEPLETED_URANIUM -> 0xFFE9FF8D;
            case HIGH_EXPLOSIVE -> 0xFFFFF19D;
            default -> SednaAmmoType.super.tracerLightColor();
        };
    }
    @Override public boolean tracerFullbright() { return this == HIGH_EXPLOSIVE; }

    public static SevenSixTwoAmmoType fromStack(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(SednaAmmoType.TYPE_KEY)) {
            String name = tag.getString(SednaAmmoType.TYPE_KEY);
            for (SevenSixTwoAmmoType type : values()) {
                if (type.serializedName.equals(name)) return type;
            }
        }
        CustomModelData modelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return fromLegacyMetadata(modelData == null ? SOFT_POINT.legacyMetadata : modelData.value());
    }

    public static SevenSixTwoAmmoType fromLegacyMetadata(int metadata) {
        for (SevenSixTwoAmmoType type : values()) if (type.legacyMetadata == metadata) return type;
        return SOFT_POINT;
    }

    public static SevenSixTwoAmmoType fromLegacyBulletConfig(int config) {
        return fromLegacyMetadata(config);
    }
}
