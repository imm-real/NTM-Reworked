package com.hbm.ntm.item;

import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Large Explosive Charge. Collect all twelve! */
public final class N2ChargeItem extends Item {
    public N2ChargeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bomb_part.used_in"));
        tooltip.add(ModBlocks.NUKE_N2.get().getName());
    }
}
