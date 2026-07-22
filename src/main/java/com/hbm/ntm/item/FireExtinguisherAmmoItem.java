package com.hbm.ntm.item;

import com.hbm.ntm.weapon.FireExtinguisherAmmoType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class FireExtinguisherAmmoItem extends Item {
    public FireExtinguisherAmmoItem() {
        super(new Properties());
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return "item.hbm.ammo_fireext." + FireExtinguisherAmmoType.fromStack(stack).serializedName();
    }
}
