package com.hbm.ntm.dfc;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.Objects;
import java.util.function.Predicate;

/** Typed tank that remembers its selected fluid while empty. */
public final class DfcTank implements IFluidHandler {
    private final int capacity;
    private final Predicate<Fluid> validator;
    private final Runnable changed;
    private Fluid fluid;
    private int amount;

    public DfcTank(Fluid initialFluid, int capacity, Predicate<Fluid> validator, Runnable changed) {
        this.fluid = Objects.requireNonNull(initialFluid);
        this.capacity = capacity;
        this.validator = validator;
        this.changed = changed;
    }

    public Fluid fluid() { return fluid; }
    public int amount() { return amount; }
    public int capacity() { return capacity; }
    public boolean isEmpty() { return amount <= 0; }

    public boolean setFluidIfEmpty(Fluid next) {
        if (amount != 0 || next == null || next == Fluids.EMPTY || !validator.test(next)) return false;
        if (fluid.isSame(next)) return true;
        fluid = next;
        changed.run();
        return true;
    }

    public int add(Fluid next, int requested) {
        if (requested <= 0 || next == null || next == Fluids.EMPTY || !validator.test(next)) return 0;
        if (amount == 0) fluid = next;
        if (!fluid.isSame(next)) return 0;
        int accepted = Math.min(requested, capacity - amount);
        if (accepted > 0) {
            amount += accepted;
            changed.run();
        }
        return accepted;
    }

    public int remove(int requested) {
        int removed = Math.min(Math.max(requested, 0), amount);
        if (removed > 0) {
            amount -= removed;
            changed.run();
        }
        return removed;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        if (id != null) tag.putString("fluid", id.toString());
        tag.putInt("amount", amount);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("fluid"));
        Fluid loaded = id == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.get(id);
        if (loaded != Fluids.EMPTY && validator.test(loaded)) fluid = loaded;
        amount = Math.clamp(tag.getInt("amount"), 0, capacity);
        if (amount > 0 && (loaded == Fluids.EMPTY || !validator.test(loaded))) amount = 0;
    }

    @Override public int getTanks() { return 1; }
    @Override public FluidStack getFluidInTank(int tank) {
        return tank == 0 && amount > 0 ? new FluidStack(fluid, amount) : FluidStack.EMPTY;
    }
    @Override public int getTankCapacity(int tank) { return tank == 0 ? capacity : 0; }
    @Override public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 && !stack.isEmpty() && validator.test(stack.getFluid())
                && (amount == 0 || fluid.isSame(stack.getFluid()));
    }
    @Override public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !validator.test(resource.getFluid())
                || amount > 0 && !fluid.isSame(resource.getFluid())) return 0;
        int accepted = Math.min(resource.getAmount(), capacity - amount);
        if (accepted > 0 && action.execute()) add(resource.getFluid(), accepted);
        return accepted;
    }
    @Override public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !fluid.isSame(resource.getFluid())) return FluidStack.EMPTY;
        return drain(resource.getAmount(), action);
    }
    @Override public FluidStack drain(int maxDrain, FluidAction action) {
        int removed = Math.min(Math.max(maxDrain, 0), amount);
        if (removed <= 0) return FluidStack.EMPTY;
        FluidStack result = new FluidStack(fluid, removed);
        if (action.execute()) remove(removed);
        return result;
    }
}
