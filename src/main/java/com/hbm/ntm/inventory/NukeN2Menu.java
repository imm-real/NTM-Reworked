package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.NukeN2BlockEntity;
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

/** Twelve bomb slots. Shift-click only works in the cowardly direction: out. */
public final class NukeN2Menu extends AbstractContainerMenu {
    private static final int BOMB_SLOTS = NukeN2BlockEntity.SLOTS;
    private final Container bomb;

    public NukeN2Menu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()));
    }

    public NukeN2Menu(int id, Inventory inventory, Container bomb) {
        super(ModMenus.NUKE_N2.get(), id);
        checkContainerSize(bomb, BOMB_SLOTS);
        this.bomb = bomb;
        bomb.startOpen(inventory.player);

        for (int slot = 0; slot < BOMB_SLOTS; slot++) {
            addSlot(new Slot(bomb, slot, 98 + (slot % 3) * 18, 36 + (slot / 3) * 18));
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18 + 56));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142 + 56));
        }
    }

    private static Container find(Inventory inventory, BlockPos position) {
        return inventory.player.level().getBlockEntity(position) instanceof NukeN2BlockEntity bomb
                ? bomb : new SimpleContainer(BOMB_SLOTS);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= BOMB_SLOTS) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (!moveItemStackTo(stack, BOMB_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override public boolean stillValid(Player player) { return bomb.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); bomb.stopOpen(player); }
}
