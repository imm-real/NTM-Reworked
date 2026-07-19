package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.NukeBalefireBlockEntity;
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
import org.jetbrains.annotations.Nullable;

/** Source {@code ContainerNukeFstbmb}: egg slot (17,36), battery slot (53,36), player inventory pushed +56. */
public final class NukeFstbmbMenu extends AbstractContainerMenu {
    private final Container bomb;
    @Nullable private final NukeBalefireBlockEntity blockEntity;

    public NukeFstbmbMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()));
    }

    public NukeFstbmbMenu(int id, Inventory inventory, Container bomb) {
        super(ModMenus.NUKE_FSTBMB.get(), id);
        checkContainerSize(bomb, 2);
        this.bomb = bomb;
        this.blockEntity = bomb instanceof NukeBalefireBlockEntity be ? be : null;
        bomb.startOpen(inventory.player);

        addSlot(new Slot(bomb, 0, 17, 36));
        addSlot(new Slot(bomb, 1, 53, 36));

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
        return inventory.player.level().getBlockEntity(position) instanceof NukeBalefireBlockEntity bomb
                ? bomb : new SimpleContainer(2);
    }

    @Nullable
    public NukeBalefireBlockEntity blockEntity() { return blockEntity; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Source transferStackInSlot only moves the bomb slots into the inventory.
        if (index >= 2) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (!moveItemStackTo(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override public boolean stillValid(Player player) { return bomb.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); bomb.stopOpen(player); }
}
