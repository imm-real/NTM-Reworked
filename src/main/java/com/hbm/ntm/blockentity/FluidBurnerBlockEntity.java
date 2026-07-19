package com.hbm.ntm.blockentity;

import com.hbm.ntm.inventory.FluidBurnerMenu;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.SourceFluidContainerItem;
import com.hbm.ntm.item.InfiniteFluidBarrelItem;
import com.hbm.ntm.item.UniversalFluidTankItem;
import com.hbm.ntm.pollution.PollutionData;
import com.hbm.ntm.recipe.FluidBurnerFuels;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.thermal.HeatSource;
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

/** Burns fluids, occasionally including the concept of an empty tank. */
public final class FluidBurnerBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, HeatSource {
    public static final int CONTAINER_INPUT = 0;
    public static final int CONTAINER_OUTPUT = 1;
    public static final int IDENTIFIER = 2;
    public static final int SLOT_COUNT = 3;
    public static final int FUEL_CAPACITY = 16_000;
    public static final int MAX_HEAT = 100_000;
    public static final int SMOKE_CAPACITY = 100;
    private static final int[] NO_SLOTS = new int[0];

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private FluidIdentifierItem.Selection selectedFluid = FluidIdentifierItem.Selection.HEATINGOIL;
    private final FluidTank fuel = new FluidTank(FUEL_CAPACITY,
            stack -> selectedFluid.accepts(stack.getFluid())) {
        @Override protected void onContentsChanged() { FluidBurnerBlockEntity.this.setChanged(); }
    };
    private boolean isOn;
    private int setting = 1;
    private int heatEnergy;
    private int smoke;
    private Component customName;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> heatEnergy;
                case 1 -> fuel.getFluidAmount();
                case 2 -> selectedFluid.ordinal();
                case 3 -> isOn ? 1 : 0;
                case 4 -> setting;
                case 5 -> smoke;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> heatEnergy = value;
                case 2 -> {
                    FluidIdentifierItem.Selection[] values = FluidIdentifierItem.Selection.values();
                    if (value >= 0 && value < values.length) selectedFluid = values[value];
                }
                case 3 -> isOn = value != 0;
                case 4 -> setting = value;
                case 5 -> smoke = value;
                default -> { }
            }
        }
        @Override public int getCount() { return 6; }
    };

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

    public FluidBurnerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HEATER_OILBURNER.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FluidBurnerBlockEntity burner) {
        if (!level.isClientSide) burner.serverTick((ServerLevel) level, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        // Empty the container before deciding what the tank calls itself.
        loadFuelContainer();
        refreshSelectedFluid();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            pushSmoke(level, pos.relative(direction, 2), direction.getOpposite());
        }

        boolean shouldCool = true;
        if (isOn && heatEnergy < MAX_HEAT && FluidBurnerFuels.flammable(selectedFluid)) {
            int toBurn = Math.min(setting, fuel.getFluidAmount());
            fuel.drain(toBurn, IFluidHandler.FluidAction.EXECUTE);
            heatEnergy += FluidBurnerFuels.heatPerMb(selectedFluid) * toBurn;
            if (level.getGameTime() % 5L == 0L && toBurn > 0 && FluidBurnerFuels.polluting(selectedFluid)) {
                emitSmoke(level, pos);
            }
            // An enabled flammable tank refuses to cool, even while empty.
            shouldCool = false;
        }
        // A full buffer burns nothing and cools nothing. Overshoot gets diplomatic immunity.
        if (heatEnergy >= MAX_HEAT) shouldCool = false;
        if (shouldCool) heatEnergy = Math.max(heatEnergy - Math.max(heatEnergy / 1000, 1), 0);

        setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    private void refreshSelectedFluid() {
        ItemStack identifier = items.get(IDENTIFIER);
        if (!(identifier.getItem() instanceof FluidIdentifierItem)) return;
        FluidIdentifierItem.Selection selection = FluidIdentifierItem.primary(identifier);
        if (selection == selectedFluid) return;
        selectedFluid = selection;
        fuel.setFluid(FluidStack.EMPTY);
        setChanged();
    }

    private void loadFuelContainer() {
        ItemStack input = items.get(CONTAINER_INPUT);
        if (input.isEmpty()) return;
        if (InfiniteFluidBarrelItem.is(input)) {
            if (selectedFluid != FluidIdentifierItem.Selection.NONE
                    && InfiniteFluidBarrelItem.fillTank(fuel, selectedFluid.fluid()) > 0) setChanged();
            return;
        }
        Fluid fluid;
        ItemStack remainder;
        if (input.getItem() instanceof UniversalFluidTankItem) {
            fluid = UniversalFluidTankItem.fluid(input).fluid();
            remainder = new ItemStack(ModItems.FLUID_TANK_EMPTY.get());
        } else if (input.is(ModItems.CANISTER_FULL.get())) {
            fluid = SourceFluidContainerItem.fluid(input).fluid();
            remainder = new ItemStack(ModItems.CANISTER_EMPTY.get());
        } else if (input.is(ModItems.GAS_FULL.get())) {
            fluid = SourceFluidContainerItem.fluid(input).fluid();
            remainder = new ItemStack(ModItems.GAS_EMPTY.get());
        } else return;
        if (!selectedFluid.accepts(fluid) || !canMerge(items.get(CONTAINER_OUTPUT), remainder)
                || fuel.fill(new FluidStack(fluid, 1_000), IFluidHandler.FluidAction.SIMULATE) != 1_000) return;
        fuel.fill(new FluidStack(fluid, 1_000), IFluidHandler.FluidAction.EXECUTE);
        input.shrink(1);
        if (input.isEmpty()) items.set(CONTAINER_INPUT, ItemStack.EMPTY);
        mergeOutput(CONTAINER_OUTPUT, remainder);
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

    private void pushSmoke(ServerLevel level, BlockPos target, Direction side) {
        if (smoke <= 0) return;
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, target, side);
        if (handler == null) return;
        int accepted = handler.fill(new FluidStack(ModFluids.SMOKE.get(), smoke), IFluidHandler.FluidAction.EXECUTE);
        smoke -= Math.min(Math.max(accepted, 0), smoke);
    }

    private void emitSmoke(ServerLevel level, BlockPos pos) {
        // Every active oil/fuel/gas profile coughs exactly one mB into this buffer.
        smoke++;
        if (smoke <= SMOKE_CAPACITY) return;
        smoke = SMOKE_CAPACITY;
        PollutionData.get(level).increment(pos, PollutionData.Type.SOOT, 0.01F);
        if (level.random.nextInt(3) == 0) {
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.1F, 1.5F);
        }
    }

    public void toggleSetting() {
        setting++;
        if (setting > 10) setting = 1;
        setChanged();
    }

    public void toggleOn() {
        isOn = !isOn;
        setChanged();
    }

    public void setCustomName(Component name) { customName = name; setChanged(); }

    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.heaterOilburner");
    }

    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new FluidBurnerMenu(id, inventory, this, data);
    }

    @Override public int getHeatStored() { return heatEnergy; }
    @Override public void useUpHeat(int heat) { heatEnergy = Math.max(0, heatEnergy - heat); setChanged(); }

    public IFluidHandler fluidHandler() { return fluidHandler; }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("tank", fuel.getFluidAmount());
        tag.putString("selectedFluid", selectedFluid.id());
        tag.putBoolean("isOn", isOn);
        tag.putInt("heatEnergy", heatEnergy);
        tag.putByte("setting", (byte) setting);
        tag.putInt("smoke0", smoke);
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        selectedFluid = tag.contains("selectedFluid")
                ? FluidIdentifierItem.Selection.byId(tag.getString("selectedFluid"))
                : FluidIdentifierItem.Selection.HEATINGOIL;
        fuel.setFluid(tag.getInt("tank") > 0
                ? new FluidStack(selectedFluid.fluid(), Math.min(tag.getInt("tank"), FUEL_CAPACITY))
                : FluidStack.EMPTY);
        isOn = tag.getBoolean("isOn");
        heatEnergy = tag.getInt("heatEnergy");
        setting = Math.clamp(tag.getByte("setting"), 1, 10);
        smoke = Math.clamp(tag.getInt("smoke0"), 0, SMOKE_CAPACITY);
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("tank", fuel.getFluidAmount());
        tag.putString("selectedFluid", selectedFluid.id());
        tag.putBoolean("isOn", isOn);
        tag.putInt("heatEnergy", heatEnergy);
        tag.putByte("setting", (byte) setting);
        tag.putInt("smoke0", smoke);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        selectedFluid = FluidIdentifierItem.Selection.byId(tag.getString("selectedFluid"));
        fuel.setFluid(tag.getInt("tank") > 0
                ? new FluidStack(selectedFluid.fluid(), tag.getInt("tank")) : FluidStack.EMPTY);
        isOn = tag.getBoolean("isOn");
        heatEnergy = tag.getInt("heatEnergy");
        setting = tag.getByte("setting");
        smoke = tag.getInt("smoke0");
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack result = ContainerHelper.removeItem(items, slot, count);
        if (!result.isEmpty()) setChanged();
        return result;
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
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return false; }
    @Override public int[] getSlotsForFace(Direction side) { return NO_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }

    public static boolean isFluidContainer(ItemStack stack) {
        return InfiniteFluidBarrelItem.is(stack) || stack.getItem() instanceof UniversalFluidTankItem
                || stack.is(ModItems.CANISTER_FULL.get()) || stack.is(ModItems.GAS_FULL.get());
    }

    public FluidIdentifierItem.Selection selectedFluid() { return selectedFluid; }
    public int fuelAmount() { return fuel.getFluidAmount(); }
    public int heatEnergy() { return heatEnergy; }
    public int setting() { return setting; }
    public boolean isOn() { return isOn; }
    public int smokeAmount() { return smoke; }

    public void selectForTest(FluidIdentifierItem.Selection selection) {
        selectedFluid = selection;
        fuel.setFluid(FluidStack.EMPTY);
    }
    public int addFuelForTest(int amount) {
        return fuel.fill(new FluidStack(selectedFluid.fluid(), amount), IFluidHandler.FluidAction.EXECUTE);
    }
}
