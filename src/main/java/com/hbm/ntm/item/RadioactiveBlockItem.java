package com.hbm.ntm.item;

import com.hbm.ntm.hazard.HazardCarrier;
import com.hbm.ntm.hazard.HazardProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public final class RadioactiveBlockItem extends BlockItem implements HazardCarrier {
    private final HazardProfile hazards;

    public RadioactiveBlockItem(Block block, Properties properties, float radiation) {
        super(block, properties);
        this.hazards = HazardProfile.radiation(radiation);
    }

    @Override
    public HazardProfile hbm$getHazards(ItemStack stack) {
        return hazards;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RadioactiveItem.appendRadiationTooltip(tooltip, hazards.radiation(), stack.getCount());
    }
}
