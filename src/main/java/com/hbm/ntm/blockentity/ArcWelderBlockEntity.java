package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.ArcWelderBlock;
import com.hbm.ntm.energy.HeReceiver;
import com.hbm.ntm.inventory.ArcWelderMenu;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.item.MachineUpgradeItem;
import com.hbm.ntm.recipe.ArcWelderRecipes;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModParticles;
import com.hbm.ntm.registry.ModSounds;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

public final class ArcWelderBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider, HeReceiver {
    public static final int INPUT_START = 0;
    public static final int INPUT_END = 3;
    public static final int OUTPUT = 3;
    public static final int BATTERY = 4;
    public static final int FLUID_IDENTIFIER = 5;
    public static final int UPGRADE_START = 6;
    public static final int UPGRADE_END = 8;
    public static final int SLOT_COUNT = 8;
    public static final int TANK_CAPACITY = 24_000;
    private static final int[] GENERIC_AUTOMATION_SLOTS = {1, 3};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final FluidTank tank = new FluidTank(TANK_CAPACITY) {
        @Override protected void onContentsChanged() { ArcWelderBlockEntity.this.setChanged(); }
    };
    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tankIndex) { return tank.getFluid(); }
        @Override public int getTankCapacity(int tankIndex) { return tank.getCapacity(); }
        @Override public boolean isFluidValid(int tankIndex, FluidStack stack) {
            return identifierSelection().accepts(stack.getFluid());
        }
        @Override public int fill(FluidStack stack, FluidAction action) {
            return isFluidValid(0, stack) ? tank.fill(stack, action) : 0;
        }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int amount, FluidAction action) { return FluidStack.EMPTY; }
    };
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> processTime;
                case 2 -> (int) power;
                case 3 -> (int) (power >>> 32);
                case 4 -> (int) maxPower;
                case 5 -> (int) (maxPower >>> 32);
                case 6 -> (int) consumption;
                case 7 -> (int) (consumption >>> 32);
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = value;
                case 1 -> processTime = value;
                case 2 -> power = power & 0xFFFFFFFF00000000L | value & 0xFFFFFFFFL;
                case 3 -> power = power & 0xFFFFFFFFL | (long) value << 32;
                case 4 -> maxPower = maxPower & 0xFFFFFFFF00000000L | value & 0xFFFFFFFFL;
                case 5 -> maxPower = maxPower & 0xFFFFFFFFL | (long) value << 32;
                case 6 -> consumption = consumption & 0xFFFFFFFF00000000L | value & 0xFFFFFFFFL;
                case 7 -> consumption = consumption & 0xFFFFFFFFL | (long) value << 32;
                default -> { }
            }
        }
        @Override public int getCount() { return 8; }
    };

    private Component customName;
    private long power;
    private long maxPower = 2_000L;
    private long consumption = 100L;
    private int progress;
    private int processTime = 1;
    private FluidIdentifierItem.Selection selectedFluid = FluidIdentifierItem.Selection.NONE;
    private ItemStack display = ItemStack.EMPTY;
    private long lastPower = Long.MIN_VALUE;
    private int lastProgress = Integer.MIN_VALUE;
    private ItemStack lastDisplay = ItemStack.EMPTY;

    public ArcWelderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_ARC_WELDER.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ArcWelderBlockEntity welder) {
        if (!level.isClientSide) welder.serverTick((ServerLevel) level, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        long previousPower = power;
        long previousMaxPower = maxPower;
        long previousConsumption = consumption;
        int previousProgress = progress;
        int previousProcessTime = processTime;

        // Source chargeTEFromItems receives getPower(), which clamps malformed/NBT-overfilled
        // state to the current buffer before the battery or recipe logic runs.
        power = getPower();
        refreshIdentifiedFluid();
        dischargeBattery();
        if (level.getGameTime() % 20L == 0L) subscribeConnections(level, pos, state);

        ArcWelderRecipes.ArcWelderRecipe recipe = ArcWelderRecipes.find(
                items.get(0), items.get(1), items.get(2));
        display = recipe == null ? ItemStack.EMPTY : recipe.output().copyWithCount(1);
        long intendedMaxPower;
        if (recipe != null) {
            int speed = upgradeLevel(MachineUpgradeItem.Type.SPEED);
            int saving = upgradeLevel(MachineUpgradeItem.Type.POWER);
            int overdrive = upgradeLevel(MachineUpgradeItem.Type.OVERDRIVE);
            processTime = recipe.duration() - recipe.duration() * speed / 6 + recipe.duration() * saving / 3;
            consumption = recipe.consumption() + recipe.consumption() * speed
                    - recipe.consumption() * saving / 6;
            consumption *= 1L << overdrive;
            intendedMaxPower = consumption * 20L;

            if (canProcess(recipe)) {
                progress += 1 + overdrive;
                power -= consumption;
                emitParticles(level, pos, state);
                if (progress >= processTime) {
                    progress = 0;
                    consumeItems(recipe);
                    ItemStack output = items.get(OUTPUT);
                    if (output.isEmpty()) items.set(OUTPUT, recipe.output().copy());
                    else output.grow(recipe.output().getCount());
                }
            } else progress = 0;
        } else {
            progress = 0;
            processTime = 1;
            consumption = 100L;
            intendedMaxPower = 2_000L;
        }
        maxPower = Math.max(intendedMaxPower, power);

        if (power != lastPower || progress != lastProgress || !ItemStack.matches(display, lastDisplay)
                || level.getGameTime() % 20L == 0L) {
            lastPower = power;
            lastProgress = progress;
            lastDisplay = display.copy();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
        if (power != previousPower || maxPower != previousMaxPower
                || consumption != previousConsumption || progress != previousProgress
                || processTime != previousProcessTime) setChanged();
    }

    private void emitParticles(ServerLevel level, BlockPos core, BlockState state) {
        if (level.getGameTime() % 2L != 0L) return;
        Direction direction = state.getValue(ArcWelderBlock.FACING);
        double x = core.getX() + 0.5D - direction.getStepX() * 0.5D;
        double y = core.getY() + 1.25D;
        double z = core.getZ() + 0.5D - direction.getStepZ() * 0.5D;
        if (level.getGameTime() % 20L == 0L) {
            for (int index = 0; index < 5; index++) {
                level.sendParticles(ModParticles.TAU_SPARK.get(), x, y, z, 0,
                        level.random.nextGaussian() * 0.05D, 0.05D,
                        level.random.nextGaussian() * 0.05D, 1D);
            }
        }
        level.sendParticles(ModParticles.TAU_HADRON.get(), x, y, z, 1, 0D, 0D, 0D, 0D);
    }

    @Nullable private FluidIdentifierItem.Selection installedIdentifierSelection() {
        ItemStack identifier = items.get(FLUID_IDENTIFIER);
        return identifier.getItem() instanceof FluidIdentifierItem
                ? FluidIdentifierItem.primary(identifier) : null;
    }

    private FluidIdentifierItem.Selection identifierSelection() {
        return selectedFluid;
    }

    private void refreshIdentifiedFluid() {
        FluidIdentifierItem.Selection installed = installedIdentifierSelection();
        // Empty identifier means no-op: selected type and contents remain configured.
        if (installed == null || installed == selectedFluid) return;
        selectedFluid = installed;
        if (!tank.isEmpty() && !selectedFluid.accepts(tank.getFluid().getFluid())) {
            tank.setFluid(FluidStack.EMPTY);
        }
        setChanged();
    }

    private void dischargeBattery() {
        ItemStack stack = items.get(BATTERY);
        if (!(stack.getItem() instanceof HeBatteryItem battery)) return;
        long amount = Math.min(Math.min(Math.max(maxPower - power, 0L), battery.getDischargeRate(stack)),
                battery.getCharge(stack));
        if (amount > 0L) { battery.discharge(stack, amount); power += amount; }
    }

    private void subscribeConnections(ServerLevel level, BlockPos core, BlockState state) {
        Direction direction = state.getValue(ArcWelderBlock.FACING);
        Direction side = direction.getClockWise();
        BlockPos[] positions = {
                core.relative(direction), core.relative(direction).relative(side),
                core.relative(direction).relative(side.getOpposite()),
                core.relative(direction.getOpposite(), 2),
                core.relative(direction.getOpposite(), 2).relative(side),
                core.relative(direction.getOpposite(), 2).relative(side.getOpposite()),
                core.relative(side, 2), core.relative(direction.getOpposite()).relative(side, 2),
                core.relative(side.getOpposite(), 2),
                core.relative(direction.getOpposite()).relative(side.getOpposite(), 2)
        };
        Direction[] faces = {direction, direction, direction,
                direction.getOpposite(), direction.getOpposite(), direction.getOpposite(),
                side, side, side.getOpposite(), side.getOpposite()};
        for (int index = 0; index < positions.length; index++) trySubscribe(level, positions[index], faces[index]);
    }

    public boolean canProcess(ArcWelderRecipes.ArcWelderRecipe recipe) {
        if (power < consumption) return false;
        if (recipe.fluid() != null) {
            if (tank.isEmpty() || !tank.getFluid().is(recipe.fluid().getFluid())
                    || tank.getFluidAmount() < recipe.fluid().getAmount()) return false;
        }
        ItemStack output = items.get(OUTPUT);
        return output.isEmpty() || recipe.matchesOutput(output)
                && output.getCount() + recipe.output().getCount() <= output.getMaxStackSize();
    }

    private void consumeItems(ArcWelderRecipes.ArcWelderRecipe recipe) {
        for (ArcWelderRecipes.Input input : recipe.ingredients()) {
            for (int slot = INPUT_START; slot < INPUT_END; slot++) {
                if (input.matches(items.get(slot))) { removeItem(slot, input.count()); break; }
            }
        }
        if (recipe.fluid() != null) tank.drain(recipe.fluid().getAmount(), IFluidHandler.FluidAction.EXECUTE);
    }

    private int upgradeLevel(MachineUpgradeItem.Type type) {
        int level = 0;
        for (int slot = UPGRADE_START; slot < UPGRADE_END; slot++) {
            if (items.get(slot).getItem() instanceof MachineUpgradeItem upgrade && upgrade.type() == type) {
                level += upgrade.level();
            }
        }
        return Math.min(level, 3);
    }

    public int progress() { return progress; }
    public int processTime() { return processTime; }
    public long consumption() { return consumption; }
    public ItemStack display() { return display; }
    public FluidTank tank() { return tank; }
    public IFluidHandler fluidHandler() { return fluidHandler; }
    public FluidIdentifierItem.Selection configuredFluid() { return selectedFluid; }

    @Override public long getPower() { return Math.max(Math.min(power, maxPower), 0L); }
    @Override public void setPower(long value) { power = value; }
    @Override public long getMaxPower() { return maxPower; }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putLong("power", power); tag.putLong("maxPower", maxPower); tag.putLong("consumption", consumption);
        tag.putInt("progress", progress); tag.putInt("processTime", processTime);
        tag.putString("selectedFluid", selectedFluid.id());
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        power = tag.getLong("power"); maxPower = tag.getLong("maxPower"); consumption = tag.getLong("consumption");
        progress = tag.getInt("progress"); processTime = Math.max(1, tag.getInt("processTime"));
        if (tag.contains("tank")) tank.readFromNBT(registries, tag.getCompound("tank"));
        if (tag.contains("selectedFluid")) {
            selectedFluid = FluidIdentifierItem.Selection.byId(tag.getString("selectedFluid"));
        } else {
            selectedFluid = selectionForTankContents();
        }
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("power", power); tag.putLong("maxPower", maxPower); tag.putLong("consumption", consumption);
        tag.putInt("progress", progress); tag.putInt("processTime", processTime);
        tag.putString("selectedFluid", selectedFluid.id());
        if (!display.isEmpty()) tag.put("display", display.save(registries));
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        power = tag.getLong("power"); maxPower = tag.getLong("maxPower"); consumption = tag.getLong("consumption");
        progress = tag.getInt("progress"); processTime = Math.max(1, tag.getInt("processTime"));
        selectedFluid = FluidIdentifierItem.Selection.byId(tag.getString("selectedFluid"));
        display = tag.contains("display") ? ItemStack.parseOptional(registries, tag.getCompound("display")) : ItemStack.EMPTY;
        if (tag.contains("tank")) tank.readFromNBT(registries, tag.getCompound("tank"));
    }

    private FluidIdentifierItem.Selection selectionForTankContents() {
        if (tank.isEmpty()) return FluidIdentifierItem.Selection.NONE;
        for (FluidIdentifierItem.Selection selection : FluidIdentifierItem.Selection.values()) {
            if (selection.accepts(tank.getFluid().getFluid())) return selection;
        }
        return FluidIdentifierItem.Selection.NONE;
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.machineArcWelder");
    }
    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ArcWelderMenu(id, inventory, this, data);
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
        if (slot >= UPGRADE_START && slot < UPGRADE_END && stack.getItem() instanceof MachineUpgradeItem
                && level != null) level.playSound(null, worldPosition, ModSounds.UPGRADE_PLUG.get(),
                SoundSource.BLOCKS, 1F, 1F);
        setChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getCenter()) <= 128D;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot >= INPUT_START && slot < INPUT_END) return true;
        if (slot == BATTERY) return stack.getItem() instanceof HeBatteryItem;
        if (slot == FLUID_IDENTIFIER) return stack.getItem() instanceof FluidIdentifierItem;
        if (slot >= UPGRADE_START && slot < UPGRADE_END) return stack.getItem() instanceof MachineUpgradeItem;
        return false;
    }
    @Override public int[] getSlotsForFace(Direction side) { return GENERIC_AUTOMATION_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot >= INPUT_START && slot < INPUT_END;
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return slot == OUTPUT; }
}
