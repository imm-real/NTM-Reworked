package com.hbm.ntm.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Stores a mask filter without reviving the hfrFilter NBT crypt. */
public final class MaskFilterStorage {
    private static final String FILTER_TYPE = "hfrFilterType";
    private static final String FILTER_DAMAGE = "hfrFilterDamage";

    private MaskFilterStorage() {
    }

    public static void install(ItemStack mask, ItemStack filter) {
        if (!(filter.getItem() instanceof HazmatFilterItem item)) return;
        CompoundTag tag = data(mask);
        tag.putString(FILTER_TYPE, item.type().id());
        tag.putInt(FILTER_DAMAGE, filter.getDamageValue());
        mask.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static ItemStack installed(ItemStack mask) {
        CompoundTag tag = data(mask);
        if (!tag.contains(FILTER_TYPE)) return ItemStack.EMPTY;
        HazmatFilterItem.FilterType type = HazmatFilterItem.FilterType.byId(tag.getString(FILTER_TYPE));
        ItemStack filter = new ItemStack(type.item());
        filter.setDamageValue(tag.getInt(FILTER_DAMAGE));
        return filter;
    }

    public static void remove(ItemStack mask) {
        CompoundTag tag = data(mask);
        tag.remove(FILTER_TYPE);
        tag.remove(FILTER_DAMAGE);
        mask.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void damage(ItemStack mask, int amount) {
        ItemStack filter = installed(mask);
        if (filter.isEmpty() || filter.getMaxDamage() == 0) return;
        int damage = filter.getDamageValue() + amount;
        // The 1.7.10 helper used a strict greater-than check, leaving a filter at max damage installed.
        if (damage > filter.getMaxDamage()) {
            remove(mask);
        } else {
            filter.setDamageValue(damage);
            install(mask, filter);
        }
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
}
