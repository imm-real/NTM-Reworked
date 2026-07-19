package com.hbm.ntm.item;

import com.hbm.ntm.hazard.HazardCarrier;
import com.hbm.ntm.hazard.HazardProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class RadioactiveItem extends Item implements HazardCarrier {
    private final HazardProfile hazards;

    public RadioactiveItem(Properties properties, float radiation) {
        super(properties);
        this.hazards = HazardProfile.radiation(radiation);
    }

    @Override
    public HazardProfile hbm$getHazards(ItemStack stack) {
        return hazards;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        appendRadiationTooltip(tooltip, hazards.radiation(), stack.getCount());
    }

    public static void appendRadiationTooltip(List<Component> tooltip, float radiation, int count) {
        if (radiation < 1.0E-5F) {
            return;
        }
        tooltip.add(Component.translatable("trait.radioactive.bracketed").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(format(radiation) + "RAD/s").withStyle(ChatFormatting.YELLOW));
        if (count > 1) {
            tooltip.add(Component.translatable("tooltip.hbm.radiation.stack", format(radiation * count))
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static String format(float value) {
        return Double.toString(Math.floor(value * 1000.0D) / 1000.0D);
    }
}
