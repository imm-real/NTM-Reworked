package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.ConveyorBoxerBlockEntity;
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

public final class ConveyorBoxerMenu extends AbstractContainerMenu {
    private final Container boxer;
    private final ContainerData data;

    public ConveyorBoxerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(1));
    }

    public ConveyorBoxerMenu(int id, Inventory inventory, Container boxer, ContainerData data) {
        super(ModMenus.CRANE_BOXER.get(), id);
        checkContainerSize(boxer, ConveyorBoxerBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, 1);
        this.boxer = boxer;
        this.data = data;
        boxer.startOpen(inventory.player);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 7; column++) {
                addSlot(new Slot(boxer, column + row * 7, 8 + column * 18, 17 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 103 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 161));
        }
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof ConveyorBoxerBlockEntity boxer
                ? boxer : new SimpleContainer(ConveyorBoxerBlockEntity.SLOT_COUNT);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int machineSlots = ConveyorBoxerBlockEntity.SLOT_COUNT;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, machineSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != 0 || !(boxer instanceof ConveyorBoxerBlockEntity blockEntity)) return false;
        blockEntity.cycleMode();
        return true;
    }

    @Override public boolean stillValid(Player player) { return boxer.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); boxer.stopOpen(player); }
    public int mode() { return data.get(0); }
}
