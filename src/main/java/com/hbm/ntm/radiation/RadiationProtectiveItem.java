package com.hbm.ntm.radiation;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public interface RadiationProtectiveItem {
    float hbm$getRadiationResistance(ItemStack stack, LivingEntity wearer);
}
