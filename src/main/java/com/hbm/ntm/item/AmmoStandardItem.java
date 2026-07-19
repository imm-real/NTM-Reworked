package com.hbm.ntm.item;

import com.hbm.ntm.weapon.StandardAmmoTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class AmmoStandardItem extends Item {
    public AmmoStandardItem() {
        super(new Properties());
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return "item.hbm.ammo_standard." + StandardAmmoTypes.fromStack(stack).serializedName();
    }
}
