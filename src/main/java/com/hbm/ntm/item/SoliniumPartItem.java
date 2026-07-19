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

/** Blue Rinse parts. Igniter, propellant and the part that ruins eyesight. */
public final class SoliniumPartItem extends Item implements HazardCarrier {
    private final HazardProfile hazards;

    public SoliniumPartItem(Properties properties, HazardProfile hazards) {
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
        tooltip.add(ModBlocks.NUKE_SOLINIUM.get().getName());
        HazardTooltip.append(tooltip, hazards, stack);
    }
}
