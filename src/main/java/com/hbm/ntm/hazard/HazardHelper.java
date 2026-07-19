package com.hbm.ntm.hazard;

import net.minecraft.world.item.ItemStack;

public final class HazardHelper {
    private HazardHelper() {
    }

    public static HazardProfile get(ItemStack stack) {
        if (stack.isEmpty()) {
            return HazardProfile.NONE;
        }
        HazardProfile direct = HazardRegistry.get(stack);
        return stack.getItem() instanceof HazardCarrier carrier
                ? direct.add(carrier.hbm$getHazards(stack))
                : direct;
    }
}
