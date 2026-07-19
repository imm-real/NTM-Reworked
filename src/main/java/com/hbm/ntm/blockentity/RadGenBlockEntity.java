package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.RadGenBlock;
import com.hbm.ntm.energy.HeProvider;
import com.hbm.ntm.inventory.RadGenMenu;
import com.hbm.ntm.recipe.RadGenFuelRecipes;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/** Twelve radiation queues feeding one needlessly large battery. */
public final class RadGenBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider, HeProvider {
    public static final int LANE_COUNT = 12;
    public static final int SLOT_COUNT = 24;
    public static final long MAX_POWER = 1_000_000L;

    private static final int[] AUTOMATION_SLOTS = createAutomationSlots();

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final ItemStack[] processing = new ItemStack[LANE_COUNT];
    private final int[] progress = new int[LANE_COUNT];
    private final int[] maxProgress = new int[LANE_COUNT];
    private final int[] production = new int[LANE_COUNT];
    private long power;
    private int output;
    private boolean isOn;
    private Component customName;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            if (index == 0) return (int) power;
            if (index == 1) return (int) (power >>> 32);
            if (index == 2) return output;
            if (index == 3) return isOn ? 1 : 0;
            if (index >= 4 && index < 16) return progress[index - 4];
            if (index >= 16 && index < 28) return maxProgress[index - 16];
            if (index >= 28 && index < 40) return production[index - 28];
            return 0;
        }

        @Override public void set(int index, int value) {
            if (index == 0) power = power & 0xFFFFFFFF00000000L | value & 0xFFFFFFFFL;
            else if (index == 1) power = power & 0xFFFFFFFFL | (long) value << 32;
            else if (index == 2) output = value;
            else if (index == 3) isOn = value != 0;
            else if (index >= 4 && index < 16) progress[index - 4] = value;
            else if (index >= 16 && index < 28) maxProgress[index - 16] = value;
            else if (index >= 28 && index < 40) production[index - 28] = value;
        }

        @Override public int getCount() { return 40; }
    };

    public RadGenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_RADGEN.get(), pos, state);
        Arrays.fill(processing, ItemStack.EMPTY);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RadGenBlockEntity radGen) {
        if (!level.isClientSide) radGen.serverTick((ServerLevel) level, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        output = 0;

        // Export yesterday's power before making today's.
        Direction facing = state.getValue(RadGenBlock.FACING);
        tryProvide(level, RadGenBlock.powerTarget(pos, facing), facing.getOpposite());

        for (int lane = 0; lane < LANE_COUNT; lane++) tryLoadLane(lane);

        isOn = false;
        for (int lane = 0; lane < LANE_COUNT; lane++) processLane(lane);
        if (power > MAX_POWER) power = MAX_POWER;

        setChanged();
        // All twelve queues are live GUI data, so ship them every tick.
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    private void tryLoadLane(int lane) {
        if (!processing[lane].isEmpty()) return;
        ItemStack input = items.get(lane);
        RadGenFuelRecipes.Fuel fuel = RadGenFuelRecipes.find(input);
        if (fuel == null || !canAcceptOutput(lane, fuel.output())) return;

        progress[lane] = 0;
        maxProgress[lane] = fuel.duration();
        production[lane] = fuel.power();
        processing[lane] = RadGenFuelRecipes.processingCopy(input);
        input.shrink(1);
        if (input.isEmpty()) items.set(lane, ItemStack.EMPTY);
        setChanged();
    }

    private boolean canAcceptOutput(int lane, ItemStack output) {
        if (output.isEmpty()) return true;
        ItemStack existing = items.get(lane + LANE_COUNT);
        return existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, output)
                && existing.getCount() + output.getCount() <= existing.getMaxStackSize();
    }

    private void processLane(int lane) {
        ItemStack active = processing[lane];
        if (active.isEmpty()) return;
        isOn = true;
        power += production[lane];
        output += production[lane];
        progress[lane]++;
        if (progress[lane] < maxProgress[lane]) return;

        progress[lane] = 0;
        RadGenFuelRecipes.Fuel fuel = RadGenFuelRecipes.find(active);
        ItemStack result = fuel == null ? ItemStack.EMPTY : fuel.output();
        if (!result.isEmpty()) {
            int outputSlot = lane + LANE_COUNT;
            ItemStack existing = items.get(outputSlot);
            if (existing.isEmpty()) items.set(outputSlot, result);
            else existing.grow(result.getCount());
        }
        processing[lane] = ItemStack.EMPTY;
        setChanged();
    }

    @Override public long getPower() { return power; }
    @Override public void setPower(long value) { power = Math.clamp(value, 0L, MAX_POWER); setChanged(); }
    @Override public long getMaxPower() { return MAX_POWER; }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }

    public int output() { return output; }
    public boolean isOn() { return isOn; }
    public int progress(int lane) { return progress[lane]; }
    public int maxProgress(int lane) { return maxProgress[lane]; }
    public int production(int lane) { return production[lane]; }
    public ItemStack processing(int lane) { return processing[lane].copy(); }
    public ContainerData dataAccess() { return data; }

    public void setCustomName(Component name) { customName = name; setChanged(); }

    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.radGen");
    }

    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RadGenMenu(id, inventory, this, data);
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }

    @Override public boolean isEmpty() {
        for (ItemStack stack : items) if (!stack.isEmpty()) return false;
        return true;
    }

    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
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
                && player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) <= 128.0D;
    }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot >= LANE_COUNT || RadGenFuelRecipes.find(stack) == null) return false;
        ItemStack lane = items.get(slot);
        if (lane.isEmpty()) return true;
        int size = lane.getCount();
        for (int other = 0; other < LANE_COUNT; other++) {
            ItemStack existing = items.get(other);
            if (existing.isEmpty()) return false;
            if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < size) return false;
        }
        return true;
    }

    @Override public void clearContent() { items.clear(); }
    @Override public int[] getSlotsForFace(Direction side) { return AUTOMATION_SLOTS; }

    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= LANE_COUNT;
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putIntArray("progress", progress);
        tag.putIntArray("maxProgress", maxProgress);
        tag.putIntArray("production", production);
        tag.putLong("power", power);
        tag.putBoolean("isOn", isOn);

        ListTag active = new ListTag();
        for (int lane = 0; lane < LANE_COUNT; lane++) {
            if (processing[lane].isEmpty()) continue;
            CompoundTag entry = (CompoundTag) processing[lane].save(registries);
            entry.putByte("slot", (byte) lane);
            active.add(entry);
        }
        tag.put("progressing", active);
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        int[] savedProgress = tag.getIntArray("progress");
        if (savedProgress.length != LANE_COUNT) {
            Arrays.fill(progress, 0);
            Arrays.fill(maxProgress, 0);
            Arrays.fill(production, 0);
            Arrays.fill(processing, ItemStack.EMPTY);
            return;
        }
        System.arraycopy(savedProgress, 0, progress, 0, LANE_COUNT);
        copyArray(tag.getIntArray("maxProgress"), maxProgress);
        copyArray(tag.getIntArray("production"), production);
        power = Math.clamp(tag.getLong("power"), 0L, MAX_POWER);
        isOn = tag.getBoolean("isOn");
        Arrays.fill(processing, ItemStack.EMPTY);
        ListTag active = tag.getList("progressing", Tag.TAG_COMPOUND);
        for (int index = 0; index < active.size(); index++) {
            CompoundTag entry = active.getCompound(index);
            int lane = entry.getByte("slot");
            if (lane >= 0 && lane < LANE_COUNT) processing[lane] = ItemStack.parseOptional(registries, entry);
        }
        customName = tag.contains("name")
                ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("progress", progress);
        tag.putIntArray("maxProgress", maxProgress);
        tag.putIntArray("production", production);
        tag.putLong("power", power);
        tag.putBoolean("isOn", isOn);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        copyArray(tag.getIntArray("progress"), progress);
        copyArray(tag.getIntArray("maxProgress"), maxProgress);
        copyArray(tag.getIntArray("production"), production);
        power = Math.clamp(tag.getLong("power"), 0L, MAX_POWER);
        isOn = tag.getBoolean("isOn");
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static void copyArray(int[] source, int[] destination) {
        Arrays.fill(destination, 0);
        System.arraycopy(source, 0, destination, 0, Math.min(source.length, destination.length));
    }

    private static int[] createAutomationSlots() {
        int[] slots = new int[SLOT_COUNT];
        for (int index = 0; index < SLOT_COUNT; index++) slots[index] = index;
        return slots;
    }
}
