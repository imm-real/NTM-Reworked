package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.NukeCustomBlockEntity;
import com.hbm.ntm.nuclear.CustomNukeExplosion;
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

import java.util.ArrayList;
import java.util.List;

/** Twenty-seven slots for building one extremely personal crater. */
public final class NukeCustomMenu extends AbstractContainerMenu {
    public static final int DEVICE_SLOTS = NukeCustomBlockEntity.SLOTS;
    private final Container bomb;

    public NukeCustomMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()));
    }

    public NukeCustomMenu(int id, Inventory inventory, Container bomb) {
        super(ModMenus.NUKE_CUSTOM.get(), id);
        checkContainerSize(bomb, DEVICE_SLOTS);
        this.bomb = bomb;
        bomb.startOpen(inventory.player);

        // Source: three rows of nine at x = 8 + col*18, y = 18 / 36 / 54.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(bomb, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }
        // Source: player inventory rows at y = 140/158/176 and the hotbar at y = 198.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 140 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 198));
        }
    }

    private static Container find(Inventory inventory, BlockPos position) {
        return inventory.player.level().getBlockEntity(position) instanceof NukeCustomBlockEntity bomb
                ? bomb : new SimpleContainer(DEVICE_SLOTS);
    }

    /** Yields computed from the (menu-synced) device slots, used by the screen for live display. */
    public CustomNukeExplosion.Yields yields() {
        List<ItemStack> slots = new ArrayList<>(DEVICE_SLOTS);
        for (int i = 0; i < DEVICE_SLOTS; i++) slots.add(bomb.getItem(i));
        return CustomNukeExplosion.computeYields(slots);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < DEVICE_SLOTS) {
                // Device -> player inventory.
                if (!moveItemStackTo(stack, DEVICE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
            } else {
                // Player inventory -> device.
                if (!moveItemStackTo(stack, 0, DEVICE_SLOTS, false)) return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }

    @Override public boolean stillValid(Player player) { return bomb.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); bomb.stopOpen(player); }
}
