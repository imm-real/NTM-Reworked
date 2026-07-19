package com.hbm.ntm.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Burnt electrode variants filed under component data. */
public final class ArcElectrodeBurntItem extends Item {
    public ArcElectrodeBurntItem() { super(new Properties().stacksTo(1)); }

    @Override public String getDescriptionId(ItemStack stack) {
        return "item.hbm.arc_electrode_burnt." + ArcElectrodeItem.type(stack).id();
    }
}
