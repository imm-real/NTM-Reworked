package com.hbm.ntm.weapon;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** Usable XFactory40mm loads. The two C-130 flares are still missing. */
public enum FortyMillimeterAmmoType implements SednaAmmoType {
    SIGNAL_FLARE("g26_flare", 50, Family.FLARE, 1.0F, 0.015D, 100, 0.0F),
    HIGH_EXPLOSIVE("g40_he", 53, Family.HE, 1.0F, 0.035D, 200, 5.0F),
    SHAPED_CHARGE("g40_heat", 54, Family.HEAT, 0.5F, 0.035D, 200, 3.5F),
    DEMOLITION("g40_demo", 55, Family.DEMOLITION, 0.75F, 0.035D, 200, 5.0F),
    INCENDIARY("g40_inc", 56, Family.INCENDIARY, 0.75F, 0.035D, 200, 3.0F),
    WHITE_PHOSPHORUS("g40_phosphorus", 57, Family.PHOSPHORUS, 0.75F, 0.035D, 200, 3.0F);

    private final String serializedName;
    private final int legacyMetadata;
    private final Family family;
    private final float damageMultiplier;
    private final double gravity;
    private final int lifeTicks;
    private final float blastRadius;

    FortyMillimeterAmmoType(String serializedName, int legacyMetadata, Family family,
                            float damageMultiplier, double gravity, int lifeTicks, float blastRadius) {
        this.serializedName = serializedName;
        this.legacyMetadata = legacyMetadata;
        this.family = family;
        this.damageMultiplier = damageMultiplier;
        this.gravity = gravity;
        this.lifeTicks = lifeTicks;
        this.blastRadius = blastRadius;
    }

    @Override public String serializedName() { return serializedName; }
    @Override public int legacyMetadata() { return legacyMetadata; }
    // Stable ammo IDs live directly in the magazine. The old runtime
    // BulletConfig ids were registration-order internals and were never serialized item identity.
    @Override public int legacyBulletConfig() { return legacyMetadata; }
    @Override public float damageMultiplier() { return damageMultiplier; }
    @Override public float spread() { return 0.0F; }
    @Override public int projectiles() { return 1; }
    @Override public float ricochetAngle() { return 15.0F; }
    @Override public int maxRicochets() { return 0; }
    @Override public boolean penetrates() { return false; }
    @Override public boolean penetrationDamageFalloff() { return true; }
    @Override public float impactExplosionRadius() { return blastRadius; }

    public Family family() { return family; }
    public double gravity() { return gravity; }
    public int lifeTicks() { return lifeTicks; }
    public boolean isFlare() { return family == Family.FLARE; }
    public boolean isGrenade() { return family != Family.FLARE; }

    public static FortyMillimeterAmmoType fromStack(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(SednaAmmoType.TYPE_KEY)) {
            String name = tag.getString(SednaAmmoType.TYPE_KEY);
            for (FortyMillimeterAmmoType type : values()) {
                if (type.serializedName.equals(name)) return type;
            }
        }
        CustomModelData modelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return fromLegacyMetadata(modelData == null ? SIGNAL_FLARE.legacyMetadata : modelData.value());
    }

    public static FortyMillimeterAmmoType fromLegacyMetadata(int metadata) {
        for (FortyMillimeterAmmoType type : values()) if (type.legacyMetadata == metadata) return type;
        return SIGNAL_FLARE;
    }

    public enum Family { FLARE, HE, HEAT, DEMOLITION, INCENDIARY, PHOSPHORUS }
}
