package com.hbm.ntm.item;

import net.minecraft.world.item.Item;

/** Source screwdriver tooling identity; Foundry Mold removal consumes one durability. */
public final class ScrewdriverItem extends Item {
    public ScrewdriverItem() {
        super(new Properties().durability(100));
    }
}
