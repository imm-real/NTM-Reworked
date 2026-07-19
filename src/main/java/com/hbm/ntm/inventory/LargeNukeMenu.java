package com.hbm.ntm.inventory;

import com.hbm.ntm.block.LargeNukeType;
import com.hbm.ntm.blockentity.LargeNukeBlockEntity;
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

public final class LargeNukeMenu extends AbstractContainerMenu {
    private final Container bomb;
    private final LargeNukeType type;

    public LargeNukeMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()));
    }

    public LargeNukeMenu(int id, Inventory inventory, LargeNukeBlockEntity bomb) {
        super(ModMenus.LARGE_NUKE.get(), id);
        this.bomb = bomb;
        this.type = bomb.type();
        checkContainerSize(bomb, type.slots());
        bomb.startOpen(inventory.player);

        for (int slot = 0; slot < type.slots(); slot++) {
            addSlot(new Slot(bomb, slot, type.slotX(slot), type.slotY(slot)));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        type.inventoryX() + column * 18, type.inventoryY() + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, type.inventoryX() + column * 18,
                    type.inventoryY() + 58));
        }
    }

    private static LargeNukeBlockEntity find(Inventory inventory, BlockPos position) {
        if (inventory.player.level().getBlockEntity(position) instanceof LargeNukeBlockEntity bomb) return bomb;
        throw new IllegalStateException("Missing large nuke block entity at " + position);
    }

    public LargeNukeType type() { return type; }
    public ItemStack bombItem(int slot) { return getSlot(slot).getItem(); }
    public boolean isReady() { return type.isReady(bombItems()); }
    public boolean isFilled() { return type.isFilled(bombItems()); }

    private ItemStack[] bombItems() {
        ItemStack[] items = new ItemStack[type.slots()];
        for (int slot = 0; slot < items.length; slot++) items[slot] = getSlot(slot).getItem();
        return items;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= type.slots()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (!moveItemStackTo(stack, type.slots(), slots.size(), true)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override public boolean stillValid(Player player) { return bomb.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); bomb.stopOpen(player); }
}
