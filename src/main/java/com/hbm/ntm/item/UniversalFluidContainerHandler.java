package com.hbm.ntm.item;

import com.hbm.ntm.registry.ModItems;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/** One-bucket capability for HBM's metadata-era universal full/empty tank items. */
public final class UniversalFluidContainerHandler implements IFluidHandlerItem {
    public static final int CAPACITY = 1_000;
    private ItemStack container;
    private UniversalFluidTankItem.ContainedFluid fluid;

    public UniversalFluidContainerHandler(ItemStack container, boolean full) {
        this.container = container;
        fluid = full ? UniversalFluidTankItem.fluid(container) : UniversalFluidTankItem.ContainedFluid.NONE;
    }

    @Override public ItemStack getContainer() { return container; }
    @Override public int getTanks() { return 1; }
    @Override public FluidStack getFluidInTank(int tank) {
        return tank == 0 && fluid != UniversalFluidTankItem.ContainedFluid.NONE
                ? new FluidStack(fluid.fluid(), CAPACITY) : FluidStack.EMPTY;
    }
    @Override public int getTankCapacity(int tank) { return tank == 0 ? CAPACITY : 0; }
    @Override public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 && fluid == UniversalFluidTankItem.ContainedFluid.NONE
                && UniversalFluidTankItem.ContainedFluid.fromFluid(stack.getFluid()) != null;
    }

    @Override public int fill(FluidStack resource, FluidAction action) {
        UniversalFluidTankItem.ContainedFluid type = UniversalFluidTankItem.ContainedFluid.fromFluid(resource.getFluid());
        if (fluid != UniversalFluidTankItem.ContainedFluid.NONE || container.getCount() != 1
                || resource.getAmount() < CAPACITY || type == null
                || type == UniversalFluidTankItem.ContainedFluid.NONE) return 0;
        if (action.execute()) {
            fluid = type;
            container = UniversalFluidTankItem.create(ModItems.FLUID_TANK_FULL.get(), type, 1);
        }
        return CAPACITY;
    }

    @Override public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || resource.getAmount() < CAPACITY
                || fluid == UniversalFluidTankItem.ContainedFluid.NONE
                || !resource.getFluid().isSame(fluid.fluid())) return FluidStack.EMPTY;
        return drain(CAPACITY, action);
    }

    @Override public FluidStack drain(int maxDrain, FluidAction action) {
        if (fluid == UniversalFluidTankItem.ContainedFluid.NONE || container.getCount() != 1
                || maxDrain < CAPACITY) return FluidStack.EMPTY;
        FluidStack result = new FluidStack(fluid.fluid(), CAPACITY);
        if (action.execute()) {
            fluid = UniversalFluidTankItem.ContainedFluid.NONE;
            container = new ItemStack(ModItems.FLUID_TANK_EMPTY.get());
        }
        return result;
    }
}
