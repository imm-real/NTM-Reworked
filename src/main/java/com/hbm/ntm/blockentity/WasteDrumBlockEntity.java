package com.hbm.ntm.blockentity;

import com.hbm.ntm.inventory.WasteDrumMenu;
import com.hbm.ntm.recipe.WasteDrumRecipes;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Twelve one-item cooling cells from {@code TileEntityWasteDrum}. */
public final class WasteDrumBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SLOT_COUNT = 12;
    private static final int[] AUTOMATION_SLOTS = createSlots();

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private Component customName;

    public WasteDrumBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_WASTE_DRUM.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, WasteDrumBlockEntity drum) {
        if (!level.isClientSide) drum.serverTick((ServerLevel) level, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        int water = adjacentWater(level, pos);
        if (water <= 0) return;
        int rollBound = WasteDrumRecipes.rollBound(water);
        boolean changed = false;

        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) continue;
            if (WasteDrumRecipes.coolEveryTick(stack)) {
                WasteDrumRecipes.coolContinuous(level, stack);
                changed = true;
            } else if (WasteDrumRecipes.isInput(stack) && level.random.nextInt(rollBound) == 0) {
                items.set(slot, WasteDrumRecipes.cooledResult(stack).copyWithCount(1));
                changed = true;
            }
        }

        if (changed) {
            setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public static int adjacentWater(Level level, BlockPos pos) {
        int count = 0;
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).is(Blocks.WATER)) count++;
        }
        return count;
    }

    public void setCustomName(Component name) { customName = name; setChanged(); }

    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.wasteDrum");
    }

    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new WasteDrumMenu(id, inventory, this);
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public int getMaxStackSize() { return 1; }

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
        if (stack.getCount() > 1) stack.setCount(1);
        setChanged();
    }

    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return WasteDrumRecipes.isInput(stack); }
    @Override public void clearContent() { items.clear(); }
    @Override public int[] getSlotsForFace(Direction side) { return AUTOMATION_SLOTS; }

    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return WasteDrumRecipes.mayExtract(stack);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        customName = tag.contains("name")
                ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    private static int[] createSlots() {
        int[] slots = new int[SLOT_COUNT];
        for (int index = 0; index < SLOT_COUNT; index++) slots[index] = index;
        return slots;
    }
}
