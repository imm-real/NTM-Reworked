package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.AshpitBlockEntity;
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

public final class AshpitMenu extends AbstractContainerMenu {
    private final Container ashpit;

    public AshpitMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()));
    }

    public AshpitMenu(int id, Inventory inventory, Container ashpit) {
        super(ModMenus.MACHINE_ASHPIT.get(), id);
        checkContainerSize(ashpit, 5);
        this.ashpit = ashpit;
        ashpit.startOpen(inventory.player);

        for (int i = 0; i < 5; i++) addSlot(new OutputSlot(ashpit, i, 44 + i * 18, 27));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 86 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 144));
        }
    }

    private static Container find(Inventory inventory, BlockPos position) {
        return inventory.player.level().getBlockEntity(position) instanceof AshpitBlockEntity ashpit
                ? ashpit : new SimpleContainer(5);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index > 4) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (!moveItemStackTo(stack, 5, slots.size(), true)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override public boolean stillValid(Player player) { return ashpit.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); ashpit.stopOpen(player); }

    private static final class OutputSlot extends Slot {
        private OutputSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
