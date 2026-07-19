package com.hbm.ntm.weapon;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** Five rocket warheads still carrying ammo IDs 58 through 62. */
public enum RocketAmmoType implements SednaAmmoType {
    HIGH_EXPLOSIVE("rocket_he", 58, Family.HIGH_EXPLOSIVE, 1.0F, 5.0F, 0),
    SHAPED_CHARGE("rocket_heat", 59, Family.SHAPED_CHARGE, 0.5F, 3.5F, 0),
    DEMOLITION("rocket_demo", 60, Family.DEMOLITION, 0.75F, 5.0F, 0),
    INCENDIARY("rocket_inc", 61, Family.INCENDIARY, 0.75F, 3.0F, 300),
    WHITE_PHOSPHORUS("rocket_phosphorus", 62, Family.WHITE_PHOSPHORUS, 0.75F, 3.0F, 600);

    public static final int LIFE_TICKS = 300;
    public static final int SELF_DAMAGE_DELAY = 10;
    public static final float ACCELERATION_PER_TICK = 0.4F;
    public static final float ACCELERATION_THRESHOLD = 7.0F;
    /** Source tests {@code accel < 7} before adding .4, so the discrete sequence ends at 7.2. */
    public static final float MAX_ACCELERATION = 7.2F;

    private final String serializedName;
    private final int legacyMetadata;
    private final Family family;
    private final float damageMultiplier;
    private final float blastRadius;
    private final int lingeringTicks;

    RocketAmmoType(String serializedName, int legacyMetadata, Family family,
                   float damageMultiplier, float blastRadius, int lingeringTicks) {
        this.serializedName = serializedName;
        this.legacyMetadata = legacyMetadata;
        this.family = family;
        this.damageMultiplier = damageMultiplier;
        this.blastRadius = blastRadius;
        this.lingeringTicks = lingeringTicks;
    }

    @Override public String serializedName() { return serializedName; }
    @Override public int legacyMetadata() { return legacyMetadata; }
    @Override public int legacyBulletConfig() { return legacyMetadata; }
    @Override public float damageMultiplier() { return damageMultiplier; }
    @Override public float spread() { return 0.0F; }
    @Override public int projectiles() { return 1; }
    @Override public float ricochetAngle() { return 5.0F; }
    @Override public int maxRicochets() { return 0; }
    @Override public boolean penetrates() { return false; }
    @Override public boolean penetrationDamageFalloff() { return true; }
    @Override public float impactExplosionRadius() { return blastRadius; }
    @Override public int phosphorusTicks() {
        return family == Family.WHITE_PHOSPHORUS ? lingeringTicks : 0;
    }

    public Family family() { return family; }
    public int lifeTicks() { return LIFE_TICKS; }
    public int selfDamageDelay() { return SELF_DAMAGE_DELAY; }
    public float accelerationPerTick() { return ACCELERATION_PER_TICK; }
    public float accelerationThreshold() { return ACCELERATION_THRESHOLD; }
    public float maximumAcceleration() { return MAX_ACCELERATION; }
    public float accelerate(float currentSpeed) {
        return currentSpeed < ACCELERATION_THRESHOLD
                ? currentSpeed + ACCELERATION_PER_TICK : currentSpeed;
    }
    public int lingeringTicks() { return lingeringTicks; }
    public boolean destroysBlocks() { return family == Family.DEMOLITION; }
    public boolean createsLingeringFire() {
        return family == Family.INCENDIARY || family == Family.WHITE_PHOSPHORUS;
    }

    public static RocketAmmoType fromStack(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(SednaAmmoType.TYPE_KEY)) {
            String name = tag.getString(SednaAmmoType.TYPE_KEY);
            for (RocketAmmoType type : values()) if (type.serializedName.equals(name)) return type;
        }
        CustomModelData modelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return fromLegacyMetadata(modelData == null ? HIGH_EXPLOSIVE.legacyMetadata : modelData.value());
    }

    public static RocketAmmoType fromLegacyMetadata(int metadata) {
        for (RocketAmmoType type : values()) if (type.legacyMetadata == metadata) return type;
        return HIGH_EXPLOSIVE;
    }

    public enum Family {
        HIGH_EXPLOSIVE,
        SHAPED_CHARGE,
        DEMOLITION,
        INCENDIARY,
        WHITE_PHOSPHORUS
    }
}
