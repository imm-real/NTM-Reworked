package com.hbm.ntm.blockentity;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.energy.HeProvider;
import com.hbm.ntm.inventory.SteamTurbineMenu;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.item.InfiniteFluidBarrelItem;
import com.hbm.ntm.registry.ModBlockEntities;
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
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/** Seven-slot single-block Steam Turbine. */
public final class SteamTurbineBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, HeProvider {
    public static final int IDENTIFIER_INPUT = 0;
    public static final int IDENTIFIER_OUTPUT = 1;
    public static final int INPUT_CONTAINER = 2;
    public static final int INPUT_CONTAINER_OUTPUT = 3;
    public static final int BATTERY = 4;
    public static final int OUTPUT_CONTAINER = 5;
    public static final int OUTPUT_CONTAINER_OUTPUT = 6;
    public static final int SLOT_COUNT = 7;

    private static final int[] TOP_SLOTS = {BATTERY};
    private static final int[] BOTTOM_SLOTS = {OUTPUT_CONTAINER_OUTPUT};
    private static final int[] SIDE_SLOTS = {BATTERY};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private FluidIdentifierItem.Selection inputSelection = FluidIdentifierItem.Selection.STEAM;
    private FluidIdentifierItem.Selection outputSelection = FluidIdentifierItem.Selection.SPENTSTEAM;
    private final FluidTank input = new FluidTank(inputCapacity(),
            stack -> inputSelection.accepts(stack.getFluid())) {
        @Override protected void onContentsChanged() { SteamTurbineBlockEntity.this.setChanged(); }
    };
    private final FluidTank output = new FluidTank(outputCapacity(),
            stack -> outputSelection.accepts(stack.getFluid())) {
        @Override protected void onContentsChanged() { SteamTurbineBlockEntity.this.setChanged(); }
    };
    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return 2; }
        @Override public FluidStack getFluidInTank(int tank) {
            return tank == 0 ? input.getFluid().copy() : tank == 1 ? output.getFluid().copy() : FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int tank) {
            return tank == 0 ? input.getCapacity() : tank == 1 ? output.getCapacity() : 0;
        }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && inputSelection.accepts(stack.getFluid());
        }
        @Override public int fill(FluidStack resource, FluidAction action) {
            return inputSelection.accepts(resource.getFluid()) ? input.fill(resource, action) : 0;
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !outputSelection.accepts(resource.getFluid())) return FluidStack.EMPTY;
            return output.drain(resource, action);
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            return output.drain(maxDrain, action);
        }
    };

    private long power;
    private int age;
    private Component customName;
    private int lastConsumed;
    private int lastProduced;
    private long lastGenerated;

    private long lastSyncedPower = Long.MIN_VALUE;
    private int lastSyncedInput = Integer.MIN_VALUE;
    private int lastSyncedOutput = Integer.MIN_VALUE;
    private FluidIdentifierItem.Selection lastSyncedInputType;
    private FluidIdentifierItem.Selection lastSyncedOutputType;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int) power;
                case 1 -> (int) (power >>> 32);
                case 2 -> (int) maxPower();
                case 3 -> (int) (maxPower() >>> 32);
                case 4 -> input.getFluidAmount();
                case 5 -> input.getCapacity();
                case 6 -> output.getFluidAmount();
                case 7 -> output.getCapacity();
                case 8 -> inputSelection.ordinal();
                case 9 -> outputSelection.ordinal();
                default -> 0;
            };
        }

        @Override public void set(int index, int value) {
            FluidIdentifierItem.Selection[] values = FluidIdentifierItem.Selection.values();
            switch (index) {
                case 0 -> power = power & 0xFFFFFFFF00000000L | value & 0xFFFFFFFFL;
                case 1 -> power = power & 0xFFFFFFFFL | (long) value << 32;
                case 4 -> setClientTank(input, inputSelection, value);
                case 6 -> setClientTank(output, outputSelection, value);
                case 8 -> inputSelection = selectionByOrdinal(values, value);
                case 9 -> outputSelection = selectionByOrdinal(values, value);
                default -> { }
            }
        }

        @Override public int getCount() { return 10; }
    };

    public SteamTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_TURBINE.get(), pos, state);
    }

    private static FluidIdentifierItem.Selection selectionByOrdinal(
            FluidIdentifierItem.Selection[] values, int ordinal) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FluidIdentifierItem.Selection.NONE;
    }

    private static void setClientTank(FluidTank tank, FluidIdentifierItem.Selection selection, int amount) {
        tank.setFluid(amount <= 0 || selection == FluidIdentifierItem.Selection.NONE
                ? FluidStack.EMPTY
                : new FluidStack(selection.fluid(), Math.min(amount, tank.getCapacity())));
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SteamTurbineBlockEntity turbine) {
        if (!level.isClientSide) turbine.serverTick((ServerLevel) level, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        conformTanks();
        lastConsumed = 0;
        lastProduced = 0;
        lastGenerated = 0L;
        age = age == 0 ? 1 : 0;

        pullInput(level, pos);
        for (Direction direction : Direction.values()) {
            tryProvide(level, pos.relative(direction), direction);
        }
        processIdentifier();
        loadInputContainer();
        chargeBattery();
        power = (long) (power * 0.95D);
        processSteam();
        power = Math.min(Math.max(power, 0L), maxPower());
        pushOutput(level, pos);
        unloadOutputContainer();
        syncIfChanged(level, pos, state);
    }

    private void processIdentifier() {
        ItemStack identifier = items.get(IDENTIFIER_INPUT);
        if (!(identifier.getItem() instanceof FluidIdentifierItem)
                || !items.get(IDENTIFIER_OUTPUT).isEmpty()) return;
        FluidIdentifierItem.Selection selected = FluidIdentifierItem.primary(identifier);
        if (selected == inputSelection) return;
        setInputSelection(selected);
        items.set(IDENTIFIER_OUTPUT, identifier.copy());
        items.set(IDENTIFIER_INPUT, ItemStack.EMPTY);
        setChanged();
    }

    private void setInputSelection(FluidIdentifierItem.Selection selection) {
        if (selection == null) selection = FluidIdentifierItem.Selection.NONE;
        if (selection == inputSelection) return;
        inputSelection = selection;
        input.setFluid(FluidStack.EMPTY);
        setChanged();
    }

    private void setOutputSelection(FluidIdentifierItem.Selection selection) {
        if (selection == null) selection = FluidIdentifierItem.Selection.NONE;
        if (selection == outputSelection) return;
        outputSelection = selection;
        output.setFluid(FluidStack.EMPTY);
        setChanged();
    }

    private void loadInputContainer() {
        ItemStack source = items.get(INPUT_CONTAINER);
        if (source.isEmpty() || inputSelection == FluidIdentifierItem.Selection.NONE) return;
        if (InfiniteFluidBarrelItem.is(source)) {
            if (InfiniteFluidBarrelItem.fillTank(input, inputSelection.fluid()) > 0) setChanged();
            return;
        }
        IFluidHandlerItem handler = source.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) return;
        FluidStack simulated = handler.drain(input.getSpace(), IFluidHandler.FluidAction.SIMULATE);
        if (simulated.isEmpty() || !inputSelection.accepts(simulated.getFluid())) return;
        int accepted = input.fill(simulated, IFluidHandler.FluidAction.SIMULATE);
        if (accepted != simulated.getAmount()) return;
        FluidStack drained = handler.drain(simulated.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
        if (drained.getAmount() != accepted) return;
        ItemStack remainder = handler.getContainer().copy();
        if (!canMerge(items.get(INPUT_CONTAINER_OUTPUT), remainder)) return;
        input.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        source.shrink(1);
        if (source.isEmpty()) items.set(INPUT_CONTAINER, ItemStack.EMPTY);
        mergeOutput(INPUT_CONTAINER_OUTPUT, remainder);
        setChanged();
    }

    private void unloadOutputContainer() {
        if (output.isEmpty() || outputSelection == FluidIdentifierItem.Selection.NONE) return;
        ItemStack source = items.get(OUTPUT_CONTAINER);
        if (source.isEmpty()) return;
        if (InfiniteFluidBarrelItem.is(source)) {
            if (InfiniteFluidBarrelItem.discardTank(output) > 0) setChanged();
            return;
        }
        IFluidHandlerItem handler = source.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) return;
        FluidStack available = output.getFluid().copy();
        int accepted = handler.fill(available, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) return;
        int filled = handler.fill(available.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0) return;
        ItemStack result = handler.getContainer().copy();
        if (!canMerge(items.get(OUTPUT_CONTAINER_OUTPUT), result)) return;
        output.drain(filled, IFluidHandler.FluidAction.EXECUTE);
        source.shrink(1);
        if (source.isEmpty()) items.set(OUTPUT_CONTAINER, ItemStack.EMPTY);
        mergeOutput(OUTPUT_CONTAINER_OUTPUT, result);
        setChanged();
    }

    private void chargeBattery() {
        power = Math.min(Math.max(power, 0L), maxPower());
        ItemStack stack = items.get(BATTERY);
        if (!(stack.getItem() instanceof HeBatteryItem battery)) return;
        long charge = Math.min(Math.min(power, battery.getChargeRate(stack)),
                Math.max(battery.getMaxCharge(stack) - battery.getCharge(stack), 0L));
        if (charge <= 0L) return;
        battery.charge(stack, charge);
        power -= charge;
        setChanged();
    }

    private void processSteam() {
        SteamGrade grade = SteamGrade.forInput(inputSelection);
        if (grade == null || HbmConfig.STEAM_TURBINE_EFFICIENCY.get() <= 0D) {
            setOutputSelection(FluidIdentifierItem.Selection.NONE);
            return;
        }
        setOutputSelection(grade.output());
        int operations = Math.min(input.getFluidAmount() / grade.inputAmount(),
                Math.min(output.getSpace() / grade.outputAmount(),
                        maxSteamPerTick() / grade.inputAmount()));
        if (operations <= 0) return;
        lastConsumed = operations * grade.inputAmount();
        lastProduced = operations * grade.outputAmount();
        input.drain(lastConsumed, IFluidHandler.FluidAction.EXECUTE);
        output.fill(new FluidStack(grade.output().fluid(), lastProduced), IFluidHandler.FluidAction.EXECUTE);
        double generated = operations * (double) grade.heatEnergy() * HbmConfig.STEAM_TURBINE_EFFICIENCY.get();
        power = (long) (power + generated);
        lastGenerated = (long) generated;
    }

    private void pullInput(ServerLevel level, BlockPos pos) {
        if (inputSelection == FluidIdentifierItem.Selection.NONE || input.getSpace() <= 0) return;
        for (Direction direction : Direction.values()) {
            IFluidHandler neighbor = level.getCapability(Capabilities.FluidHandler.BLOCK,
                    pos.relative(direction), direction.getOpposite());
            if (neighbor == null) continue;
            FluidStack request = new FluidStack(inputSelection.fluid(), input.getSpace());
            FluidStack available = neighbor.drain(request, IFluidHandler.FluidAction.SIMULATE);
            if (available.isEmpty() || !inputSelection.accepts(available.getFluid())) continue;
            int accepted = input.fill(available, IFluidHandler.FluidAction.SIMULATE);
            if (accepted <= 0) continue;
            FluidStack drained = neighbor.drain(new FluidStack(inputSelection.fluid(), accepted),
                    IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty()) input.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            if (input.getSpace() <= 0) return;
        }
    }

    private void pushOutput(ServerLevel level, BlockPos pos) {
        if (output.isEmpty()) return;
        for (Direction direction : Direction.values()) {
            IFluidHandler neighbor = level.getCapability(Capabilities.FluidHandler.BLOCK,
                    pos.relative(direction), direction.getOpposite());
            if (neighbor == null) continue;
            int accepted = neighbor.fill(output.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
            if (accepted > 0) output.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
            if (output.isEmpty()) return;
        }
    }

    private void conformTanks() {
        input.setCapacity(inputCapacity());
        output.setCapacity(outputCapacity());
        if (!input.isEmpty() && !inputSelection.accepts(input.getFluid().getFluid())) input.setFluid(FluidStack.EMPTY);
        if (!output.isEmpty() && !outputSelection.accepts(output.getFluid().getFluid())) output.setFluid(FluidStack.EMPTY);
    }

    private void syncIfChanged(ServerLevel level, BlockPos pos, BlockState state) {
        if (power != lastSyncedPower || input.getFluidAmount() != lastSyncedInput
                || output.getFluidAmount() != lastSyncedOutput || inputSelection != lastSyncedInputType
                || outputSelection != lastSyncedOutputType || level.getGameTime() % 20L == 0L) {
            lastSyncedPower = power;
            lastSyncedInput = input.getFluidAmount();
            lastSyncedOutput = output.getFluidAmount();
            lastSyncedInputType = inputSelection;
            lastSyncedOutputType = outputSelection;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
        setChanged();
    }

    private static boolean canMerge(ItemStack target, ItemStack addition) {
        return !addition.isEmpty() && (target.isEmpty()
                || ItemStack.isSameItemSameComponents(target, addition)
                && target.getCount() + addition.getCount() <= target.getMaxStackSize());
    }

    private void mergeOutput(int slot, ItemStack addition) {
        if (items.get(slot).isEmpty()) items.set(slot, addition.copy());
        else items.get(slot).grow(addition.getCount());
    }

    public IFluidHandler fluidHandler(@Nullable Direction side) { return fluidHandler; }
    public FluidTank inputTank() { return input; }
    public FluidTank outputTank() { return output; }
    public FluidIdentifierItem.Selection inputSelection() { return inputSelection; }
    public FluidIdentifierItem.Selection outputSelection() { return outputSelection; }
    public int lastConsumed() { return lastConsumed; }
    public int lastProduced() { return lastProduced; }
    public long lastGenerated() { return lastGenerated; }
    public static int inputCapacity() { return HbmConfig.STEAM_TURBINE_INPUT_CAPACITY.get(); }
    public static int outputCapacity() { return HbmConfig.STEAM_TURBINE_OUTPUT_CAPACITY.get(); }
    public static int maxSteamPerTick() { return HbmConfig.STEAM_TURBINE_MAX_STEAM_PER_TICK.get(); }
    public static long maxPower() { return HbmConfig.STEAM_TURBINE_POWER_CAPACITY.get(); }

    public void setSelectionForTest(FluidIdentifierItem.Selection selection) {
        setInputSelection(selection);
        SteamGrade grade = SteamGrade.forInput(selection);
        setOutputSelection(grade == null ? FluidIdentifierItem.Selection.NONE : grade.output());
    }

    public void processForTest() {
        power = (long) (Math.min(Math.max(power, 0L), maxPower()) * 0.95D);
        processSteam();
        power = Math.min(Math.max(power, 0L), maxPower());
    }

    public void chargeBatteryForTest() { chargeBattery(); }

    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.machineTurbine");
    }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SteamTurbineMenu(id, inventory, this, data);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putLong("power", power);
        tag.putInt("age", age);
        tag.putString("inputType", inputSelection.id());
        tag.putString("outputType", outputSelection.id());
        tag.put("input", input.writeToNBT(registries, new CompoundTag()));
        tag.put("output", output.writeToNBT(registries, new CompoundTag()));
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        power = Math.clamp(tag.getLong("power"), 0L, maxPower());
        age = tag.getInt("age") & 1;
        inputSelection = tag.contains("inputType")
                ? FluidIdentifierItem.Selection.byId(tag.getString("inputType"))
                : FluidIdentifierItem.Selection.STEAM;
        outputSelection = tag.contains("outputType")
                ? FluidIdentifierItem.Selection.byId(tag.getString("outputType"))
                : FluidIdentifierItem.Selection.SPENTSTEAM;
        conformTanks();
        if (tag.contains("input")) input.readFromNBT(registries, tag.getCompound("input"));
        if (tag.contains("output")) output.readFromNBT(registries, tag.getCompound("output"));
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
        conformTanks();
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("power", power);
        tag.putString("inputType", inputSelection.id());
        tag.putString("outputType", outputSelection.id());
        tag.putInt("inputAmount", input.getFluidAmount());
        tag.putInt("outputAmount", output.getFluidAmount());
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        power = Math.clamp(tag.getLong("power"), 0L, maxPower());
        inputSelection = FluidIdentifierItem.Selection.byId(tag.getString("inputType"));
        outputSelection = FluidIdentifierItem.Selection.byId(tag.getString("outputType"));
        conformTanks();
        setClientTank(input, inputSelection, tag.getInt("inputAmount"));
        setClientTank(output, outputSelection, tag.getInt("outputAmount"));
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
                && player.distanceToSqr(worldPosition.getCenter()) <= 64D;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }

    @Override public int[] getSlotsForFace(Direction side) {
        return side == Direction.UP ? TOP_SLOTS : side == Direction.DOWN ? BOTTOM_SLOTS : SIDE_SLOTS;
    }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot == BATTERY && stack.getItem() instanceof HeBatteryItem;
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == IDENTIFIER_INPUT) return stack.getItem() instanceof FluidIdentifierItem;
        if (slot == BATTERY) return stack.getItem() instanceof HeBatteryItem;
        IFluidHandlerItem handler = stack.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) return false;
        if (slot == INPUT_CONTAINER) {
            if (InfiniteFluidBarrelItem.is(stack)) {
                return inputSelection != FluidIdentifierItem.Selection.NONE;
            }
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack contained = handler.getFluidInTank(tank);
                if (!contained.isEmpty() && inputSelection.accepts(contained.getFluid())) return true;
            }
            return false;
        }
        if (slot == OUTPUT_CONTAINER && outputSelection != FluidIdentifierItem.Selection.NONE) {
            return handler.fill(new FluidStack(outputSelection.fluid(), 1_000),
                    IFluidHandler.FluidAction.SIMULATE) > 0;
        }
        return false;
    }

    @Override public boolean canConnect(Direction side) { return side != null; }
    @Override public long getPower() { return power; }
    @Override public void setPower(long power) { this.power = Math.clamp(power, 0L, maxPower()); setChanged(); }
    @Override public long getMaxPower() { return maxPower(); }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }

    public enum SteamGrade {
        STEAM(FluidIdentifierItem.Selection.STEAM, 100,
                FluidIdentifierItem.Selection.SPENTSTEAM, 1, 200),
        DENSE(FluidIdentifierItem.Selection.HOTSTEAM, 1,
                FluidIdentifierItem.Selection.STEAM, 10, 2),
        SUPER_DENSE(FluidIdentifierItem.Selection.SUPERHOTSTEAM, 1,
                FluidIdentifierItem.Selection.HOTSTEAM, 10, 18),
        ULTRA_DENSE(FluidIdentifierItem.Selection.ULTRAHOTSTEAM, 1,
                FluidIdentifierItem.Selection.SUPERHOTSTEAM, 10, 120);

        private final FluidIdentifierItem.Selection input;
        private final int inputAmount;
        private final FluidIdentifierItem.Selection output;
        private final int outputAmount;
        private final int heatEnergy;

        SteamGrade(FluidIdentifierItem.Selection input, int inputAmount,
                   FluidIdentifierItem.Selection output, int outputAmount, int heatEnergy) {
            this.input = input;
            this.inputAmount = inputAmount;
            this.output = output;
            this.outputAmount = outputAmount;
            this.heatEnergy = heatEnergy;
        }

        public FluidIdentifierItem.Selection input() { return input; }
        public int inputAmount() { return inputAmount; }
        public FluidIdentifierItem.Selection output() { return output; }
        public int outputAmount() { return outputAmount; }
        public int heatEnergy() { return heatEnergy; }

        public static SteamGrade forInput(FluidIdentifierItem.Selection selection) {
            for (SteamGrade grade : values()) if (grade.input == selection) return grade;
            return null;
        }
    }
}
