package com.hbm.ntm.item;

import net.minecraft.world.item.Item;

public final class StampItem extends Item {
    private final StampType stampType;

    public StampItem(int uses, StampType stampType) {
        super(uses > 0
                ? new Item.Properties().stacksTo(1).durability(uses)
                : new Item.Properties().stacksTo(1));
        this.stampType = stampType;
    }

    public StampType stampType() {
        return stampType;
    }

    public enum StampType {
        FLAT,
        PLATE,
        WIRE,
        CIRCUIT,
        C357,
        C44,
        C9,
        C50,
        PRINTING1,
        PRINTING2,
        PRINTING3,
        PRINTING4,
        PRINTING5,
        PRINTING6,
        PRINTING7,
        PRINTING8
    }
}
