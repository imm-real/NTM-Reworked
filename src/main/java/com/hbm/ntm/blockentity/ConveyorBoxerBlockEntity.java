package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.ConveyorBoxerBlock;
import com.hbm.ntm.conveyor.ConveyorBelt;
import com.hbm.ntm.entity.MovingConveyorPackageEntity;
import com.hbm.ntm.inventory.ConveyorBoxerMenu;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ConveyorBoxerBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SLOT_COUNT = 21;
    public static final int MODE_4 = 0;
    public static final int MODE_8 = 1;
    public static final int MODE_16 = 2;
    public static final int MODE_REDSTONE = 3;
    private static final int[] SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20
    };

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return index == 0 ? mode : 0; }
        @Override public void set(int index, int value) { if (index == 0) setMode(value); }
        @Override public int getCount() { return 1; }
    };
    private Component customName;
    private int mode = MODE_4;
    private boolean lastRedstone;

    public ConveyorBoxerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRANE_BOXER.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ConveyorBoxerBlockEntity boxer) {
        boxer.serverTick(level, pos, state);
    }

    private void serverTick(Level level, BlockPos pos, BlockState state) {
        boolean redstone = level.hasNeighborSignal(pos);
        if (mode == MODE_REDSTONE && redstone && !lastRedstone) {
            packageAllOccupied(level, pos, state);
        }
        if (lastRedstone != redstone) {
            lastRedstone = redstone;
            setChanged();
        }

        if (mode != MODE_REDSTONE && level.getGameTime() % 2L == 0L) {
            int requested = switch (mode) {
                case MODE_8 -> 8;
                case MODE_16 -> 16;
                default -> 4;
            };
            packageFullStacks(level, pos, state, requested);
        }
    }

    private void packageAllOccupied(Level level, BlockPos pos, BlockState state) {
        int occupied = 0;
        for (ItemStack stack : items) if (!stack.isEmpty()) occupied++;
        if (occupied == 0 || outputBelt(level, pos, state) == null) return;

        List<ItemStack> contents = new ArrayList<>(occupied);
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty()) {
                // Scan upward while counting backward. Slot order emerges reversed.
                contents.add(0, stack.copy());
                items.set(slot, ItemStack.EMPTY);
            }
        }
        spawnPackage(level, pos, state, contents);
        inventoryChanged();
    }

    private void packageFullStacks(Level level, BlockPos pos, BlockState state, int requested) {
        int fullStacks = 0;
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && stack.getCount() == stack.getMaxStackSize()) fullStacks++;
        }
        if (fullStacks < requested || outputBelt(level, pos, state) == null) return;

        List<ItemStack> contents = new ArrayList<>(requested);
        int remaining = requested;
        for (int slot = 0; slot < items.size() && remaining > 0; slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty() && stack.getCount() == stack.getMaxStackSize()) {
                contents.add(0, stack.copy());
                items.set(slot, ItemStack.EMPTY);
                remaining--;
            }
        }
        spawnPackage(level, pos, state, contents);
        inventoryChanged();
    }

    @Nullable
    private BeltTarget outputBelt(Level level, BlockPos pos, BlockState state) {
        Direction output = state.getValue(ConveyorBoxerBlock.OUTPUT);
        BlockPos beltPos = pos.relative(output);
        BlockState beltState = level.getBlockState(beltPos);
        return beltState.getBlock() instanceof ConveyorBelt belt
                ? new BeltTarget(output, beltPos, beltState, belt) : null;
    }

    private void spawnPackage(Level level, BlockPos pos, BlockState state, List<ItemStack> contents) {
        BeltTarget target = outputBelt(level, pos, state);
        if (target == null) return;
        Vec3 start = pos.getCenter().add(target.output().getStepX() * 0.55D,
                target.output().getStepY() * 0.55D, target.output().getStepZ() * 0.55D);
        Vec3 snap = target.belt().closestSnappingPosition(level, target.pos(), target.state(), start);
        MovingConveyorPackageEntity moving = MovingConveyorPackageEntity.create(level, contents);
        moving.setPos(snap.x, snap.y, snap.z);
        level.addFreshEntity(moving);
    }

    private record BeltTarget(Direction output, BlockPos pos, BlockState state, ConveyorBelt belt) {
    }

    public void insertOrDrop(ItemStack incoming) {
        if (incoming.isEmpty() || level == null || level.isClientSide) return;
        ItemStack remainder = insert(incoming.copy());
        if (!remainder.isEmpty()) {
            level.addFreshEntity(new ItemEntity(level, worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D, remainder));
        }
    }

    public ItemStack insert(ItemStack incoming) {
        if (incoming.isEmpty()) return ItemStack.EMPTY;
        ItemStack remainder = incoming.copy();
        boolean changed = false;

        for (int slot = 0; slot < items.size() && !remainder.isEmpty(); slot++) {
            ItemStack existing = items.get(slot);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remainder)) continue;
            int limit = Math.min(getMaxStackSize(), existing.getMaxStackSize());
            int moved = Math.min(remainder.getCount(), limit - existing.getCount());
            if (moved > 0) {
                existing.grow(moved);
                remainder.shrink(moved);
                changed = true;
            }
        }
        for (int slot = 0; slot < items.size() && !remainder.isEmpty(); slot++) {
            if (!items.get(slot).isEmpty()) continue;
            int moved = Math.min(remainder.getCount(), Math.min(getMaxStackSize(), remainder.getMaxStackSize()));
            items.set(slot, remainder.copyWithCount(moved));
            remainder.shrink(moved);
            changed = true;
        }
        if (changed) inventoryChanged();
        return remainder;
    }

    public int comparatorOutput() {
        float fullness = 0.0F;
        int occupied = 0;
        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            fullness += (float) stack.getCount() / Math.min(getMaxStackSize(), stack.getMaxStackSize());
            occupied++;
        }
        fullness /= items.size();
        return Mth.floor(fullness * 14.0F) + (occupied > 0 ? 1 : 0);
    }

    private void inventoryChanged() {
        setChanged();
        if (level != null) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    public int mode() {
        return mode;
    }

    public void setMode(int mode) {
        int normalized = Math.floorMod(mode, 4);
        if (this.mode == normalized) return;
        this.mode = normalized;
        setChanged();
    }

    public void cycleMode() {
        setMode(mode + 1);
    }

    public ContainerData dataAccess() {
        return data;
    }

    public void setCustomName(Component customName) {
        this.customName = customName;
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.craneBoxer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ConveyorBoxerMenu(id, inventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putByte("mode", (byte) mode);
        tag.putBoolean("lastRedstone", lastRedstone);
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        mode = Mth.clamp(tag.getByte("mode"), MODE_4, MODE_REDSTONE);
        lastRedstone = tag.getBoolean("lastRedstone");
        customName = tag.contains("name")
                ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public int getMaxStackSize() { return 64; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) inventoryChanged();
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack.copyWithCount(Math.min(stack.getCount(),
                Math.min(getMaxStackSize(), stack.getMaxStackSize()))));
        inventoryChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()) < 400.0D;
    }
    @Override public void clearContent() { items.clear(); inventoryChanged(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return true; }
    @Override public int[] getSlotsForFace(Direction side) { return SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return true; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return true; }
}
