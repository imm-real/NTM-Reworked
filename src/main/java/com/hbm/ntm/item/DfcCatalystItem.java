package com.hbm.ntm.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class DfcCatalystItem extends Item {
    private final int color;
    private final long powerAbsorption;
    private final float powerModifier;
    private final float heatModifier;
    private final float fuelModifier;

    public DfcCatalystItem(int color, long powerAbsorption, float powerModifier,
                           float heatModifier, float fuelModifier) {
        super(new Properties().stacksTo(1));
        this.color = color;
        this.powerAbsorption = powerAbsorption;
        this.powerModifier = powerModifier;
        this.heatModifier = heatModifier;
        this.fuelModifier = fuelModifier;
    }

    public int color() { return color; }
    public long powerAbsorption() { return powerAbsorption; }
    public float powerModifier() { return powerModifier; }
    public float heatModifier() { return heatModifier; }
    public float fuelModifier() { return fuelModifier; }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
                                          List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Adds spice to the core."));
        tooltip.add(Component.literal("Look at all those colors!"));
    }
}
