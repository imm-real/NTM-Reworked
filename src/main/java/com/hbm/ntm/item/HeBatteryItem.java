package com.hbm.ntm.item;

import net.minecraft.world.item.ItemStack;

public interface HeBatteryItem {
    long getCharge(ItemStack stack);

    long getMaxCharge(ItemStack stack);

    long getChargeRate(ItemStack stack);

    long getDischargeRate(ItemStack stack);

    void charge(ItemStack stack, long amount);

    void setCharge(ItemStack stack, long amount);

    void discharge(ItemStack stack, long amount);
}
