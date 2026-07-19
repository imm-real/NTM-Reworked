package com.hbm.ntm.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

abstract class InventoryProxyBlockEntity<T extends WorldlyContainer> extends BlockEntity implements WorldlyContainer {
    protected static final int[] NO_SLOTS = {};

    protected InventoryProxyBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state) {
        super(type, position, state);
    }

    @Nullable
    protected abstract T target();

    @Override
    public int getContainerSize() {
        T target = target();
        return target == null ? 0 : target.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        T target = target();
        return target == null || target.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        T target = target();
        return target == null ? ItemStack.EMPTY : target.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        T target = target();
        return target == null ? ItemStack.EMPTY : target.removeItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        T target = target();
        return target == null ? ItemStack.EMPTY : target.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        T target = target();
        if (target != null) {
            target.setItem(slot, stack);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        T target = target();
        return target != null && target.stillValid(player);
    }

    @Override
    public void clearContent() {
        T target = target();
        if (target != null) {
            target.clearContent();
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        T target = target();
        return target != null && target.canPlaceItem(slot, stack);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        T target = target();
        return target == null ? NO_SLOTS : target.getSlotsForFace(side);
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        T target = target();
        return target != null && target.canPlaceItemThroughFace(slot, stack, side);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        T target = target();
        return target != null && target.canTakeItemThroughFace(slot, stack, side);
    }
}
