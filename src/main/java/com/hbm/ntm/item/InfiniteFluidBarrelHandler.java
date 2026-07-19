package com.hbm.ntm.item;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/**
 * Capability adapter for a barrel that is both type-less source and sink.
 * Type-aware consumers use {@link #drain(FluidStack, FluidAction)}; an untyped drain
 * cannot choose a fluid and therefore deliberately returns empty.
 */
public final class InfiniteFluidBarrelHandler implements IFluidHandlerItem {
    private final ItemStack container;

    public InfiniteFluidBarrelHandler(ItemStack container) {
        this.container = container;
    }

    @Override public ItemStack getContainer() { return container; }
    @Override public int getTanks() { return 1; }
    @Override public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
    @Override public int getTankCapacity(int tank) {
        return tank == 0 ? InfiniteFluidBarrelItem.TRANSFER_AMOUNT : 0;
    }
    @Override public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 && !stack.isEmpty();
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return resource.isEmpty() ? 0
                : Math.min(resource.getAmount(), InfiniteFluidBarrelItem.TRANSFER_AMOUNT);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        return resource.copyWithAmount(Math.min(resource.getAmount(),
                InfiniteFluidBarrelItem.TRANSFER_AMOUNT));
    }

    @Override public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
}
