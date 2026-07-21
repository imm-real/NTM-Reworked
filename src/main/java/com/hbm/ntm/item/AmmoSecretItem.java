package com.hbm.ntm.item;

import com.hbm.ntm.weapon.SecretAmmoTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class AmmoSecretItem extends Item {
    public AmmoSecretItem() {
        super(new Properties());
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return "item.hbm.ammo_secret." + SecretAmmoTypes.fromStack(stack).serializedName();
    }
}
