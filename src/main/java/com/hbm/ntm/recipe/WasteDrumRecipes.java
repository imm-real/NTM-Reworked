package com.hbm.ntm.recipe;

import com.hbm.ntm.item.WasteDrumCoolable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Waste-drum recipe boundary, waiting for the missing fuel-pool families. */
public final class WasteDrumRecipes {
    public static final int ONE_WATER_ROLL_BOUND = 60 * 60 * 20;
    public static final double ROD_DIFFUSION = 0.025D;
    public static final double ROD_MAX_TRANSFER = 20.0D;

    private WasteDrumRecipes() { }

    public static boolean isInput(ItemStack stack) {
        return stack.getItem() instanceof WasteDrumCoolable coolable
                && coolable.isWasteDrumInput(stack);
    }

    public static boolean coolEveryTick(ItemStack stack) {
        return stack.getItem() instanceof WasteDrumCoolable coolable
                && coolable.isWasteDrumInput(stack)
                && coolable.coolEverySubmergedTick(stack);
    }

    public static void coolContinuous(ServerLevel level, ItemStack stack) {
        if (stack.getItem() instanceof WasteDrumCoolable coolable) {
            coolable.coolInWasteDrum(level, stack, ROD_DIFFUSION, ROD_MAX_TRANSFER);
        }
    }

    public static ItemStack cooledResult(ItemStack stack) {
        return stack.getItem() instanceof WasteDrumCoolable coolable
                ? coolable.wasteDrumResult(stack) : stack.copy();
    }

    public static boolean mayExtract(ItemStack stack) {
        if (!(stack.getItem() instanceof WasteDrumCoolable coolable)) return true;
        return coolable.canExtractFromWasteDrum(stack);
    }

    public static int rollBound(int adjacentWaterBlocks) {
        if (adjacentWaterBlocks <= 0) return Integer.MAX_VALUE;
        return ONE_WATER_ROLL_BOUND / adjacentWaterBlocks;
    }
}
