package com.hbm.ntm.weapon;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Four flamethrower tanks filed under ammo IDs 63 through 66. */
public enum FlamerFuelType implements SednaAmmoType {
    DIESEL("flame_diesel", 63, 63, 100, 0.02F, 0.0F, 2.0F, 1.0F, 100),
    GAS("flame_gas", 64, 64, 10, 0.0F, 0.05F, 0.0F, 0.0F, 0),
    NAPALM("flame_napalm", 65, 65, 200, 0.02F, 0.0F, 2.5F, 1.0F, 200),
    BALEFIRE("flame_balefire", 66, 66, 200, 0.02F, 0.0F, 3.0F, 1.0F, 300);

    public static final int RELOAD_AMOUNT = 500;

    private final String serializedName;
    private final int legacyMetadata;
    private final int legacyBulletConfig;
    private final int lifeTicks;
    private final float gravity;
    private final float spread;
    private final float lingerWidth;
    private final float lingerHeight;
    private final int lingerTicks;

    FlamerFuelType(String serializedName, int legacyMetadata, int legacyBulletConfig,
                   int lifeTicks, float gravity, float spread,
                   float lingerWidth, float lingerHeight, int lingerTicks) {
        this.serializedName = serializedName;
        this.legacyMetadata = legacyMetadata;
        this.legacyBulletConfig = legacyBulletConfig;
        this.lifeTicks = lifeTicks;
        this.gravity = gravity;
        this.spread = spread;
        this.lingerWidth = lingerWidth;
        this.lingerHeight = lingerHeight;
        this.lingerTicks = lingerTicks;
    }

    @Override public String serializedName() { return serializedName; }
    @Override public int legacyMetadata() { return legacyMetadata; }
    @Override public int legacyBulletConfig() { return legacyBulletConfig; }
    @Override public float damageMultiplier() { return 1.0F; }
    @Override public float spread() { return spread; }
    @Override public int projectiles() { return 1; }
    @Override public float ricochetAngle() { return 0.0F; }
    @Override public int maxRicochets() { return 0; }
    @Override public boolean penetrates() { return false; }
    @Override public boolean penetrationDamageFalloff() { return false; }

    public int lifeTicks() { return lifeTicks; }
    public float gravity() { return gravity; }
    public float lingerWidth() { return lingerWidth; }
    public float lingerHeight() { return lingerHeight; }
    public int lingerTicks() { return lingerTicks; }
    public boolean lingers() { return lingerTicks > 0; }
    public boolean isBalefire() { return this == BALEFIRE; }

    public static FlamerFuelType fromLegacyMetadata(int metadata) {
        for (FlamerFuelType type : values()) if (type.legacyMetadata == metadata) return type;
        return DIESEL;
    }

    public static FlamerFuelType fromLegacyBulletConfig(int config) {
        for (FlamerFuelType type : values()) if (type.legacyBulletConfig == config) return type;
        return DIESEL;
    }

    public static FlamerFuelType fromStack(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(TYPE_KEY)) {
            String name = tag.getString(TYPE_KEY);
            for (FlamerFuelType type : values()) if (type.serializedName.equals(name)) return type;
        }
        var modelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return fromLegacyMetadata(modelData == null ? DIESEL.legacyMetadata : modelData.value());
    }
}
