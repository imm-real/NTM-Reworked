package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.ElectricFurnaceBlock;
import com.hbm.ntm.energy.HeReceiver;
import com.hbm.ntm.inventory.ElectricFurnaceMenu;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.item.MachineUpgradeItem;
import com.hbm.ntm.pollution.PollutionData;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Exact one-block 1.7.10 Electric Furnace processing, HE, upgrade, and sided-inventory rules. */
public final class ElectricFurnaceBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, HeReceiver {
    public static final int BATTERY = 0;
    public static final int INPUT = 1;
    public static final int OUTPUT = 2;
    public static final int UPGRADE = 3;
    public static final int SLOT_COUNT = 4;
    public static final long MAX_POWER = 100_000L;
    public static final int BASE_MAX_PROGRESS = 100;
    public static final int BASE_CONSUMPTION = 50;
    public static final int DATA_COUNT = 6;
    private static final float SOOT_PER_SECOND = 1.0F / 25.0F;
    private static final int[] AUTOMATION_SLOTS = {BATTERY, INPUT, OUTPUT};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> maxProgress;
                case 2 -> (int) power;
                case 3 -> (int) (power >>> 32);
                case 4 -> consumption;
                case 5 -> lit ? 1 : 0;
                default -> 0;
            };
        }

        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = value;
                case 1 -> maxProgress = value;
                case 2 -> power = power & 0xFFFFFFFF00000000L | value & 0xFFFFFFFFL;
                case 3 -> power = power & 0xFFFFFFFFL | (long) value << 32;
                case 4 -> consumption = value;
                case 5 -> lit = value != 0;
                default -> { }
            }
        }

        @Override public int getCount() { return DATA_COUNT; }
    };

    private Component customName;
    private long power;
    private int progress;
    private int maxProgress = BASE_MAX_PROGRESS;
    private int consumption = BASE_CONSUMPTION;
    private int cooldown;
    private boolean lit;
    private long lastSyncedPower = Long.MIN_VALUE;
    private int lastSyncedProgress = Integer.MIN_VALUE;
    private int lastSyncedMaxProgress = Integer.MIN_VALUE;
    private int lastSyncedConsumption = Integer.MIN_VALUE;
    private boolean lastSyncedLit;

    public ElectricFurnaceBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_ELECTRIC_FURNACE.get(), position, state);
        lit = state.getValue(ElectricFurnaceBlock.LIT);
    }

    public static void tick(Level level, BlockPos position, BlockState state, ElectricFurnaceBlockEntity furnace) {
        if (!level.isClientSide) furnace.serverTick((ServerLevel) level, position, state);
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        if (cooldown > 0) cooldown--;
        dischargeBattery();
        if (level.getGameTime() % 40L == 0L) {
            for (Direction direction : Direction.values()) {
                trySubscribe(level, position.relative(direction), direction);
            }
        }

        int speedLevel = upgradeLevel(MachineUpgradeItem.Type.SPEED);
        int powerLevel = upgradeLevel(MachineUpgradeItem.Type.POWER);
        maxProgress = BASE_MAX_PROGRESS - speedLevel * 25 + powerLevel * 10;
        consumption = BASE_CONSUMPTION + speedLevel * 50 - powerLevel * 15;

        if (!hasPower()) cooldown = 20;

        if (hasPower() && canProcess()) {
            progress++;
            power -= consumption;
            if (level.getGameTime() % 20L == 0L) {
                PollutionData.get(level).increment(position, PollutionData.Type.SOOT, SOOT_PER_SECOND);
            }
            if (progress >= maxProgress) {
                progress = 0;
                processItem();
            }
        } else {
            progress = 0;
        }

        // Source quirk: do not perform the off/on block swap at a zero-progress handoff
        // when another item can immediately continue.
        boolean trigger = !(hasPower() && canProcess() && progress == 0);
        if (trigger) setLit(level, position, progress > 0);

        setChanged();
        syncIfChanged(level, position);
    }

    private void dischargeBattery() {
        ItemStack stack = items.get(BATTERY);
        if (!(stack.getItem() instanceof HeBatteryItem battery)) return;
        long amount = Math.min(Math.min(MAX_POWER - power, battery.getDischargeRate(stack)),
                battery.getCharge(stack));
        if (amount > 0L) {
            battery.discharge(stack, amount);
            power += amount;
        }
    }

    private int upgradeLevel(MachineUpgradeItem.Type type) {
        ItemStack stack = items.get(UPGRADE);
        return stack.getItem() instanceof MachineUpgradeItem upgrade && upgrade.type() == type
                ? Math.min(upgrade.level(), 3) : 0;
    }

    public boolean hasPower() { return power >= consumption; }

    public boolean canProcess() {
        if (items.get(INPUT).isEmpty() || cooldown > 0) return false;
        ItemStack result = smeltingResult(items.get(INPUT));
        if (result.isEmpty()) return false;
        ItemStack output = items.get(OUTPUT);
        if (output.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= Math.min(output.getMaxStackSize(), getMaxStackSize());
    }

    private ItemStack smeltingResult(ItemStack input) {
        if (level == null || input.isEmpty()) return ItemStack.EMPTY;
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, recipeInput, level)
                .map(holder -> holder.value().assemble(recipeInput, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    private void processItem() {
        if (!canProcess()) return;
        ItemStack result = smeltingResult(items.get(INPUT));
        if (items.get(OUTPUT).isEmpty()) items.set(OUTPUT, result.copy());
        else items.get(OUTPUT).grow(result.getCount());
        removeItem(INPUT, 1);
    }

    private void setLit(ServerLevel level, BlockPos position, boolean value) {
        lit = value;
        BlockState current = level.getBlockState(position);
        if (current.hasProperty(ElectricFurnaceBlock.LIT)
                && current.getValue(ElectricFurnaceBlock.LIT) != value) {
            level.setBlock(position, current.setValue(ElectricFurnaceBlock.LIT, value), Block.UPDATE_CLIENTS);
        }
    }

    private void syncIfChanged(ServerLevel level, BlockPos position) {
        if (power != lastSyncedPower || progress != lastSyncedProgress
                || maxProgress != lastSyncedMaxProgress || consumption != lastSyncedConsumption
                || lit != lastSyncedLit) {
            lastSyncedPower = power;
            lastSyncedProgress = progress;
            lastSyncedMaxProgress = maxProgress;
            lastSyncedConsumption = consumption;
            lastSyncedLit = lit;
            BlockState current = level.getBlockState(position);
            level.sendBlockUpdated(position, current, current, Block.UPDATE_CLIENTS);
        }
    }

    public int progress() { return progress; }
    public int maxProgress() { return maxProgress; }
    public int consumption() { return consumption; }
    public int cooldown() { return cooldown; }
    public boolean lit() { return lit; }
    public ContainerData dataAccess() { return data; }
    public void setProgressForTest(int value) { progress = value; }
    public void setCooldownForTest(int value) { cooldown = value; }

    @Override public long getPower() { return power; }
    @Override public void setPower(long power) { this.power = Math.max(0L, Math.min(power, MAX_POWER)); }
    @Override public long getMaxPower() { return MAX_POWER; }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putLong("power", power);
        tag.putInt("progress", progress);
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        power = Math.max(0L, Math.min(tag.getLong("power"), MAX_POWER));
        progress = tag.getInt("progress");
        customName = tag.contains("name")
                ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
        lit = getBlockState().hasProperty(ElectricFurnaceBlock.LIT)
                && getBlockState().getValue(ElectricFurnaceBlock.LIT);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("power", power);
        tag.putInt("progress", progress);
        tag.putInt("maxProgress", maxProgress);
        tag.putInt("consumption", consumption);
        tag.putBoolean("lit", lit);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        power = tag.getLong("power");
        progress = tag.getInt("progress");
        maxProgress = tag.getInt("maxProgress");
        consumption = tag.getInt("consumption");
        lit = tag.getBoolean("lit");
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.electricFurnace");
    }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ElectricFurnaceMenu(id, inventory, this, data);
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
                && player.distanceToSqr(worldPosition.getCenter()) <= 64.0D;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == BATTERY) return stack.getItem() instanceof HeBatteryItem;
        if (slot == INPUT) return !smeltingResult(stack).isEmpty();
        if (slot == UPGRADE) return stack.getItem() instanceof MachineUpgradeItem;
        return false;
    }

    @Override public int[] getSlotsForFace(Direction side) { return AUTOMATION_SLOTS.clone(); }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (slot == BATTERY && stack.getItem() instanceof HeBatteryItem battery) {
            return battery.getCharge(stack) == 0L;
        }
        return slot == OUTPUT;
    }
}
