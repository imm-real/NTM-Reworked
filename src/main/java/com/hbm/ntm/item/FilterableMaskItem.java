package com.hbm.ntm.item;

import net.minecraft.world.item.ItemStack;

/** Marks headgear willing to swallow gas-mask filters. */
public interface FilterableMaskItem {
    default boolean hbm$acceptsFilter(ItemStack mask, HazmatFilterItem filter) {
        return true;
    }
}
