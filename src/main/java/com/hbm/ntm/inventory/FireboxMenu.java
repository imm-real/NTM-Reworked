package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.FireboxBlockEntity;
import com.hbm.ntm.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class FireboxMenu extends AbstractContainerMenu {
    private final Container firebox;
    private final ContainerData data;

    public FireboxMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(6));
    }

    public FireboxMenu(int id, Inventory inventory, Container firebox, ContainerData data) {
        super(ModMenus.HEATER_FIREBOX.get(), id);
        checkContainerSize(firebox, 2);
        checkContainerDataCount(data, 6);
        this.firebox = firebox;
        this.data = data;
        firebox.startOpen(inventory.player);

        addSlot(new Slot(firebox, 0, 44, 27));
        addSlot(new Slot(firebox, 1, 62, 27));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 86 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 144));
        }
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos position) {
        return inventory.player.level().getBlockEntity(position) instanceof FireboxBlockEntity firebox
                ? firebox : new SimpleContainer(2);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index <= 1) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, 2, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override public boolean stillValid(Player player) { return firebox.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); firebox.stopOpen(player); }

    public int maxBurnTime() { return data.get(0); }
    public int burnTime() { return data.get(1); }
    public int burnHeat() { return data.get(2); }
    public int heat() { return data.get(3); }
    public boolean wasOn() { return data.get(4) != 0; }
    public boolean isHeatingOven() { return data.get(5) != 0; }
    public int maxHeat() { return isHeatingOven() ? FireboxBlockEntity.OVEN_MAX_HEAT : FireboxBlockEntity.MAX_HEAT; }
}
