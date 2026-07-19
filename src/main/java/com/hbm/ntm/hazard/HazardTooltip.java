package com.hbm.ntm.hazard;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class HazardTooltip {
    private HazardTooltip() {
    }

    public static void append(List<Component> tooltip, HazardProfile hazards, ItemStack stack) {
        int count = stack.getCount();
        if (hazards.radiation() >= 1.0E-5F) {
            tooltip.add(Component.translatable("trait.radioactive.bracketed").withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.literal(format(hazards.radiation()) + "RAD/s").withStyle(ChatFormatting.YELLOW));
            if (count > 1) {
                tooltip.add(Component.translatable("tooltip.hbm.radiation.stack", format(hazards.radiation() * count))
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
        if (hazards.heat() > 0) tooltip.add(Component.translatable("trait.hot.bracketed").withStyle(ChatFormatting.GOLD));
        if (hazards.hydroactive() > 0) tooltip.add(Component.translatable("trait.hydroactive.bracketed").withStyle(ChatFormatting.RED));
        if (hazards.explosive() > 0) tooltip.add(Component.translatable("trait.explosive.bracketed").withStyle(ChatFormatting.RED));
        if (hazards.coalDust() > 0) tooltip.add(Component.translatable("trait.coal_dust.bracketed").withStyle(ChatFormatting.DARK_GRAY));
        if (hazards.asbestos() > 0) tooltip.add(Component.translatable("trait.asbestos.bracketed").withStyle(ChatFormatting.WHITE));
        if (hazards.blinding() > 0) tooltip.add(Component.translatable("trait.blinding.bracketed").withStyle(ChatFormatting.DARK_AQUA));
        if (hazards.digamma() > 0) {
            tooltip.add(Component.translatable("trait.digamma.bracketed").withStyle(ChatFormatting.RED));
            tooltip.add(Component.literal(formatDigamma(hazards.digamma()) + "mDRX/s").withStyle(ChatFormatting.DARK_RED));
            if (count > 1) {
                tooltip.add(Component.translatable("tooltip.hbm.digamma.stack", formatDigamma(hazards.digamma() * count))
                        .withStyle(ChatFormatting.DARK_RED));
            }
        }
    }

    private static String format(float value) {
        return Double.toString(Math.floor(value * 1000.0D) / 1000.0D);
    }

    private static String formatDigamma(float value) {
        return Float.toString((float) Math.floor(value * 10_000.0F) / 10.0F);
    }
}
