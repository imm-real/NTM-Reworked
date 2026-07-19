package com.hbm.ntm.item;

import com.hbm.ntm.weapon.FiftyCalAmmoType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Original secret-ammunition container, initially used by the XFactory50 legendary rounds. */
public final class AmmoSecretItem extends Item {
    public AmmoSecretItem() {
        super(new Properties());
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return "item.hbm.ammo_secret." + FiftyCalAmmoType.fromStack(stack).serializedName();
    }
}
