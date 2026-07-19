package com.hbm.ntm.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** Turbofan tooltip reminding kerosene who the favorite child is. */
public final class TurbofanBlockItem extends BlockItem {
    public TurbofanBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Fuel efficiency:").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("-Aviation: ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("100%").withStyle(ChatFormatting.RED)));
    }
}
