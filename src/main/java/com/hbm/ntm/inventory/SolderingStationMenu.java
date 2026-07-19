package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.SolderingStationBlockEntity;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.item.MachineUpgradeItem;
import com.hbm.ntm.recipe.SolderingRecipes;
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

public final class SolderingStationMenu extends AbstractContainerMenu {
    private final Container station;
    private final ContainerData data;

    public SolderingStationMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(9));
    }

    public SolderingStationMenu(int id, Inventory inventory, Container station, ContainerData data) {
        super(ModMenus.MACHINE_SOLDERING_STATION.get(), id);
        checkContainerSize(station, SolderingStationBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, 9);
        this.station = station;
        this.data = data;
        for (int row = 0; row < 2; row++) for (int column = 0; column < 3; column++) {
            addSlot(new RestrictedSlot(station, row * 3 + column, 17 + column * 18, 18 + row * 18));
        }
        addSlot(new OutputSlot(station, 6, 107, 27));
        addSlot(new RestrictedSlot(station, 7, 152, 72));
        addSlot(new RestrictedSlot(station, 8, 17, 63));
        addSlot(new RestrictedSlot(station, 9, 89, 63));
        addSlot(new RestrictedSlot(station, 10, 107, 63));
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 122 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 180));
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof SolderingStationBlockEntity station
                ? station : new SimpleContainer(SolderingStationBlockEntity.SLOT_COUNT);
    }

    public SolderingStationBlockEntity blockEntity() {
        return station instanceof SolderingStationBlockEntity entity ? entity : null;
    }
    public int progress() { return data.get(0); }
    public int processTime() { return Math.max(1, data.get(1)); }
    public long power() { return (data.get(2) & 0xFFFFFFFFL) | (long) data.get(3) << 32; }
    public long maxPower() { return (data.get(4) & 0xFFFFFFFFL) | (long) data.get(5) << 32; }
    public long consumption() { return (data.get(6) & 0xFFFFFFFFL) | (long) data.get(7) << 32; }
    public boolean collisionPrevention() { return data.get(8) != 0; }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index <= 10) {
            if (!moveItemStackTo(stack, 11, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof HeBatteryItem) {
            if (!moveItemStackTo(stack, 7, 8, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof FluidIdentifierItem) {
            if (!moveItemStackTo(stack, 8, 9, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof MachineUpgradeItem) {
            if (!moveItemStackTo(stack, 9, 11, false)) return ItemStack.EMPTY;
        } else if (SolderingRecipes.validForGroup(0, stack)) {
            if (!moveItemStackTo(stack, 0, 3, false)) return ItemStack.EMPTY;
        } else if (SolderingRecipes.validForGroup(3, stack)) {
            if (!moveItemStackTo(stack, 3, 5, false)) return ItemStack.EMPTY;
        } else if (SolderingRecipes.validForGroup(5, stack)) {
            if (!moveItemStackTo(stack, 5, 6, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return station.stillValid(player); }

    private final class RestrictedSlot extends Slot {
        private RestrictedSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return station.canPlaceItem(getContainerSlot(), stack); }
    }
    private static final class OutputSlot extends Slot {
        private OutputSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
