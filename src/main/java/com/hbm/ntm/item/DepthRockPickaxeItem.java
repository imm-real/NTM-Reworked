package com.hbm.ntm.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Tool;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import java.util.List;

/** Unbreakable bismuth pickaxe, shovel and licensed depth-rock disagreement tool. */
public final class DepthRockPickaxeItem extends Item implements DepthRockTool {
    public DepthRockPickaxeItem() {
        super(new Properties().stacksTo(1)
                .component(DataComponents.TOOL, new Tool(List.of(
                        Tool.Rule.deniesDrops(ModToolTiers.BISMUTH.getIncorrectBlocksForDrops()),
                        Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_PICKAXE, ModToolTiers.BISMUTH.getSpeed()),
                        Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_SHOVEL, ModToolTiers.BISMUTH.getSpeed())
                ), 1.0F, 1))
                .attributes(DiggerItem.createAttributes(ModToolTiers.BISMUTH, 15.0F, -2.8F)));
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        return ItemAbilities.DEFAULT_PICKAXE_ACTIONS.contains(ability)
                || ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(ability);
    }

    @Override
    public int getEnchantmentValue() {
        return ModToolTiers.BISMUTH.getEnchantmentValue();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hbm.bismuth_pickaxe.depth").withStyle(ChatFormatting.RED));
    }
}
