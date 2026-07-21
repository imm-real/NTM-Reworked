package com.hbm.ntm.blockentity;

import com.hbm.ntm.energy.HeReceiver;
import com.hbm.ntm.inventory.RefineryMenu;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.item.SourceFluidContainerItem;
import com.hbm.ntm.item.InfiniteFluidBarrelItem;
import com.hbm.ntm.item.UniversalFluidTankItem;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/** Turns hot oil into several smaller, more marketable problems. */
public final class RefineryBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider, HeReceiver {
    public static final String ITEM_SELECTED_FLUID = "refinery_selected_fluid";
    public static final String ITEM_INPUT = "refinery_input";
    public static final String ITEM_HEAVY = "refinery_heavy";
    public static final String ITEM_NAPHTHA = "refinery_naphtha";
    public static final String ITEM_LIGHT = "refinery_light";
    public static final String ITEM_PETROLEUM = "refinery_petroleum";
    public static final int BATTERY = 0;
    public static final int INPUT_CONTAINER = 1;
    public static final int INPUT_REMAINDER = 2;
    public static final int HEAVY_EMPTY = 3;
    public static final int HEAVY_FULL = 4;
    public static final int NAPHTHA_EMPTY = 5;
    public static final int NAPHTHA_FULL = 6;
    public static final int LIGHT_EMPTY = 7;
    public static final int LIGHT_FULL = 8;
    public static final int PETROLEUM_EMPTY = 9;
    public static final int PETROLEUM_FULL = 10;
    public static final int SULFUR_OUTPUT = 11;
    public static final int FLUID_IDENTIFIER = 12;
    public static final int SLOT_COUNT = 13;
    public static final int INPUT_CAPACITY = 64_000;
    public static final int OUTPUT_CAPACITY = 24_000;
    public static final long POWER_CAPACITY = 1_000L;
    public static final int INPUT_PER_OPERATION = 100;
    public static final long POWER_PER_OPERATION = 5L;
    private static final int[] SULFUR_ONLY = {SULFUR_OUTPUT};
    private static final int[] OUTPUT_AMOUNTS = {50, 25, 15, 10};

    /** The exact per-operation Heavy Oil/Naphtha/Light Oil/Petroleum split, exposed read-only for JEI. */
    public static int[] outputAmounts() {
        return OUTPUT_AMOUNTS.clone();
    }

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private FluidIdentifierItem.Selection selectedFluid = FluidIdentifierItem.Selection.HOTOIL;
    private final FluidTank input = new FluidTank(INPUT_CAPACITY,
            stack -> selectedFluid.accepts(stack.getFluid())) {
        @Override protected void onContentsChanged() { RefineryBlockEntity.this.setChanged(); }
    };
    private final FluidTank[] outputs = {
            outputTank(ModFluids.HEAVYOIL.get()), outputTank(ModFluids.NAPHTHA.get()),
            outputTank(ModFluids.LIGHTOIL.get()), outputTank(ModFluids.PETROLEUM.get())
    };
    private final IFluidHandler inputHandler = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return input.getFluid(); }
        @Override public int getTankCapacity(int tank) { return INPUT_CAPACITY; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return selectedFluid.accepts(stack.getFluid());
        }
        @Override public int fill(FluidStack stack, FluidAction action) {
            return isFluidValid(0, stack) ? input.fill(stack, action) : 0;
        }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int amount, FluidAction action) { return FluidStack.EMPTY; }
    };
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int) power;
                case 1 -> (int) (power >>> 32);
                case 2 -> (int) POWER_CAPACITY;
                case 3 -> (int) (POWER_CAPACITY >>> 32);
                case 4 -> input.getFluidAmount();
                case 5, 6, 7, 8 -> outputs[index - 5].getFluidAmount();
                case 9 -> selectedFluid.ordinal();
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> power = power & 0xFFFFFFFF00000000L | value & 0xFFFFFFFFL;
                case 1 -> power = power & 0xFFFFFFFFL | (long) value << 32;
                default -> { }
            }
        }
        @Override public int getCount() { return 10; }
    };

    private Component customName;
    private long power;
    private int sulfur;
    private boolean active;
    private long lastPower = Long.MIN_VALUE;
    private int lastInput = Integer.MIN_VALUE;
    private int[] lastOutputs = {-1, -1, -1, -1};
    private boolean lastActive;

    public RefineryBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_REFINERY.get(), position, state);
    }

    private FluidTank outputTank(Fluid fluid) {
        return new FluidTank(OUTPUT_CAPACITY, stack -> stack.getFluid().isSame(fluid)) {
            @Override protected void onContentsChanged() { RefineryBlockEntity.this.setChanged(); }
        };
    }

    public static void tick(Level level, BlockPos position, BlockState state, RefineryBlockEntity refinery) {
        if (!level.isClientSide) refinery.serverTick((ServerLevel) level, position, state);
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        active = false;
        subscribeConnections(level, position);
        dischargeBattery();
        refreshIdentifiedFluid();
        conformTanks();
        loadInputContainer();
        refine();
        unloadOutputContainers();
        pushOutputs(level, position);
        syncIfChanged(level, position, state);
    }

    private void subscribeConnections(ServerLevel level, BlockPos core) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Direction cross = direction.getClockWise();
            trySubscribe(level, core.relative(direction, 2).relative(cross), direction);
            trySubscribe(level, core.relative(direction, 2).relative(cross.getOpposite()), direction);
        }
    }

    private void dischargeBattery() {
        ItemStack stack = items.get(BATTERY);
        if (!(stack.getItem() instanceof HeBatteryItem battery)) return;
        long amount = Math.min(Math.min(POWER_CAPACITY - power, battery.getDischargeRate(stack)),
                battery.getCharge(stack));
        if (amount > 0L) {
            battery.discharge(stack, amount);
            power += amount;
        }
    }

    private void refreshIdentifiedFluid() {
        ItemStack identifier = items.get(FLUID_IDENTIFIER);
        if (!(identifier.getItem() instanceof FluidIdentifierItem)) return;
        FluidIdentifierItem.Selection installed = FluidIdentifierItem.primary(identifier);
        if (installed == selectedFluid) return;
        selectedFluid = installed;
        input.setFluid(FluidStack.EMPTY);
        setChanged();
    }

    private void conformTanks() {
        if (!input.isEmpty() && !selectedFluid.accepts(input.getFluid().getFluid())) {
            input.setFluid(FluidStack.EMPTY);
        }
        Fluid[] expected = {ModFluids.HEAVYOIL.get(), ModFluids.NAPHTHA.get(),
                ModFluids.LIGHTOIL.get(), ModFluids.PETROLEUM.get()};
        boolean hasRecipe = selectedFluid == FluidIdentifierItem.Selection.HOTOIL;
        for (int index = 0; index < outputs.length; index++) {
            FluidTank tank = outputs[index];
            if (!tank.isEmpty() && (!hasRecipe || !tank.getFluid().getFluid().isSame(expected[index]))) {
                tank.setFluid(FluidStack.EMPTY);
            }
        }
    }

    private void loadInputContainer() {
        ItemStack stack = items.get(INPUT_CONTAINER);
        if (stack.isEmpty()) return;
        if (InfiniteFluidBarrelItem.is(stack)) {
            if (selectedFluid != FluidIdentifierItem.Selection.NONE
                    && InfiniteFluidBarrelItem.fillTank(input, selectedFluid.fluid()) > 0) setChanged();
            return;
        }
        Fluid fluid;
        ItemStack remainder;
        if (stack.getItem() instanceof UniversalFluidTankItem) {
            UniversalFluidTankItem.ContainedFluid contained = UniversalFluidTankItem.fluid(stack);
            fluid = contained.fluid();
            remainder = new ItemStack(ModItems.FLUID_TANK_EMPTY.get());
        } else if (stack.is(ModItems.CANISTER_FULL.get())) {
            SourceFluidContainerItem.ContainedFluid contained = SourceFluidContainerItem.fluid(stack);
            fluid = contained.fluid();
            remainder = new ItemStack(ModItems.CANISTER_EMPTY.get());
        } else if (stack.is(ModItems.GAS_FULL.get())) {
            SourceFluidContainerItem.ContainedFluid contained = SourceFluidContainerItem.fluid(stack);
            fluid = contained.fluid();
            remainder = new ItemStack(ModItems.GAS_EMPTY.get());
        } else return;
        if (!selectedFluid.accepts(fluid) || !canMerge(items.get(INPUT_REMAINDER), remainder)) return;
        FluidStack load = new FluidStack(fluid, 1_000);
        if (input.fill(load, IFluidHandler.FluidAction.SIMULATE) != 1_000) return;
        input.fill(load, IFluidHandler.FluidAction.EXECUTE);
        stack.shrink(1);
        mergeInto(INPUT_REMAINDER, remainder);
    }

    private void refine() {
        if (selectedFluid != FluidIdentifierItem.Selection.HOTOIL || power < POWER_PER_OPERATION
                || input.getFluidAmount() < INPUT_PER_OPERATION
                || !input.getFluid().getFluid().isSame(ModFluids.HOTOIL.get())) return;
        for (int index = 0; index < outputs.length; index++) {
            if (outputs[index].getSpace() < OUTPUT_AMOUNTS[index]) return;
        }
        input.drain(INPUT_PER_OPERATION, IFluidHandler.FluidAction.EXECUTE);
        Fluid[] fluids = {ModFluids.HEAVYOIL.get(), ModFluids.NAPHTHA.get(),
                ModFluids.LIGHTOIL.get(), ModFluids.PETROLEUM.get()};
        for (int index = 0; index < outputs.length; index++) {
            outputs[index].fill(new FluidStack(fluids[index], OUTPUT_AMOUNTS[index]),
                    IFluidHandler.FluidAction.EXECUTE);
        }
        sulfur++;
        if (sulfur >= 10) {
            sulfur -= 10;
            ItemStack produced = new ItemStack(ModItems.get("sulfur").get());
            if (canMerge(items.get(SULFUR_OUTPUT), produced)) mergeInto(SULFUR_OUTPUT, produced);
        }
        power -= POWER_PER_OPERATION;
        active = true;
        setChanged();
    }

    private void unloadOutputContainers() {
        unloadOutputContainer(0, HEAVY_EMPTY, HEAVY_FULL, false);
        unloadOutputContainer(1, NAPHTHA_EMPTY, NAPHTHA_FULL, false);
        unloadOutputContainer(2, LIGHT_EMPTY, LIGHT_FULL, false);
        unloadOutputContainer(3, PETROLEUM_EMPTY, PETROLEUM_FULL, true);
    }

    private void unloadOutputContainer(int lane, int emptySlot, int fullSlot, boolean gas) {
        FluidTank tank = outputs[lane];
        ItemStack empty = items.get(emptySlot);
        if (empty.isEmpty()) return;
        if (InfiniteFluidBarrelItem.is(empty)) {
            if (InfiniteFluidBarrelItem.discardTank(tank) > 0) setChanged();
            return;
        }
        if (tank.getFluidAmount() < 1_000) return;
        ItemStack full;
        if (empty.is(ModItems.FLUID_TANK_EMPTY.get())) {
            UniversalFluidTankItem.ContainedFluid type = UniversalFluidTankItem.ContainedFluid.fromFluid(
                    tank.getFluid().getFluid());
            if (type == null || type == UniversalFluidTankItem.ContainedFluid.NONE) return;
            full = UniversalFluidTankItem.create(ModItems.FLUID_TANK_FULL.get(), type, 1);
        } else if (!gas && empty.is(ModItems.CANISTER_EMPTY.get())) {
            SourceFluidContainerItem.ContainedFluid type = SourceFluidContainerItem.ContainedFluid.fromFluid(
                    tank.getFluid().getFluid());
            if (type != SourceFluidContainerItem.ContainedFluid.HEAVYOIL
                    && type != SourceFluidContainerItem.ContainedFluid.NAPHTHA
                    && type != SourceFluidContainerItem.ContainedFluid.LIGHTOIL) return;
            full = SourceFluidContainerItem.create(ModItems.CANISTER_FULL.get(), type, 1);
        } else if (gas && empty.is(ModItems.GAS_EMPTY.get())) {
            full = SourceFluidContainerItem.create(ModItems.GAS_FULL.get(),
                    SourceFluidContainerItem.ContainedFluid.PETROLEUM, 1);
        } else return;
        if (!canMerge(items.get(fullSlot), full)) return;
        empty.shrink(1);
        tank.drain(1_000, IFluidHandler.FluidAction.EXECUTE);
        mergeInto(fullSlot, full);
    }

    private void pushOutputs(ServerLevel level, BlockPos core) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Direction cross = direction.getClockWise();
            pushOutputsAt(level, core.relative(direction, 2).relative(cross), direction);
            pushOutputsAt(level, core.relative(direction, 2).relative(cross.getOpposite()), direction);
        }
    }

    private void pushOutputsAt(ServerLevel level, BlockPos target, Direction direction) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                target, direction.getOpposite());
        if (handler == null) return;
        for (FluidTank tank : outputs) {
            if (tank.isEmpty()) continue;
            int accepted = handler.fill(tank.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
            if (accepted > 0) tank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private static boolean canMerge(ItemStack existing, ItemStack addition) {
        return !addition.isEmpty() && (existing.isEmpty()
                || ItemStack.isSameItemSameComponents(existing, addition)
                && existing.getCount() + addition.getCount() <= existing.getMaxStackSize());
    }

    private void mergeInto(int slot, ItemStack addition) {
        ItemStack existing = items.get(slot);
        if (existing.isEmpty()) items.set(slot, addition.copy());
        else existing.grow(addition.getCount());
    }

    private void syncIfChanged(ServerLevel level, BlockPos position, BlockState state) {
        int[] amounts = Arrays.stream(outputs).mapToInt(FluidTank::getFluidAmount).toArray();
        if (power != lastPower || input.getFluidAmount() != lastInput
                || !Arrays.equals(amounts, lastOutputs) || active != lastActive
                || level.getGameTime() % 20L == 0L) {
            lastPower = power;
            lastInput = input.getFluidAmount();
            lastOutputs = amounts;
            lastActive = active;
            level.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS);
        }
        setChanged();
    }

    public FluidTank inputTank() { return input; }
    public FluidTank outputTank(int index) { return outputs[index]; }
    public IFluidHandler inputFluidHandler() { return inputHandler; }
    public ContainerData dataAccess() { return data; }
    public FluidIdentifierItem.Selection configuredFluid() { return selectedFluid; }
    public int sulfurCounter() { return sulfur; }
    public boolean active() { return active; }

    /** Source persistent-machine drop keeps tanks, but not HE, sulfur progress or inventory. */
    public ItemStack machineDrop() {
        ItemStack stack = new ItemStack(ModItems.MACHINE_REFINERY_ITEM.get());
        if (input.isEmpty() && Arrays.stream(outputs).allMatch(FluidTank::isEmpty)) return stack;
        CompoundTag data = new CompoundTag();
        data.putString(ITEM_SELECTED_FLUID, selectedFluid.id());
        data.putInt(ITEM_INPUT, input.getFluidAmount());
        data.putInt(ITEM_HEAVY, outputs[0].getFluidAmount());
        data.putInt(ITEM_NAPHTHA, outputs[1].getFluidAmount());
        data.putInt(ITEM_LIGHT, outputs[2].getFluidAmount());
        data.putInt(ITEM_PETROLEUM, outputs[3].getFluidAmount());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    public void restoreFromItem(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains(ITEM_SELECTED_FLUID)) return;
        selectedFluid = FluidIdentifierItem.Selection.byId(data.getString(ITEM_SELECTED_FLUID));
        int inputAmount = Math.clamp(data.getInt(ITEM_INPUT), 0, INPUT_CAPACITY);
        input.setFluid(inputAmount == 0 || selectedFluid == FluidIdentifierItem.Selection.NONE
                ? FluidStack.EMPTY : new FluidStack(selectedFluid.fluid(), inputAmount));
        Fluid[] fluids = {ModFluids.HEAVYOIL.get(), ModFluids.NAPHTHA.get(),
                ModFluids.LIGHTOIL.get(), ModFluids.PETROLEUM.get()};
        String[] keys = {ITEM_HEAVY, ITEM_NAPHTHA, ITEM_LIGHT, ITEM_PETROLEUM};
        for (int index = 0; index < outputs.length; index++) {
            int amount = Math.clamp(data.getInt(keys[index]), 0, OUTPUT_CAPACITY);
            outputs[index].setFluid(amount == 0 ? FluidStack.EMPTY : new FluidStack(fluids[index], amount));
        }
        conformTanks();
        setChanged();
    }

    @Override public long getPower() { return Math.clamp(power, 0L, POWER_CAPACITY); }
    @Override public void setPower(long value) { power = Math.clamp(value, 0L, POWER_CAPACITY); }
    @Override public long getMaxPower() { return POWER_CAPACITY; }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putLong("power", power);
        tag.putInt("sulfur", sulfur);
        tag.putString("selectedFluid", selectedFluid.id());
        tag.put("input", input.writeToNBT(registries, new CompoundTag()));
        String[] names = {"heavy", "naphtha", "light", "petroleum"};
        for (int index = 0; index < outputs.length; index++) {
            tag.put(names[index], outputs[index].writeToNBT(registries, new CompoundTag()));
        }
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        power = Math.clamp(tag.getLong("power"), 0L, POWER_CAPACITY);
        sulfur = Math.clamp(tag.getInt("sulfur"), 0, 9);
        selectedFluid = tag.contains("selectedFluid")
                ? FluidIdentifierItem.Selection.byId(tag.getString("selectedFluid"))
                : FluidIdentifierItem.Selection.HOTOIL;
        if (tag.contains("input")) input.readFromNBT(registries, tag.getCompound("input"));
        String[] names = {"heavy", "naphtha", "light", "petroleum"};
        for (int index = 0; index < outputs.length; index++) {
            if (tag.contains(names[index])) outputs[index].readFromNBT(registries, tag.getCompound(names[index]));
        }
        conformTanks();
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("power", power);
        tag.putInt("sulfur", sulfur);
        tag.putBoolean("active", active);
        tag.putString("selectedFluid", selectedFluid.id());
        tag.put("input", input.writeToNBT(registries, new CompoundTag()));
        String[] names = {"heavy", "naphtha", "light", "petroleum"};
        for (int index = 0; index < outputs.length; index++) {
            tag.put(names[index], outputs[index].writeToNBT(registries, new CompoundTag()));
        }
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        power = tag.getLong("power");
        sulfur = tag.getInt("sulfur");
        active = tag.getBoolean("active");
        selectedFluid = FluidIdentifierItem.Selection.byId(tag.getString("selectedFluid"));
        if (tag.contains("input")) input.readFromNBT(registries, tag.getCompound("input"));
        String[] names = {"heavy", "naphtha", "light", "petroleum"};
        for (int index = 0; index < outputs.length; index++) {
            if (tag.contains(names[index])) outputs[index].readFromNBT(registries, tag.getCompound(names[index]));
        }
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.machineRefinery");
    }
    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RefineryMenu(id, inventory, this, data);
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getCenter()) <= 128D;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == BATTERY) return stack.getItem() instanceof HeBatteryItem;
        if (slot == INPUT_CONTAINER) return isSelectedFluidContainer(stack);
        if (slot == HEAVY_EMPTY || slot == NAPHTHA_EMPTY || slot == LIGHT_EMPTY) {
            return InfiniteFluidBarrelItem.is(stack) || stack.is(ModItems.CANISTER_EMPTY.get())
                    || stack.is(ModItems.FLUID_TANK_EMPTY.get());
        }
        if (slot == PETROLEUM_EMPTY) {
            return InfiniteFluidBarrelItem.is(stack) || stack.is(ModItems.GAS_EMPTY.get())
                    || stack.is(ModItems.FLUID_TANK_EMPTY.get());
        }
        return slot == FLUID_IDENTIFIER && stack.getItem() instanceof FluidIdentifierItem;
    }

    private boolean isSelectedFluidContainer(ItemStack stack) {
        if (InfiniteFluidBarrelItem.is(stack)) {
            return selectedFluid != FluidIdentifierItem.Selection.NONE;
        }
        if (stack.getItem() instanceof UniversalFluidTankItem) {
            return selectedFluid.accepts(UniversalFluidTankItem.fluid(stack).fluid());
        }
        if (stack.is(ModItems.CANISTER_FULL.get()) || stack.is(ModItems.GAS_FULL.get())) {
            return selectedFluid.accepts(SourceFluidContainerItem.fluid(stack).fluid());
        }
        return false;
    }

    @Override public int[] getSlotsForFace(Direction side) {
        return Arrays.copyOf(SULFUR_ONLY, SULFUR_ONLY.length);
    }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return false;
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SULFUR_OUTPUT;
    }
}
