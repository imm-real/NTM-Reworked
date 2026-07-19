package com.hbm.ntm.weapon;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** The seven standard and two secret .50 BMG identities from {@code XFactory50}. */
public enum FiftyCalAmmoType implements SednaAmmoType {
    SOFT_POINT("bmg50_sp", 33, 33, false, 1.0F, false, true, 1.25F, 0.0F, 0.0F, 1.0F),
    FULL_METAL_JACKET("bmg50_fmj", 34, 34, false, 0.8F, false, true, 1.25F, 7.0F, 0.1F, 1.0F),
    HOLLOW_POINT("bmg50_jhp", 35, 35, false, 1.5F, false, true, 1.5F, 0.0F, -0.25F, 1.0F),
    ARMOR_PIERCING("bmg50_ap", 36, 36, false, 1.25F, true, false, 1.25F, 17.5F, 0.15F, 1.0F),
    DEPLETED_URANIUM("bmg50_du", 37, 37, false, 1.5F, true, false, 1.25F, 21.0F, 0.25F, 1.0F),
    HIGH_EXPLOSIVE("bmg50_he", 83, 83, false, 1.75F, false, true, 1.25F, 0.0F, 0.0F, 3.0F),
    STARMETAL("bmg50_sm", 94, 94, false, 2.5F, true, false, 1.25F, 30.0F, 0.35F, 10.0F),
    EQUESTRIAN("bmg50_equestrian", 4, 104, true, 0.0F, false, true, 1.25F, 0.0F, 0.0F, 1.0F),
    BLACK("bmg50_black", 6, 106, true, 1.5F, true, false, 3.0F, 30.0F, 0.35F, 5.0F);

    private final String serializedName;
    private final int legacyMetadata;
    private final int bulletConfig;
    private final boolean secret;
    private final float damageMultiplier;
    private final boolean penetrates;
    private final boolean penetrationDamageFalloff;
    private final float headshotMultiplier;
    private final float thresholdNegation;
    private final float armorPiercing;
    private final float wear;

    FiftyCalAmmoType(String serializedName, int legacyMetadata, int bulletConfig, boolean secret,
                     float damageMultiplier, boolean penetrates, boolean penetrationDamageFalloff,
                     float headshotMultiplier, float thresholdNegation, float armorPiercing, float wear) {
        this.serializedName = serializedName;
        this.legacyMetadata = legacyMetadata;
        this.bulletConfig = bulletConfig;
        this.secret = secret;
        this.damageMultiplier = damageMultiplier;
        this.penetrates = penetrates;
        this.penetrationDamageFalloff = penetrationDamageFalloff;
        this.headshotMultiplier = headshotMultiplier;
        this.thresholdNegation = thresholdNegation;
        this.armorPiercing = armorPiercing;
        this.wear = wear;
    }

    @Override public String serializedName() { return serializedName; }
    @Override public int legacyMetadata() { return legacyMetadata; }
    @Override public int legacyBulletConfig() { return bulletConfig; }
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
    @Override public float impactExplosionRadius() { return this == HIGH_EXPLOSIVE ? 2.0F : 0.0F; }
    @Override public boolean spectral() { return this == BLACK; }
    @Override public boolean spawnsBuildingOnImpact() { return this == EQUESTRIAN; }

    @Override public int tracerDarkColor() {
        return switch (this) {
            case ARMOR_PIERCING -> 0xFFFF6A00;
            case DEPLETED_URANIUM -> 0xFF5CCD41;
            case HIGH_EXPLOSIVE -> 0xFFD8CA00;
            case STARMETAL -> 0xFF42A8DD;
            case BLACK -> 0xFF000000;
            default -> SednaAmmoType.super.tracerDarkColor();
        };
    }

    @Override public int tracerLightColor() {
        return switch (this) {
            case ARMOR_PIERCING -> 0xFFFFE28D;
            case DEPLETED_URANIUM -> 0xFFE9FF8D;
            case HIGH_EXPLOSIVE -> 0xFFFFF19D;
            case STARMETAL -> 0xFFFFFFFF;
            case BLACK -> 0xFF7F006E;
            default -> SednaAmmoType.super.tracerLightColor();
        };
    }

    @Override public boolean tracerFullbright() {
        return this == HIGH_EXPLOSIVE || this == STARMETAL || this == BLACK;
    }

    public boolean secret() { return secret; }

    public static FiftyCalAmmoType fromStack(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(SednaAmmoType.TYPE_KEY)) {
            String name = tag.getString(SednaAmmoType.TYPE_KEY);
            for (FiftyCalAmmoType type : values()) {
                if (type.serializedName.equals(name)) return type;
            }
        }
        CustomModelData modelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return fromLegacyMetadata(modelData == null ? SOFT_POINT.legacyMetadata : modelData.value());
    }

    public static FiftyCalAmmoType fromLegacyMetadata(int metadata) {
        for (FiftyCalAmmoType type : values()) {
            if (type.legacyMetadata == metadata) return type;
        }
        return SOFT_POINT;
    }

    public static FiftyCalAmmoType fromLegacyBulletConfig(int config) {
        for (FiftyCalAmmoType type : values()) {
            if (type.bulletConfig == config) return type;
        }
        return SOFT_POINT;
    }
}
