package com.hbm.ntm.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class InfiniteBatteryItem extends Item implements HeBatteryItem {
    public InfiniteBatteryItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public long getCharge(ItemStack stack) {
        return Long.MAX_VALUE / 2L;
    }

    @Override
    public long getMaxCharge(ItemStack stack) {
        return Long.MAX_VALUE;
    }

    @Override
    public long getChargeRate(ItemStack stack) {
        return Long.MAX_VALUE / 100L;
    }

    @Override
    public long getDischargeRate(ItemStack stack) {
        return Long.MAX_VALUE / 100L;
    }

    @Override
    public void charge(ItemStack stack, long amount) {
    }

    @Override
    public void setCharge(ItemStack stack, long amount) {
    }

    @Override
    public void discharge(ItemStack stack, long amount) {
    }
}
