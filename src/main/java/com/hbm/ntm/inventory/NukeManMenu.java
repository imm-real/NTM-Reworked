package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.NukeManBlockEntity;
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

public final class NukeManMenu extends AbstractContainerMenu {
    private final Container bomb;

    public NukeManMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()));
    }

    public NukeManMenu(int id, Inventory inventory, Container bomb) {
        super(ModMenus.NUKE_MAN.get(), id);
        checkContainerSize(bomb, 6);
        this.bomb = bomb;
        bomb.startOpen(inventory.player);

        addSlot(new Slot(bomb, 0, 26, 35));
        addSlot(new Slot(bomb, 1, 8, 17));
        addSlot(new Slot(bomb, 2, 44, 17));
        addSlot(new Slot(bomb, 3, 8, 53));
        addSlot(new Slot(bomb, 4, 44, 53));
        addSlot(new Slot(bomb, 5, 98, 35));

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
        return inventory.player.level().getBlockEntity(position) instanceof NukeManBlockEntity bomb
                ? bomb : new SimpleContainer(6);
    }

    public boolean hasLens(int slot) {
        return slot >= 1 && slot <= 4
                && getSlot(slot).getItem().is(com.hbm.ntm.registry.ModItems.EARLY_EXPLOSIVE_LENSES.get());
    }

    public boolean isReady() {
        return getSlot(0).getItem().is(com.hbm.ntm.registry.ModItems.MAN_IGNITER.get())
                && hasLens(1) && hasLens(2) && hasLens(3) && hasLens(4)
                && getSlot(5).getItem().is(com.hbm.ntm.registry.ModItems.MAN_CORE.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index > 5) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (!moveItemStackTo(stack, 6, slots.size(), true)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override public boolean stillValid(Player player) { return bomb.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); bomb.stopOpen(player); }
}
