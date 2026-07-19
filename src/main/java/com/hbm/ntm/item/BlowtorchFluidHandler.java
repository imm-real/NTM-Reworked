package com.hbm.ntm.item;

import com.hbm.ntm.registry.ModFluids;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/** Four buckets of gas, accepting fifty millibuckets per impatient fill. */
public final class BlowtorchFluidHandler implements IFluidHandlerItem {
    private final ItemStack container;

    public BlowtorchFluidHandler(ItemStack container) {
        this.container = container;
    }

    @Override public ItemStack getContainer() { return container; }
    @Override public int getTanks() { return 1; }
    @Override public FluidStack getFluidInTank(int tank) {
        int amount = tank == 0 ? BlowtorchItem.gas(container) : 0;
        return amount > 0 ? new FluidStack(ModFluids.GAS.get(), amount) : FluidStack.EMPTY;
    }
    @Override public int getTankCapacity(int tank) { return tank == 0 ? BlowtorchItem.CAPACITY : 0; }
    @Override public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 && !stack.isEmpty() && stack.getFluid().isSame(ModFluids.GAS.get());
    }

    @Override public int fill(FluidStack resource, FluidAction action) {
        if (container.getCount() != 1 || !isFluidValid(0, resource)) return 0;
        int accepted = Math.min(50, Math.min(resource.getAmount(), BlowtorchItem.CAPACITY - BlowtorchItem.gas(container)));
        if (accepted > 0 && action.execute()) BlowtorchItem.setGas(container, BlowtorchItem.gas(container) + accepted);
        return accepted;
    }

    @Override public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
    @Override public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
}
