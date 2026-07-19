package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.NukeFleijaBlockEntity;
import com.hbm.ntm.registry.ModItems;
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

public final class NukeFleijaMenu extends AbstractContainerMenu {
    private final Container bomb;

    public NukeFleijaMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()));
    }

    public NukeFleijaMenu(int id, Inventory inventory, Container bomb) {
        super(ModMenus.NUKE_FLEIJA.get(), id);
        checkContainerSize(bomb, NukeFleijaBlockEntity.SLOTS);
        this.bomb = bomb;
        bomb.startOpen(inventory.player);

        addSlot(new Slot(bomb, 0, 8, 36));
        addSlot(new Slot(bomb, 1, 152, 36));
        addSlot(new Slot(bomb, 2, 44, 18));
        addSlot(new Slot(bomb, 3, 44, 36));
        addSlot(new Slot(bomb, 4, 44, 54));
        addSlot(new Slot(bomb, 5, 80, 18));
        addSlot(new Slot(bomb, 6, 98, 18));
        addSlot(new Slot(bomb, 7, 80, 36));
        addSlot(new Slot(bomb, 8, 98, 36));
        addSlot(new Slot(bomb, 9, 80, 54));
        addSlot(new Slot(bomb, 10, 98, 54));

        // The taller schematic GUI pushes the player inventory down by 56 pixels.
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
        return inventory.player.level().getBlockEntity(position) instanceof NukeFleijaBlockEntity bomb
                ? bomb : new SimpleContainer(NukeFleijaBlockEntity.SLOTS);
    }

    public boolean isIgniter(int slot) {
        return getSlot(slot).getItem().is(ModItems.FLEIJA_IGNITER.get());
    }

    public boolean isPropellant(int slot) {
        return getSlot(slot).getItem().is(ModItems.FLEIJA_PROPELLANT.get());
    }

    public boolean isCore(int slot) {
        return getSlot(slot).getItem().is(ModItems.FLEIJA_CORE.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index > 10) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (!moveItemStackTo(stack, 11, slots.size(), true)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override public boolean stillValid(Player player) { return bomb.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); bomb.stopOpen(player); }
}
