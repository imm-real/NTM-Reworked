package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.BombMultiBlockEntity;
import com.hbm.ntm.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Six bomb slots. Automation may leave but may not enter. */
public final class BombMultiMenu extends AbstractContainerMenu {
    private final Container bomb;

    public BombMultiMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()));
    }

    public BombMultiMenu(int id, Inventory inventory, Container bomb) {
        super(ModMenus.BOMB_MULTI.get(), id);
        checkContainerSize(bomb, 6);
        this.bomb = bomb;
        bomb.startOpen(inventory.player);

        addSlot(new Slot(bomb, 0, 44, 26));
        addSlot(new Slot(bomb, 1, 62, 26));
        addSlot(new Slot(bomb, 2, 80, 26));
        addSlot(new Slot(bomb, 3, 44, 44));
        addSlot(new Slot(bomb, 4, 62, 44));
        addSlot(new Slot(bomb, 5, 80, 44));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
    }

    private static Container find(Inventory inventory, BlockPos position) {
        return inventory.player.level().getBlockEntity(position) instanceof BombMultiBlockEntity bomb
                ? bomb : new SimpleContainer(6);
    }

    public int return2type() {
        return BombMultiBlockEntity.typeOf(getSlot(2).getItem());
    }

    public int return5type() {
        return BombMultiBlockEntity.typeOf(getSlot(5).getItem());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Shift-click evacuates the bomb. It will not arm one for you.
        if (index > 5) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (!moveItemStackTo(stack, 6, slots.size(), true)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override public boolean stillValid(Player player) { return bomb.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); bomb.stopOpen(player); }
}
