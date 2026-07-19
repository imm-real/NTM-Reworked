package com.hbm.ntm.item;

import net.minecraft.world.item.Item;

public final class ShredderBladeItem extends Item {
    public ShredderBladeItem(int durability) {
        super(durability > 0
                ? new Item.Properties().stacksTo(1).durability(durability)
                : new Item.Properties().stacksTo(1));
    }
}
