package com.hbm.ntm.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Source polymorphic infinite barrel: it creates or voids the fluid selected by the host tank. */
public final class InfiniteFluidBarrelItem extends Item {
    public static final int TRANSFER_AMOUNT = 1_000_000_000;

    public InfiniteFluidBarrelItem() {
        super(new Properties().stacksTo(1));
    }

    public static boolean is(ItemStack stack) {
        return stack.getItem() instanceof InfiniteFluidBarrelItem;
    }

    public static int fillTank(FluidTank tank, Fluid fluid) {
        if (fluid.isSame(Fluids.EMPTY) || tank.getSpace() <= 0) return 0;
        int amount = Math.min(TRANSFER_AMOUNT, tank.getSpace());
        return tank.fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE);
    }

    public static int discardTank(FluidTank tank) {
        if (tank.isEmpty()) return 0;
        return tank.drain(Math.min(TRANSFER_AMOUNT, tank.getFluidAmount()),
                IFluidHandler.FluidAction.EXECUTE).getAmount();
    }
}
