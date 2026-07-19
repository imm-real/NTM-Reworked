package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.NukeSoliniumBlockEntity;
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

public final class NukeSoliniumMenu extends AbstractContainerMenu {
    private final Container bomb;

    public NukeSoliniumMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()));
    }

    public NukeSoliniumMenu(int id, Inventory inventory, Container bomb) {
        super(ModMenus.NUKE_SOLINIUM.get(), id);
        checkContainerSize(bomb, NukeSoliniumBlockEntity.SLOTS);
        this.bomb = bomb;
        bomb.startOpen(inventory.player);

        addSlot(new Slot(bomb, 0, 26, 18));
        addSlot(new Slot(bomb, 1, 53, 18));
        addSlot(new Slot(bomb, 2, 107, 18));
        addSlot(new Slot(bomb, 3, 134, 18));
        addSlot(new Slot(bomb, 4, 80, 36));
        addSlot(new Slot(bomb, 5, 26, 54));
        addSlot(new Slot(bomb, 6, 53, 54));
        addSlot(new Slot(bomb, 7, 107, 54));
        addSlot(new Slot(bomb, 8, 134, 54));

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
        return inventory.player.level().getBlockEntity(position) instanceof NukeSoliniumBlockEntity bomb
                ? bomb : new SimpleContainer(NukeSoliniumBlockEntity.SLOTS);
    }

    public boolean isIgniter(int slot) {
        return getSlot(slot).getItem().is(ModItems.SOLINIUM_IGNITER.get());
    }

    public boolean isPropellant(int slot) {
        return getSlot(slot).getItem().is(ModItems.SOLINIUM_PROPELLANT.get());
    }

    public boolean isCore(int slot) {
        return getSlot(slot).getItem().is(ModItems.SOLINIUM_CORE.get());
    }

    /** Mirrors {@code TileEntityNukeSolinium.isReady} for the GUI's armed indicator. */
    public boolean isReady() {
        return isIgniter(0) && isPropellant(1) && isPropellant(2) && isIgniter(3) && isCore(4)
                && isIgniter(5) && isPropellant(6) && isPropellant(7) && isIgniter(8);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Source transferStackInSlot only moves the nine bomb slots out to the player.
        if (index > 8) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (!moveItemStackTo(stack, 9, slots.size(), true)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override public boolean stillValid(Player player) { return bomb.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); bomb.stopOpen(player); }
}
