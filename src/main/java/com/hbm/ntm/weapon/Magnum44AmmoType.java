package com.hbm.ntm.weapon;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** Six .44 Magnum loads with permanent IDs 10 through 15. */
public enum Magnum44AmmoType implements SednaAmmoType {
    BLACK_POWDER("m44_bp", 10, 12, 0.75F, false, true,
            1.25F, 0.0F, 0.0F, 1.0F, true, 0xFFFFBF00, 0xFFFFFFFF),
    SOFT_POINT("m44_sp", 11, 13, 1.0F, false, true,
            1.25F, 0.0F, 0.0F, 1.0F, false, 0xFFFFBF00, 0xFFFFFFFF),
    FULL_METAL_JACKET("m44_fmj", 12, 14, 0.8F, false, true,
            1.25F, 3.0F, 0.1F, 1.0F, false, 0xFFFFBF00, 0xFFFFFFFF),
    HOLLOW_POINT("m44_jhp", 13, 15, 1.5F, false, true,
            1.5F, 0.0F, -0.25F, 1.0F, false, 0xFFFFBF00, 0xFFFFFFFF),
    ARMOR_PIERCING("m44_ap", 14, 16, 1.25F, true, false,
            1.25F, 7.5F, 0.15F, 1.0F, false, 0xFFFF6A00, 0xFFFFE28D),
    EXPRESS("m44_express", 15, 17, 1.5F, true, true,
            1.25F, 3.0F, 0.1F, 1.5F, false, 0xFF9E082E, 0xFFFF8A79);

    private final String serializedName;
    private final int legacyMetadata;
    private final int legacyBulletConfig;
    private final float damageMultiplier;
    private final boolean penetrates;
    private final boolean penetrationDamageFalloff;
    private final float headshotMultiplier;
    private final float thresholdNegation;
    private final float armorPiercing;
    private final float wear;
    private final boolean blackPowder;
    private final int frontColor;
    private final int backColor;

    Magnum44AmmoType(String serializedName, int legacyMetadata, int legacyBulletConfig,
                     float damageMultiplier, boolean penetrates, boolean penetrationDamageFalloff,
                     float headshotMultiplier, float thresholdNegation, float armorPiercing, float wear,
                     boolean blackPowder, int frontColor, int backColor) {
        this.serializedName = serializedName;
        this.legacyMetadata = legacyMetadata;
        this.legacyBulletConfig = legacyBulletConfig;
        this.damageMultiplier = damageMultiplier;
        this.penetrates = penetrates;
        this.penetrationDamageFalloff = penetrationDamageFalloff;
        this.headshotMultiplier = headshotMultiplier;
        this.thresholdNegation = thresholdNegation;
        this.armorPiercing = armorPiercing;
        this.wear = wear;
        this.blackPowder = blackPowder;
        this.frontColor = frontColor;
        this.backColor = backColor;
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
    @Override public float wear() { return wear; }
    @Override public boolean blackPowder() { return blackPowder; }
    @Override public int tracerDarkColor() { return frontColor; }
    @Override public int tracerLightColor() { return backColor; }

    public static Magnum44AmmoType fromStack(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(SednaAmmoType.TYPE_KEY)) {
            String name = tag.getString(SednaAmmoType.TYPE_KEY);
            for (Magnum44AmmoType type : values()) {
                if (type.serializedName.equals(name)) return type;
            }
        }
        CustomModelData modelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return fromLegacyMetadata(modelData == null ? SOFT_POINT.legacyMetadata : modelData.value());
    }

    public static Magnum44AmmoType fromLegacyMetadata(int metadata) {
        for (Magnum44AmmoType type : values()) if (type.legacyMetadata == metadata) return type;
        return SOFT_POINT;
    }

    public static Magnum44AmmoType fromLegacyBulletConfig(int config) {
        for (Magnum44AmmoType type : values()) if (type.legacyBulletConfig == config) return type;
        return SOFT_POINT;
    }
}
