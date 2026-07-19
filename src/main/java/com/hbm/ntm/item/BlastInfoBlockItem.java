package com.hbm.ntm.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public final class BlastInfoBlockItem extends BlockItem {
    private final float effectiveResistance;

    public BlastInfoBlockItem(Block block, Properties properties, float effectiveResistance) {
        super(block, properties);
        this.effectiveResistance = effectiveResistance;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.add(Component.literal("Blast Resistance: " + effectiveResistance).withStyle(ChatFormatting.GOLD));
    }
}
