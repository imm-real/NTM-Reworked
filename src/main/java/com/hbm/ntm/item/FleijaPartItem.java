package com.hbm.ntm.item;

import com.hbm.ntm.hazard.HazardCarrier;
import com.hbm.ntm.hazard.HazardProfile;
import com.hbm.ntm.hazard.HazardTooltip;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** One third of F.L.E.I.J.A. and possibly several thirds of your yearly dose. */
public final class FleijaPartItem extends Item implements HazardCarrier {
    private final HazardProfile hazards;

    public FleijaPartItem(Properties properties, HazardProfile hazards) {
        super(properties);
        this.hazards = hazards;
    }

    @Override
    public HazardProfile hbm$getHazards(ItemStack stack) {
        return hazards;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bomb_part.used_in"));
        tooltip.add(ModBlocks.NUKE_FLEIJA.get().getName());
        HazardTooltip.append(tooltip, hazards, stack);
    }
}
