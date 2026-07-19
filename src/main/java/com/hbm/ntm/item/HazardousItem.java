package com.hbm.ntm.item;

import com.hbm.ntm.hazard.HazardCarrier;
import com.hbm.ntm.hazard.HazardProfile;
import com.hbm.ntm.hazard.HazardTooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class HazardousItem extends Item implements HazardCarrier {
    private final HazardProfile hazards;

    public HazardousItem(Properties properties, HazardProfile hazards) {
        super(properties);
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
