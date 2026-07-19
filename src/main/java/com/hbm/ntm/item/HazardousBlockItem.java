package com.hbm.ntm.item;

import com.hbm.ntm.hazard.HazardCarrier;
import com.hbm.ntm.hazard.HazardProfile;
import com.hbm.ntm.hazard.HazardTooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class HazardousBlockItem extends BlockItem implements HazardCarrier {
    private final HazardProfile hazards;

    public HazardousBlockItem(Block block, Properties properties, HazardProfile hazards) {
        super(block, properties);
        this.hazards = hazards;
    }

    @Override
    public HazardProfile hbm$getHazards(ItemStack stack) {
        return hazards;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        HazardTooltip.append(tooltip, hazards, stack);
    }
}
