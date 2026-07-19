package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.DfcComponentBlock;
import com.hbm.ntm.dfc.DfcKind;
import com.hbm.ntm.dfc.DfcTank;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.SourceFluidContainerItem;
import com.hbm.ntm.item.InfiniteFluidBarrelItem;
import com.hbm.ntm.item.UniversalFluidTankItem;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class DfcInjectorBlockEntity extends DfcBlockEntity {
    public static final int TANK_CAPACITY = 128_000;
    public static final int RANGE = 15;
    public static final int INPUT_0 = 0;
    public static final int OUTPUT_0 = 1;
    public static final int INPUT_1 = 2;
    public static final int OUTPUT_1 = 3;

    private final DfcTank tank0 = new DfcTank(ModFluids.DEUTERIUM.get(), TANK_CAPACITY,
            fluid -> DfcCoreBlockEntity.fuelEfficiency(fluid) > 0.0F, this::setChanged);
    private final DfcTank tank1 = new DfcTank(ModFluids.TRITIUM.get(), TANK_CAPACITY,
            fluid -> DfcCoreBlockEntity.fuelEfficiency(fluid) > 0.0F, this::setChanged);
    private int beam;
    private long lastSync = Long.MIN_VALUE;

    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return 2; }
        @Override public FluidStack getFluidInTank(int tank) {
            return tank >= 0 && tank < 2 ? DfcInjectorBlockEntity.this.tank(tank).getFluidInTank(0) : FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int tank) { return tank >= 0 && tank < 2 ? TANK_CAPACITY : 0; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return tank >= 0 && tank < 2 && DfcInjectorBlockEntity.this.tank(tank).isFluidValid(0, stack);
        }
        @Override public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            for (DfcTank tank : new DfcTank[]{tank0, tank1}) {
                if (tank.amount() > 0 && tank.fluid().isSame(resource.getFluid())) return tank.fill(resource, action);
            }
            for (DfcTank tank : new DfcTank[]{tank0, tank1}) {
                if (tank.amount() == 0) return tank.fill(resource, action);
            }
            return 0;
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    };

    public DfcInjectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DFC_INJECTOR.get(), pos, state, DfcKind.INJECTOR);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DfcInjectorBlockEntity injector) {
        if (level instanceof ServerLevel server) injector.serverTick(server, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        loadContainer(INPUT_0, OUTPUT_0, tank0);
        loadContainer(INPUT_1, OUTPUT_1, tank1);
        beam = 0;
        Direction direction = state.getValue(DfcComponentBlock.FACING);
        for (int i = 1; i <= RANGE; i++) {
            BlockPos target = pos.relative(direction, i);
            if (level.getBlockEntity(target) instanceof DfcCoreBlockEntity core) {
                transfer(tank0, core.tank(0));
                transfer(tank1, core.tank(1));
                beam = i;
                break;
            }
            if (!level.getBlockState(target).isAir()) break;
        }
        sync(level, pos, state);
        setChanged();
    }

    private static void transfer(DfcTank from, DfcTank to) {
        if (from.amount() <= 0) return;
        if (!to.fluid().isSame(from.fluid()) && !to.setFluidIfEmpty(from.fluid())) return;
        int moved = Math.min(from.amount(), to.capacity() - to.amount());
        if (moved > 0) {
            from.remove(moved);
            to.add(from.fluid(), moved);
        }
    }

    private void loadContainer(int inputSlot, int outputSlot, DfcTank tank) {
        ItemStack input = items.get(inputSlot);
        if (input.isEmpty()) return;
        if (InfiniteFluidBarrelItem.is(input)) {
            if (DfcCoreBlockEntity.fuelEfficiency(tank.fluid()) <= 0.0F) return;
            int moved = Math.min(InfiniteFluidBarrelItem.TRANSFER_AMOUNT, tank.capacity() - tank.amount());
            if (moved > 0) {
                tank.add(tank.fluid(), moved);
                setChanged();
            }
            return;
        }
        Fluid fluid;
        ItemStack remainder;
        if (input.getItem() instanceof UniversalFluidTankItem) {
            UniversalFluidTankItem.ContainedFluid contained = UniversalFluidTankItem.fluid(input);
            if (contained == UniversalFluidTankItem.ContainedFluid.NONE) return;
            fluid = contained.fluid();
            remainder = new ItemStack(ModItems.FLUID_TANK_EMPTY.get());
        } else if (input.is(ModItems.GAS_FULL.get()) || input.is(ModItems.CANISTER_FULL.get())) {
            SourceFluidContainerItem.ContainedFluid contained = SourceFluidContainerItem.fluid(input);
            if (contained == SourceFluidContainerItem.ContainedFluid.NONE) return;
            fluid = contained.fluid();
            remainder = new ItemStack(input.is(ModItems.GAS_FULL.get())
                    ? ModItems.GAS_EMPTY.get() : ModItems.CANISTER_EMPTY.get());
        } else return;
        if (DfcCoreBlockEntity.fuelEfficiency(fluid) <= 0.0F || !canMerge(items.get(outputSlot), remainder)) return;
        FluidStack load = new FluidStack(fluid, 1_000);
        if (tank.fill(load, IFluidHandler.FluidAction.SIMULATE) != 1_000) return;
        tank.fill(load, IFluidHandler.FluidAction.EXECUTE);
        input.shrink(1);
        if (input.isEmpty()) items.set(inputSlot, ItemStack.EMPTY);
        if (items.get(outputSlot).isEmpty()) items.set(outputSlot, remainder);
        else items.get(outputSlot).grow(1);
        setChanged();
    }

    private static boolean canMerge(ItemStack target, ItemStack addition) {
        return target.isEmpty() || ItemStack.isSameItemSameComponents(target, addition)
                && target.getCount() < target.getMaxStackSize();
    }

    public DfcTank tank(int index) { return index == 0 ? tank0 : tank1; }
    public int beam() { return beam; }
    public IFluidHandler fluidHandler(@Nullable Direction side) { return fluidHandler; }

    private void sync(ServerLevel level, BlockPos pos, BlockState state) {
        long signature = tank0.amount() ^ ((long) tank1.amount() << 18) ^ ((long) beam << 48)
                ^ net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(tank0.fluid())
                ^ ((long) net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(tank1.fluid()) << 32);
        if (signature != lastSync || level.getGameTime() % 20L == 0L) {
            lastSync = signature;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override protected int menuValue(int index) {
        return switch (index) {
            case 5 -> beam;
            case 9 -> tank0.amount();
            case 10 -> FluidIdentifierItem.Selection.fromFluid(tank0.fluid()).ordinal();
            case 11 -> tank1.amount();
            case 12 -> FluidIdentifierItem.Selection.fromFluid(tank1.fluid()).ordinal();
            default -> 0;
        };
    }

    @Override protected void saveDfcState(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.put("tank0", tank0.save(registries));
        tag.put("tank1", tank1.save(registries));
        tag.putInt("beam", beam);
    }

    @Override protected void loadDfcState(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        if (tag.contains("tank0")) tank0.load(tag.getCompound("tank0"), registries);
        if (tag.contains("tank1")) tank1.load(tag.getCompound("tank1"), registries);
        beam = tag.getInt("beam");
    }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return (slot == INPUT_0 || slot == INPUT_1)
                && (InfiniteFluidBarrelItem.is(stack) || containerFluid(stack) != null);
    }

    private static @Nullable Fluid containerFluid(ItemStack stack) {
        if (stack.getItem() instanceof UniversalFluidTankItem) {
            var contained = UniversalFluidTankItem.fluid(stack);
            return contained == UniversalFluidTankItem.ContainedFluid.NONE ? null : contained.fluid();
        }
        if (stack.is(ModItems.GAS_FULL.get()) || stack.is(ModItems.CANISTER_FULL.get())) {
            var contained = SourceFluidContainerItem.fluid(stack);
            return contained == SourceFluidContainerItem.ContainedFluid.NONE ? null : contained.fluid();
        }
        return null;
    }
}
