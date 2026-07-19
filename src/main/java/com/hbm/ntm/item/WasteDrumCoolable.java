package com.hbm.ntm.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Things willing to cool down in a spent-fuel drum. */
public interface WasteDrumCoolable {
    /** Whether the drum accepts this particular glowing stick. */
    boolean isWasteDrumInput(ItemStack stack);

    /** RBMK rods demand constant attention; other waste rolls hourly. */
    default boolean coolEverySubmergedTick(ItemStack stack) { return false; }

    /** Remove heat slowly, provide twenty units of wet disappointment. */
    default void coolInWasteDrum(ServerLevel level, ItemStack stack,
                                 double diffusion, double maximumTransfer) { }

    /** Reward for winning the water-scaled waiting lottery. */
    default ItemStack wasteDrumResult(ItemStack stack) { return stack.copy(); }

    /** Core and hull below fifty, or it stays in the pool. */
    boolean canExtractFromWasteDrum(ItemStack stack);
}
