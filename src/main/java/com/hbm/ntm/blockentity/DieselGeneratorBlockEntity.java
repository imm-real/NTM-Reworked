package com.hbm.ntm.blockentity;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.energy.HeProvider;
import com.hbm.ntm.inventory.DieselGeneratorMenu;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.InfiniteFluidBarrelItem;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.item.SourceFluidContainerItem;
import com.hbm.ntm.item.UniversalFluidTankItem;
import com.hbm.ntm.pollution.PollutionData;
import com.hbm.ntm.recipe.DieselGeneratorFuels;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
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

/** Diesel Generator that burns one mB of fuel per tick. */
public final class DieselGeneratorBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, HeProvider {
    public static final int FUEL_INPUT = 0;
    public static final int CONTAINER_OUTPUT = 1;
    public static final int BATTERY = 2;
    public static final int IDENTIFIER_INPUT = 3;
    public static final int IDENTIFIER_OUTPUT = 4;
    public static final int SLOT_COUNT = 5;
    public static final int FUEL_CAPACITY = 4_000;
    public static final int SMOKE_CAPACITY = 100;

    private static final int[] TOP_SLOTS = {FUEL_INPUT};
    private static final int[] BOTTOM_SLOTS = {CONTAINER_OUTPUT, BATTERY};
    private static final int[] SIDE_SLOTS = {BATTERY};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private FluidIdentifierItem.Selection selectedFluid = FluidIdentifierItem.Selection.DIESEL;
    private final FluidTank fuel = new FluidTank(FUEL_CAPACITY,
            stack -> selectedFluid.accepts(stack.getFluid())) {
        @Override protected void onContentsChanged() {
            DieselGeneratorBlockEntity.this.setChanged();
        }
    };
    private int smoke;
    private long power;
    private boolean active;
    private Component customName;

    private long lastPower = Long.MIN_VALUE;
    private int lastFuel = Integer.MIN_VALUE;
    private int lastSmoke = Integer.MIN_VALUE;
    private FluidIdentifierItem.Selection lastSelected;
    private boolean lastActive;

    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return 2; }
        @Override public FluidStack getFluidInTank(int tank) {
            if (tank == 0) return fuel.getFluid().copy();
            return tank == 1 && smoke > 0 ? new FluidStack(ModFluids.SMOKE.get(), smoke) : FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int tank) {
            return tank == 0 ? FUEL_CAPACITY : tank == 1 ? SMOKE_CAPACITY : 0;
        }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && selectedFluid.accepts(stack.getFluid());
        }
        @Override public int fill(FluidStack resource, FluidAction action) {
            return selectedFluid.accepts(resource.getFluid()) ? fuel.fill(resource, action) : 0;
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !resource.is(ModFluids.SMOKE.get())) return FluidStack.EMPTY;
            return drain(resource.getAmount(), action);
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            int drained = Math.min(Math.max(maxDrain, 0), smoke);
            if (drained <= 0) return FluidStack.EMPTY;
            FluidStack result = new FluidStack(ModFluids.SMOKE.get(), drained);
            if (action.execute()) {
                smoke -= drained;
                setChanged();
            }
            return result;
        }
    };

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int) power;
                case 1 -> (int) (power >>> 32);
                case 2 -> (int) maxPower();
                case 3 -> (int) (maxPower() >>> 32);
                case 4 -> fuel.getFluidAmount();
                case 5 -> selectedFluid.ordinal();
                case 6 -> active ? 1 : 0;
                case 7 -> DieselGeneratorFuels.accepted(selectedFluid) ? 1 : 0;
                case 8 -> smoke;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> power = power & 0xFFFFFFFF00000000L | value & 0xFFFFFFFFL;
                case 1 -> power = power & 0xFFFFFFFFL | (long) value << 32;
                case 5 -> {
                    FluidIdentifierItem.Selection[] values = FluidIdentifierItem.Selection.values();
                    if (value >= 0 && value < values.length) selectedFluid = values[value];
                }
                case 6 -> active = value != 0;
                case 8 -> smoke = value;
                default -> { }
            }
        }
        @Override public int getCount() { return 9; }
    };

    public DieselGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_DIESEL.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DieselGeneratorBlockEntity generator) {
        if (!level.isClientSide) generator.serverTick((ServerLevel) level, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        active = false;
        for (Direction direction : Direction.values()) {
            tryProvide(level, pos.relative(direction), direction);
            pushSmoke(level, pos.relative(direction), direction.getOpposite());
        }
        refreshSelectedFluid();
        loadFuelContainer();
        chargeBattery();
        generate(level, pos);
        syncIfChanged(level, pos, state);
    }

    private void refreshSelectedFluid() {
        ItemStack identifier = items.get(IDENTIFIER_INPUT);
        if (!(identifier.getItem() instanceof FluidIdentifierItem) || !items.get(IDENTIFIER_OUTPUT).isEmpty()) return;
        FluidIdentifierItem.Selection selection = FluidIdentifierItem.primary(identifier);
        if (selection == selectedFluid) return;
        selectedFluid = selection;
        fuel.setFluid(FluidStack.EMPTY);
        items.set(IDENTIFIER_OUTPUT, identifier.copy());
        items.set(IDENTIFIER_INPUT, ItemStack.EMPTY);
        setChanged();
    }

    private void loadFuelContainer() {
        ItemStack input = items.get(FUEL_INPUT);
        if (input.isEmpty()) return;
        if (InfiniteFluidBarrelItem.is(input)) {
            if (selectedFluid != FluidIdentifierItem.Selection.NONE
                    && InfiniteFluidBarrelItem.fillTank(fuel, selectedFluid.fluid()) > 0) setChanged();
            return;
        }
        Fluid fluid;
        ItemStack remainder;
        if (input.getItem() instanceof UniversalFluidTankItem) {
            UniversalFluidTankItem.ContainedFluid contained = UniversalFluidTankItem.fluid(input);
            fluid = contained.fluid();
            remainder = new ItemStack(ModItems.FLUID_TANK_EMPTY.get());
        } else if (input.is(ModItems.CANISTER_FULL.get())) {
            SourceFluidContainerItem.ContainedFluid contained = SourceFluidContainerItem.fluid(input);
            fluid = contained.fluid();
            remainder = new ItemStack(ModItems.CANISTER_EMPTY.get());
        } else if (input.is(ModItems.GAS_FULL.get())) {
            SourceFluidContainerItem.ContainedFluid contained = SourceFluidContainerItem.fluid(input);
            fluid = contained.fluid();
            remainder = new ItemStack(ModItems.GAS_EMPTY.get());
        } else return;
        if (!selectedFluid.accepts(fluid) || !canMerge(items.get(CONTAINER_OUTPUT), remainder)) return;
        FluidStack load = new FluidStack(fluid, 1_000);
        if (fuel.fill(load, IFluidHandler.FluidAction.SIMULATE) != 1_000) return;
        fuel.fill(load, IFluidHandler.FluidAction.EXECUTE);
        input.shrink(1);
        if (input.isEmpty()) items.set(FUEL_INPUT, ItemStack.EMPTY);
        mergeOutput(CONTAINER_OUTPUT, remainder);
        setChanged();
    }

    private void chargeBattery() {
        ItemStack stack = items.get(BATTERY);
        if (!(stack.getItem() instanceof HeBatteryItem battery)) return;
        long amount = Math.min(Math.min(power, battery.getChargeRate(stack)),
                Math.max(battery.getMaxCharge(stack) - battery.getCharge(stack), 0L));
        if (amount <= 0L) return;
        battery.charge(stack, amount);
        power -= amount;
        setChanged();
    }

    private void generate(ServerLevel level, BlockPos pos) {
        if (level.hasNeighborSignal(pos) || fuel.isEmpty()) return;
        long produced = DieselGeneratorFuels.energyPerMb(selectedFluid);
        if (produced <= 0L) return;
        active = true;
        fuel.drain(1, IFluidHandler.FluidAction.EXECUTE);
        if (level.getGameTime() % 5L == 0L && DieselGeneratorFuels.fuel(selectedFluid).polluting()) emitSmoke(level, pos);
        power = Math.min(maxPower(), power + produced);
    }

    private void pushSmoke(ServerLevel level, BlockPos target, Direction side) {
        if (smoke <= 0) return;
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, target, side);
        if (handler == null) return;
        int accepted = handler.fill(new FluidStack(ModFluids.SMOKE.get(), smoke),
                IFluidHandler.FluidAction.EXECUTE);
        smoke -= Math.min(Math.max(accepted, 0), smoke);
    }

    private void emitSmoke(ServerLevel level, BlockPos pos) {
        smoke++;
        if (smoke <= SMOKE_CAPACITY) return;
        int overflow = smoke - SMOKE_CAPACITY;
        smoke = SMOKE_CAPACITY;
        PollutionData.get(level).increment(pos, PollutionData.Type.SOOT, overflow / 100F);
        if (level.random.nextInt(3) == 0) {
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.1F, 1.5F);
        }
    }

    private void syncIfChanged(ServerLevel level, BlockPos pos, BlockState state) {
        if (power != lastPower || fuel.getFluidAmount() != lastFuel || smoke != lastSmoke
                || selectedFluid != lastSelected || active != lastActive || level.getGameTime() % 20L == 0L) {
            lastPower = power;
            lastFuel = fuel.getFluidAmount();
            lastSmoke = smoke;
            lastSelected = selectedFluid;
            lastActive = active;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
        setChanged();
    }

    private static boolean canMerge(ItemStack target, ItemStack addition) {
        return !addition.isEmpty() && (target.isEmpty() || ItemStack.isSameItemSameComponents(target, addition)
                && target.getCount() + addition.getCount() <= target.getMaxStackSize());
    }

    private void mergeOutput(int slot, ItemStack addition) {
        if (items.get(slot).isEmpty()) items.set(slot, addition.copy());
        else items.get(slot).grow(addition.getCount());
    }

    public FluidIdentifierItem.Selection selectedFluid() { return selectedFluid; }
    public int fuelAmount() { return fuel.getFluidAmount(); }
    public int smokeAmount() { return smoke; }
    public boolean active() { return active; }
    public long maxPower() { return HbmConfig.DIESEL_POWER_CAPACITY.get(); }
    public IFluidHandler fluidHandler(@Nullable Direction side) { return fluidHandler; }

    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.machineDiesel");
    }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new DieselGeneratorMenu(id, inventory, this, data);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putLong("power", power);
        tag.putString("selectedFluid", selectedFluid.id());
        tag.put("fuel", fuel.writeToNBT(registries, new CompoundTag()));
        tag.putInt("smoke", smoke);
        tag.putBoolean("active", active);
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        power = Math.clamp(tag.getLong("power"), 0L, maxPower());
        selectedFluid = tag.contains("selectedFluid")
                ? FluidIdentifierItem.Selection.byId(tag.getString("selectedFluid"))
                : FluidIdentifierItem.Selection.DIESEL;
        if (tag.contains("fuel")) fuel.readFromNBT(registries, tag.getCompound("fuel"));
        if (!fuel.isEmpty() && !selectedFluid.accepts(fuel.getFluid().getFluid())) fuel.setFluid(FluidStack.EMPTY);
        smoke = Math.clamp(tag.getInt("smoke"), 0, SMOKE_CAPACITY);
        active = tag.getBoolean("active");
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("power", power);
        tag.putString("selectedFluid", selectedFluid.id());
        tag.put("fuel", fuel.writeToNBT(registries, new CompoundTag()));
        tag.putInt("smoke", smoke);
        tag.putBoolean("active", active);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        power = Math.clamp(tag.getLong("power"), 0L, maxPower());
        selectedFluid = FluidIdentifierItem.Selection.byId(tag.getString("selectedFluid"));
        if (tag.contains("fuel")) fuel.readFromNBT(registries, tag.getCompound("fuel"));
        smoke = Math.clamp(tag.getInt("smoke"), 0, SMOKE_CAPACITY);
        active = tag.getBoolean("active");
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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

    @Override public int[] getSlotsForFace(Direction side) {
        return side == Direction.UP ? TOP_SLOTS : side == Direction.DOWN ? BOTTOM_SLOTS : SIDE_SLOTS;
    }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (slot == CONTAINER_OUTPUT) return stack.is(ModItems.CANISTER_EMPTY.get())
                || stack.is(ModItems.GAS_EMPTY.get()) || stack.is(ModItems.FLUID_TANK_EMPTY.get())
                || stack.is(ModItems.TANK_STEEL.get());
        return slot == BATTERY && stack.getItem() instanceof HeBatteryItem battery
                && battery.getCharge(stack) >= battery.getMaxCharge(stack);
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == FUEL_INPUT) return containerMatchesSelection(stack);
        if (slot == BATTERY) return stack.getItem() instanceof HeBatteryItem;
        return slot == IDENTIFIER_INPUT && stack.getItem() instanceof FluidIdentifierItem;
    }

    private boolean containerMatchesSelection(ItemStack stack) {
        if (InfiniteFluidBarrelItem.is(stack)) {
            return selectedFluid != FluidIdentifierItem.Selection.NONE;
        }
        Fluid fluid;
        if (stack.getItem() instanceof UniversalFluidTankItem) fluid = UniversalFluidTankItem.fluid(stack).fluid();
        else if (stack.is(ModItems.CANISTER_FULL.get()) || stack.is(ModItems.GAS_FULL.get())) {
            fluid = SourceFluidContainerItem.fluid(stack).fluid();
        } else return false;
        return selectedFluid.accepts(fluid);
    }

    @Override public long getPower() { return power; }
    @Override public void setPower(long power) { this.power = Math.clamp(power, 0L, maxPower()); setChanged(); }
    @Override public long getMaxPower() { return maxPower(); }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }

    public void setSelectedForTest(FluidIdentifierItem.Selection selection) {
        selectedFluid = selection;
        fuel.setFluid(FluidStack.EMPTY);
    }
    public int addFuelForTest(int amount) {
        return fuel.fill(new FluidStack(selectedFluid.fluid(), amount), IFluidHandler.FluidAction.EXECUTE);
    }
    public void generateForTest(ServerLevel level) { active = false; generate(level, worldPosition); }
    public void addSmokeForTest(int amount) { smoke = Math.clamp(smoke + amount, 0, SMOKE_CAPACITY); }
}
