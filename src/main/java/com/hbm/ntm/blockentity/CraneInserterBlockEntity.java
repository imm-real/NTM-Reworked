package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.AbstractCraneBlock;
import com.hbm.ntm.inventory.CraneInserterMenu;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.Nullable;

/** Source Conveyor Inserter with its 21-slot overflow buffer and destroy-overflow switch. */
public final class CraneInserterBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SLOT_COUNT = 21;
    private static final int[] SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20
    };

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private boolean destroyer = true;
    private Component customName;
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return index == 0 && destroyer ? 1 : 0; }
        @Override public void set(int index, int value) { if (index == 0) destroyer = value != 0; }
        @Override public int getCount() { return 1; }
    };

    public CraneInserterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRANE_INSERTER.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CraneInserterBlockEntity inserter) {
        inserter.serverTick(level, pos, state);
    }

    private void serverTick(Level level, BlockPos pos, BlockState state) {
        if (level.hasNeighborSignal(pos)) return;
        IItemHandler target = targetHandler();
        if (target == null) return;
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) continue;
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, stack.copy(), false);
            if (remainder.getCount() != stack.getCount()) {
                items.set(slot, remainder);
                inventoryChanged();
                return;
            }
            ItemStack single = stack.copyWithCount(1);
            ItemStack singleRemainder = ItemHandlerHelper.insertItemStacked(target, single, false);
            if (singleRemainder.isEmpty()) {
                stack.shrink(1);
                if (stack.isEmpty()) items.set(slot, ItemStack.EMPTY);
                inventoryChanged();
                return;
            }
        }
    }

    public void accept(ItemStack incoming) {
        if (incoming.isEmpty() || level == null || level.isClientSide) return;
        ItemStack remainder = incoming.copy();
        if (!level.hasNeighborSignal(worldPosition)) {
            IItemHandler target = targetHandler();
            if (target != null) remainder = ItemHandlerHelper.insertItemStacked(target, remainder, false);
        }
        remainder = insertBuffer(remainder);
        if (!remainder.isEmpty() && !destroyer) {
            level.addFreshEntity(new ItemEntity(level, worldPosition.getX() + .5D,
                    worldPosition.getY() + .5D, worldPosition.getZ() + .5D, remainder));
        }
    }

    @Nullable
    private IItemHandler targetHandler() {
        if (level == null) return null;
        Direction output = getBlockState().getValue(AbstractCraneBlock.OUTPUT);
        BlockPos targetPos = worldPosition.relative(output);
        Direction face = output.getOpposite();
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, face);
        if (handler != null) return handler;
        if (level.getBlockEntity(targetPos) instanceof WorldlyContainer sided) return new SidedInvWrapper(sided, face);
        if (level.getBlockEntity(targetPos) instanceof net.minecraft.world.Container container) return new InvWrapper(container);
        return null;
    }

    private ItemStack insertBuffer(ItemStack incoming) {
        ItemStack remainder = incoming.copy();
        boolean changed = false;
        for (int slot = 0; slot < items.size() && !remainder.isEmpty(); slot++) {
            ItemStack existing = items.get(slot);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remainder)) continue;
            int moved = Math.min(remainder.getCount(), Math.min(64, existing.getMaxStackSize()) - existing.getCount());
            if (moved > 0) {
                existing.grow(moved);
                remainder.shrink(moved);
                changed = true;
            }
        }
        for (int slot = 0; slot < items.size() && !remainder.isEmpty(); slot++) {
            if (!items.get(slot).isEmpty()) continue;
            int moved = Math.min(remainder.getCount(), Math.min(64, remainder.getMaxStackSize()));
            items.set(slot, remainder.copyWithCount(moved));
            remainder.shrink(moved);
            changed = true;
        }
        if (changed) inventoryChanged();
        return remainder;
    }

    public int comparatorOutput() {
        float fullness = 0F;
        int occupied = 0;
        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            fullness += (float) stack.getCount() / Math.min(64, stack.getMaxStackSize());
            occupied++;
        }
        return Mth.floor(fullness / items.size() * 14F) + (occupied > 0 ? 1 : 0);
    }

    public boolean destroyer() { return destroyer; }
    public void toggleDestroyer() { destroyer = !destroyer; setChanged(); }
    public ContainerData dataAccess() { return data; }
    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.craneInserter");
    }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CraneInserterMenu(id, inventory, this, data);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putBoolean("destroyer", destroyer);
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        destroyer = tag.getBoolean("destroyer");
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    private void inventoryChanged() {
        setChanged();
        if (level != null) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) inventoryChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack.copyWithCount(Math.min(stack.getCount(), Math.min(64, stack.getMaxStackSize()))));
        inventoryChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()) < 400D;
    }
    @Override public void clearContent() { items.clear(); inventoryChanged(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return true; }
    @Override public int[] getSlotsForFace(Direction side) { return SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return true; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return true; }
}
