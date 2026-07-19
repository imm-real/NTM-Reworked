package com.hbm.ntm.hazard;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public interface HazardProtectiveItem {
    boolean hbm$protects(ItemStack stack, LivingEntity wearer, HazardProtection protection);

    default void hbm$damageProtection(
            ItemStack stack,
            LivingEntity wearer,
            HazardProtection protection,
            int amount
    ) {
    }
}
