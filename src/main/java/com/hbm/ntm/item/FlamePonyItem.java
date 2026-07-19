package com.hbm.ntm.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Intentionally excessive turbofan afterburner catalyst. Friendship is thrust. */
public final class FlamePonyItem extends Item {
    public FlamePonyItem() {
        super(new Properties());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hbm.flame_pony.desc"));
    }
}
