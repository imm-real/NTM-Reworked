package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.HeatExchangerBlock;
import com.hbm.ntm.inventory.HeatExchangerMenu;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.recipe.HeatExchangerRecipes;
import com.hbm.ntm.registry.ModBlockEntities;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/** Source Heat Exchanging Heater conversion order and cycle controls. */
public final class HeatExchangerBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, HeatSource {
    public static final int IDENTIFIER = 0;
    public static final int SLOT_COUNT = 1;
    public static final int TANK_CAPACITY = 24_000;
    private static final int[] NO_SLOTS = new int[0];

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private FluidIdentifierItem.Selection selectedInput = FluidIdentifierItem.Selection.COOLANT_HOT;
    private final FluidTank input = new FluidTank(TANK_CAPACITY,
            stack -> selectedInput.accepts(stack.getFluid())) {
        @Override protected void onContentsChanged() { HeatExchangerBlockEntity.this.setChanged(); }
    };
    private final FluidTank output = new FluidTank(TANK_CAPACITY,
            stack -> outputSelection().accepts(stack.getFluid())) {
        @Override protected void onContentsChanged() { HeatExchangerBlockEntity.this.setChanged(); }
    };
    private int amountToCool = TANK_CAPACITY;
    private int tickDelay = 1;
    private int heatEnergy;
    private Component customName;
    private int lastInput = -1;
    private int lastOutput = -1;
    private int lastHeat = -1;
    private FluidIdentifierItem.Selection lastSelection = FluidIdentifierItem.Selection.NONE;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> heatEnergy;
                case 1 -> input.getFluidAmount();
                case 2 -> output.getFluidAmount();
                case 3 -> selectedInput.ordinal();
                case 4 -> outputSelection().ordinal();
                case 5 -> amountToCool;
                case 6 -> tickDelay;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            FluidIdentifierItem.Selection[] values = FluidIdentifierItem.Selection.values();
            switch (index) {
                case 0 -> heatEnergy = value;
                case 3 -> {
                    if (value >= 0 && value < values.length) selectedInput = values[value];
                }
                case 5 -> amountToCool = value;
                case 6 -> tickDelay = value;
                default -> { }
            }
        }
        @Override public int getCount() { return 7; }
    };

    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return 2; }
        @Override public FluidStack getFluidInTank(int tank) {
            return tank == 0 ? input.getFluid().copy() : tank == 1 ? output.getFluid().copy() : FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int tank) { return tank == 0 || tank == 1 ? TANK_CAPACITY : 0; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && selectedInput.accepts(stack.getFluid());
        }
        @Override public int fill(FluidStack resource, FluidAction action) {
            return isFluidValid(0, resource) ? input.fill(resource, action) : 0;
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || output.isEmpty() || !resource.getFluid().isSame(output.getFluid().getFluid())) {
                return FluidStack.EMPTY;
            }
            return output.drain(resource, action);
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            return output.drain(maxDrain, action);
        }
    };

    public HeatExchangerBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.HEATER_HEATEX.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, HeatExchangerBlockEntity exchanger) {
        if (!level.isClientSide) exchanger.serverTick((ServerLevel) level, position, state);
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        refreshIdentifier();
        setupTanks();
        heatEnergy = (int) (heatEnergy * 0.999D);
        tryConvert(level.getGameTime());
        pushOutput(level, position, state.getValue(HeatExchangerBlock.FACING));
        syncIfChanged(level, position, state);
    }

    private void refreshIdentifier() {
        ItemStack identifier = items.get(IDENTIFIER);
        if (identifier.getItem() instanceof FluidIdentifierItem) {
            selectInput(FluidIdentifierItem.primary(identifier));
        }
    }

    private void setupTanks() {
        HeatExchangerRecipes.Cooling recipe = HeatExchangerRecipes.get(selectedInput);
        if (recipe == null) {
            selectedInput = FluidIdentifierItem.Selection.NONE;
            input.setFluid(FluidStack.EMPTY);
            output.setFluid(FluidStack.EMPTY);
            return;
        }
        if (!input.isEmpty() && !selectedInput.accepts(input.getFluid().getFluid())) input.setFluid(FluidStack.EMPTY);
        if (!output.isEmpty() && !recipe.output().accepts(output.getFluid().getFluid())) output.setFluid(FluidStack.EMPTY);
    }

    private void tryConvert(long gameTime) {
        if (tickDelay < 1) tickDelay = 1;
        if (gameTime % tickDelay != 0L) return;
        HeatExchangerRecipes.Cooling recipe = HeatExchangerRecipes.get(selectedInput);
        if (recipe == null) return;
        int inputOperations = input.getFluidAmount() / recipe.inputAmount();
        int outputOperations = (TANK_CAPACITY - output.getFluidAmount()) / recipe.outputAmount();
        int operations = Math.min(inputOperations, Math.min(outputOperations, amountToCool));
        if (operations <= 0) return;
        input.drain(recipe.inputAmount() * operations, IFluidHandler.FluidAction.EXECUTE);
        output.fill(new FluidStack(recipe.output().fluid(), recipe.outputAmount() * operations),
                IFluidHandler.FluidAction.EXECUTE);
        heatEnergy += recipe.heatPerOperation() * operations;
        setChanged();
    }

    private void pushOutput(ServerLevel level, BlockPos core, Direction facing) {
        if (output.isEmpty()) return;
        Direction side = facing.getClockWise();
        pushTo(level, core.relative(facing, 2).relative(side), facing);
        pushTo(level, core.relative(facing, 2).relative(side.getOpposite()), facing);
        pushTo(level, core.relative(facing.getOpposite(), 2).relative(side), facing.getOpposite());
        pushTo(level, core.relative(facing.getOpposite(), 2).relative(side.getOpposite()), facing.getOpposite());
    }

    private void pushTo(ServerLevel level, BlockPos target, Direction outward) {
        if (output.isEmpty()) return;
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                target, outward.getOpposite());
        if (handler == null) return;
        int accepted = handler.fill(output.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
        if (accepted > 0) output.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
    }

    private void syncIfChanged(ServerLevel level, BlockPos position, BlockState state) {
        if (input.getFluidAmount() != lastInput || output.getFluidAmount() != lastOutput
                || heatEnergy != lastHeat || selectedInput != lastSelection || level.getGameTime() % 20L == 0L) {
            lastInput = input.getFluidAmount();
            lastOutput = output.getFluidAmount();
            lastHeat = heatEnergy;
            lastSelection = selectedInput;
            level.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS);
        }
        setChanged();
    }

    public void selectInput(FluidIdentifierItem.Selection selection) {
        if (selection == null) selection = FluidIdentifierItem.Selection.NONE;
        if (selectedInput == selection) return;
        selectedInput = selection;
        input.setFluid(FluidStack.EMPTY);
        setChanged();
    }

    public void setCycleControls(int amount, int delay) {
        amountToCool = Math.clamp(amount, 1, TANK_CAPACITY);
        tickDelay = Math.max(delay, 1);
        setChanged();
    }

    public IFluidHandler fluidHandler() { return fluidHandler; }
    public FluidTank inputTank() { return input; }
    public FluidTank outputTank() { return output; }
    public FluidIdentifierItem.Selection selectedInput() { return selectedInput; }
    public FluidIdentifierItem.Selection outputSelection() {
        HeatExchangerRecipes.Cooling recipe = HeatExchangerRecipes.get(selectedInput);
        return recipe == null ? FluidIdentifierItem.Selection.NONE : recipe.output();
    }
    public int amountToCool() { return amountToCool; }
    public int tickDelay() { return tickDelay; }
    public int heatEnergy() { return heatEnergy; }
    public void convertForTest(long gameTime) { setupTanks(); tryConvert(gameTime); }

    @Override public int getHeatStored() { return heatEnergy; }
    @Override public void useUpHeat(int heat) { heatEnergy = Math.max(heatEnergy - heat, 0); setChanged(); }

    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.heaterHeatex");
    }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new HeatExchangerMenu(id, inventory, this, data);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putString("selectedFluid", selectedInput.id());
        tag.put("tank0", input.writeToNBT(registries, new CompoundTag()));
        tag.put("tank1", output.writeToNBT(registries, new CompoundTag()));
        tag.putInt("heatEnergy", heatEnergy);
        tag.putInt("toCool", amountToCool);
        tag.putInt("delay", tickDelay);
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        selectedInput = tag.contains("selectedFluid")
                ? FluidIdentifierItem.Selection.byId(tag.getString("selectedFluid"))
                : FluidIdentifierItem.Selection.COOLANT_HOT;
        if (tag.contains("tank0")) input.readFromNBT(registries, tag.getCompound("tank0"));
        if (tag.contains("tank1")) output.readFromNBT(registries, tag.getCompound("tank1"));
        heatEnergy = tag.getInt("heatEnergy");
        amountToCool = tag.contains("toCool") ? tag.getInt("toCool") : TANK_CAPACITY;
        tickDelay = tag.contains("delay") ? tag.getInt("delay") : 1;
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
        setupTanks();
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("selectedFluid", selectedInput.id());
        tag.put("tank0", input.writeToNBT(registries, new CompoundTag()));
        tag.put("tank1", output.writeToNBT(registries, new CompoundTag()));
        tag.putInt("heatEnergy", heatEnergy);
        tag.putInt("toCool", amountToCool);
        tag.putInt("delay", tickDelay);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        selectedInput = FluidIdentifierItem.Selection.byId(tag.getString("selectedFluid"));
        if (tag.contains("tank0")) input.readFromNBT(registries, tag.getCompound("tank0"));
        if (tag.contains("tank1")) output.readFromNBT(registries, tag.getCompound("tank1"));
        heatEnergy = tag.getInt("heatEnergy");
        amountToCool = tag.getInt("toCool");
        tickDelay = tag.getInt("delay");
        setupTanks();
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.getFirst().isEmpty(); }
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
}
