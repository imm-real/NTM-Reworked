package com.hbm.ntm.weapon;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** Four .22 LR loads with tiny bullets and IDs 16 through 19. */
public enum TwentyTwoAmmoType implements SednaAmmoType {
    SOFT_POINT("p22_sp", 16, 18, 1.0F, false, true,
            1.25F, 0.0F, 0.0F),
    FULL_METAL_JACKET("p22_fmj", 17, 19, 0.8F, false, true,
            1.25F, 1.0F, 0.1F),
    HOLLOW_POINT("p22_jhp", 18, 20, 1.5F, false, true,
            1.5F, 0.0F, -0.25F),
    ARMOR_PIERCING("p22_ap", 19, 21, 1.25F, true, false,
            1.25F, 2.5F, 0.15F);

    private final String serializedName;
    private final int legacyMetadata;
    private final int legacyBulletConfig;
    private final float damageMultiplier;
    private final boolean penetrates;
    private final boolean penetrationDamageFalloff;
    private final float headshotMultiplier;
    private final float thresholdNegation;
    private final float armorPiercing;

    TwentyTwoAmmoType(String serializedName, int legacyMetadata, int legacyBulletConfig,
                      float damageMultiplier, boolean penetrates,
                      boolean penetrationDamageFalloff, float headshotMultiplier,
                      float thresholdNegation, float armorPiercing) {
        this.serializedName = serializedName;
        this.legacyMetadata = legacyMetadata;
        this.legacyBulletConfig = legacyBulletConfig;
        this.damageMultiplier = damageMultiplier;
        this.penetrates = penetrates;
        this.penetrationDamageFalloff = penetrationDamageFalloff;
        this.headshotMultiplier = headshotMultiplier;
        this.thresholdNegation = thresholdNegation;
        this.armorPiercing = armorPiercing;
    }

    @Override public String serializedName() { return serializedName; }
    @Override public int legacyMetadata() { return legacyMetadata; }
    @Override public int legacyBulletConfig() { return legacyBulletConfig; }
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
    @Override public int tracerDarkColor() {
        return this == ARMOR_PIERCING ? 0xFFFF6A00 : SednaAmmoType.super.tracerDarkColor();
    }
    @Override public int tracerLightColor() {
        return this == ARMOR_PIERCING ? 0xFFFFE28D : SednaAmmoType.super.tracerLightColor();
    }

    public static TwentyTwoAmmoType fromStack(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(SednaAmmoType.TYPE_KEY)) {
            String name = tag.getString(SednaAmmoType.TYPE_KEY);
            for (TwentyTwoAmmoType type : values()) {
                if (type.serializedName.equals(name)) return type;
            }
        }
        CustomModelData modelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return fromLegacyMetadata(modelData == null ? SOFT_POINT.legacyMetadata : modelData.value());
    }

    public static TwentyTwoAmmoType fromLegacyMetadata(int metadata) {
        for (TwentyTwoAmmoType type : values()) if (type.legacyMetadata == metadata) return type;
        return SOFT_POINT;
    }

    public static TwentyTwoAmmoType fromLegacyBulletConfig(int config) {
        for (TwentyTwoAmmoType type : values()) if (type.legacyBulletConfig == config) return type;
        return SOFT_POINT;
    }
}
