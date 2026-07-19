package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.WasteDrumBlockEntity;
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

/** Twelve drum slots arranged like a radioactive friendship diamond. */
public final class WasteDrumMenu extends AbstractContainerMenu {
    private final Container drum;

    public WasteDrumMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, findDrum(inventory, buffer.readBlockPos()));
    }

    public WasteDrumMenu(int id, Inventory inventory, Container drum) {
        super(ModMenus.MACHINE_WASTE_DRUM.get(), id);
        checkContainerSize(drum, WasteDrumBlockEntity.SLOT_COUNT);
        this.drum = drum;

        addSlot(new Slot(drum, 0, 71, 21));
        addSlot(new Slot(drum, 1, 89, 21));
        addSlot(new Slot(drum, 2, 53, 39));
        addSlot(new Slot(drum, 3, 71, 39));
        addSlot(new Slot(drum, 4, 89, 39));
        addSlot(new Slot(drum, 5, 107, 39));
        addSlot(new Slot(drum, 6, 53, 57));
        addSlot(new Slot(drum, 7, 71, 57));
        addSlot(new Slot(drum, 8, 89, 57));
        addSlot(new Slot(drum, 9, 107, 57));
        addSlot(new Slot(drum, 10, 71, 75));
        addSlot(new Slot(drum, 11, 89, 75));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 107 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 165));
        }
    }

    private static Container findDrum(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof WasteDrumBlockEntity drum
                ? drum : new SimpleContainer(WasteDrumBlockEntity.SLOT_COUNT);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < WasteDrumBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(stack, WasteDrumBlockEntity.SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, WasteDrumBlockEntity.SLOT_COUNT, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return drum.stillValid(player); }
}
