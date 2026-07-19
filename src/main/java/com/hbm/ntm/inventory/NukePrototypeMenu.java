package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.NukePrototypeBlockEntity;
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

public final class NukePrototypeMenu extends AbstractContainerMenu {
    private final Container bomb;

    public NukePrototypeMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()));
    }

    public NukePrototypeMenu(int id, Inventory inventory, Container bomb) {
        super(ModMenus.NUKE_PROTOTYPE.get(), id);
        checkContainerSize(bomb, NukePrototypeBlockEntity.SLOTS);
        this.bomb = bomb;
        bomb.startOpen(inventory.player);

        addSlot(new Slot(bomb, 0, 8, 35));
        addSlot(new Slot(bomb, 1, 26, 35));
        addSlot(new Slot(bomb, 2, 44, 26));
        addSlot(new Slot(bomb, 3, 44, 44));
        addSlot(new Slot(bomb, 4, 62, 26));
        addSlot(new Slot(bomb, 5, 62, 44));
        addSlot(new Slot(bomb, 6, 80, 26));
        addSlot(new Slot(bomb, 7, 80, 44));
        addSlot(new Slot(bomb, 8, 98, 26));
        addSlot(new Slot(bomb, 9, 98, 44));
        addSlot(new Slot(bomb, 10, 116, 26));
        addSlot(new Slot(bomb, 11, 116, 44));
        addSlot(new Slot(bomb, 12, 134, 35));
        addSlot(new Slot(bomb, 13, 152, 35));

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
        return inventory.player.level().getBlockEntity(position) instanceof NukePrototypeBlockEntity bomb
                ? bomb : new SimpleContainer(NukePrototypeBlockEntity.SLOTS);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Source ContainerNukePrototype.transferStackInSlot only shift-moves the fourteen
        // component slots out to the player inventory; it never pulls items in.
        if (index > 13) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (!moveItemStackTo(stack, 14, slots.size(), true)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override public boolean stillValid(Player player) { return bomb.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); bomb.stopOpen(player); }
}
