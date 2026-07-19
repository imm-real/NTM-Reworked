package com.hbm.ntm.item;

import com.hbm.ntm.config.HbmConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** Explains which three fuels the diesel generator judges most harshly. */
public final class DieselGeneratorBlockItem extends BlockItem {
    public DieselGeneratorBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Fuel efficiency:").withStyle(ChatFormatting.YELLOW));
        efficiency(tooltip, "Medium", HbmConfig.DIESEL_EFFICIENCY_MEDIUM.get());
        efficiency(tooltip, "High", HbmConfig.DIESEL_EFFICIENCY_HIGH.get());
        efficiency(tooltip, "Aviation", HbmConfig.DIESEL_EFFICIENCY_AERO.get());
    }

    private static void efficiency(List<Component> tooltip, String grade, double value) {
        tooltip.add(Component.literal("-" + grade + ": ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal((int) (value * 100D) + "%").withStyle(ChatFormatting.RED)));
    }
}
