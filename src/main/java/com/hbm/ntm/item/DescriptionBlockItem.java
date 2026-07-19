package com.hbm.ntm.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class DescriptionBlockItem extends BlockItem {
    private final String[] descriptionKeys;

    public DescriptionBlockItem(Block block, Properties properties, String... descriptionKeys) {
        super(block, properties);
        this.descriptionKeys = descriptionKeys;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        for (String key : descriptionKeys) {
            tooltip.add(Component.translatable(key));
        }
    }
}
