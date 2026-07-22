package com.hbm.ntm.weapon;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** The three source ItemEnumMulti fire-extinguisher tanks. */
public enum FireExtinguisherAmmoType {
    WATER("water", 0, 0.025F),
    FOAM("foam", 1, 0.05F),
    SAND("sand", 2, 0.05F);

    public static final int RELOAD_AMOUNT = 300;
    private static final String TYPE_KEY = "hbm_fireext_type";

    private final String serializedName;
    private final int legacyMetadata;
    private final float spread;

    FireExtinguisherAmmoType(String serializedName, int legacyMetadata, float spread) {
        this.serializedName = serializedName;
        this.legacyMetadata = legacyMetadata;
        this.spread = spread;
    }

    public String serializedName() { return serializedName; }
    public int legacyMetadata() { return legacyMetadata; }
    public float spread() { return spread; }

    public ItemStack createStack(Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        CompoundTag tag = new CompoundTag();
        tag.putString(TYPE_KEY, serializedName);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(legacyMetadata));
        return stack;
    }

    public static FireExtinguisherAmmoType fromLegacyMetadata(int metadata) {
        for (FireExtinguisherAmmoType type : values()) {
            if (type.legacyMetadata == metadata) return type;
        }
        return WATER;
    }

    public static FireExtinguisherAmmoType fromStack(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(TYPE_KEY)) {
            String name = tag.getString(TYPE_KEY);
            for (FireExtinguisherAmmoType type : values()) {
                if (type.serializedName.equals(name)) return type;
            }
        }
        CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return fromLegacyMetadata(model == null ? 0 : model.value());
    }
}
