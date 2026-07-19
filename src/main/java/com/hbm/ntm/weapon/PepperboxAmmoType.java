package com.hbm.ntm.weapon;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** Four standard-ammo variants for guns with historical ambitions. */
public enum PepperboxAmmoType implements SednaAmmoType {
    STONE("stone", 0, 2, 1.0F, 0.025F, 1, 15.0F, 2, false, true),
    FLINT("stone_ap", 1, 3, 1.5F, 0.01F, 1, 5.0F, 2, true, true),
    IRON("stone_iron", 2, 4, 1.5F, 0.0F, 1, 90.0F, 5, true, false),
    SHOT("stone_shot", 3, 5, 1.0F / 6.0F, 0.1F, 6, 45.0F, 2, false, true);

    private final String serializedName;
    private final int legacyMetadata;
    private final int legacyBulletConfig;
    private final float damageMultiplier;
    private final float spread;
    private final int projectiles;
    private final float ricochetAngle;
    private final int maxRicochets;
    private final boolean penetrates;
    private final boolean penetrationDamageFalloff;

    PepperboxAmmoType(String serializedName, int legacyMetadata, int legacyBulletConfig,
                      float damageMultiplier, float spread, int projectiles,
                      float ricochetAngle, int maxRicochets, boolean penetrates,
                      boolean penetrationDamageFalloff) {
        this.serializedName = serializedName;
        this.legacyMetadata = legacyMetadata;
        this.legacyBulletConfig = legacyBulletConfig;
        this.damageMultiplier = damageMultiplier;
        this.spread = spread;
        this.projectiles = projectiles;
        this.ricochetAngle = ricochetAngle;
        this.maxRicochets = maxRicochets;
        this.penetrates = penetrates;
        this.penetrationDamageFalloff = penetrationDamageFalloff;
    }

    public String serializedName() { return serializedName; }
    public int legacyMetadata() { return legacyMetadata; }
    public int legacyBulletConfig() { return legacyBulletConfig; }
    public float damageMultiplier() { return damageMultiplier; }
    public float spread() { return spread; }
    public int projectiles() { return projectiles; }
    public float ricochetAngle() { return ricochetAngle; }
    public int maxRicochets() { return maxRicochets; }
    public boolean penetrates() { return penetrates; }
    public boolean penetrationDamageFalloff() { return penetrationDamageFalloff; }
    @Override public float headshotMultiplier() { return 1.0F; }
    @Override public boolean blackPowder() { return true; }

    public ItemStack createStack(Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        CompoundTag tag = new CompoundTag();
        tag.putString(SednaAmmoType.TYPE_KEY, serializedName);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(legacyMetadata));
        return stack;
    }

    public static PepperboxAmmoType fromStack(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(SednaAmmoType.TYPE_KEY)) {
            String name = tag.getString(SednaAmmoType.TYPE_KEY);
            for (PepperboxAmmoType type : values()) {
                if (type.serializedName.equals(name)) return type;
            }
        }
        CustomModelData modelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return fromLegacyMetadata(modelData == null ? 0 : modelData.value());
    }

    public static PepperboxAmmoType fromLegacyMetadata(int metadata) {
        for (PepperboxAmmoType type : values()) {
            if (type.legacyMetadata == metadata) return type;
        }
        return STONE;
    }

    public static PepperboxAmmoType fromLegacyBulletConfig(int config) {
        for (PepperboxAmmoType type : values()) {
            if (type.legacyBulletConfig == config) return type;
        }
        return STONE;
    }
}
