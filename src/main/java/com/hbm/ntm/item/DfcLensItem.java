package com.hbm.ntm.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

public final class DfcLensItem extends Item {
    public static final long MAX_DAMAGE = 432_000_000L;
    private static final String DAMAGE = "damage";

    public DfcLensItem() { super(new Properties().stacksTo(1)); }

    public static long damage(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return Math.clamp(tag.getLong(DAMAGE), 0L, MAX_DAMAGE);
    }

    public static void setDamage(ItemStack stack, long damage) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putLong(DAMAGE, Math.clamp(damage, 0L, MAX_DAMAGE));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
                                          List<Component> tooltip, TooltipFlag flag) {
        long remaining = MAX_DAMAGE - damage(stack);
        int percent = (int) (remaining * 100L / MAX_DAMAGE);
        tooltip.add(Component.literal("Durability: " + remaining + "/" + MAX_DAMAGE + " (" + percent + "%)"));
    }

    @Override public boolean isBarVisible(ItemStack stack) { return damage(stack) != 0L; }
    @Override public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * (MAX_DAMAGE - damage(stack)) / MAX_DAMAGE);
    }
    @Override public int getBarColor(ItemStack stack) {
        float remaining = (float) (MAX_DAMAGE - damage(stack)) / MAX_DAMAGE;
        return net.minecraft.util.Mth.hsvToRgb(remaining / 3.0F, 1.0F, 1.0F);
    }
}
