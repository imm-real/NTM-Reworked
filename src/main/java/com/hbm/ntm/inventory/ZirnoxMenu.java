package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.ZirnoxBlockEntity;
import com.hbm.ntm.item.ZirnoxRodItem;
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

public final class ZirnoxMenu extends AbstractContainerMenu {
    private static final int[][] ROD_POSITIONS = {
            {26,16},{62,16},{98,16},{8,34},{44,34},{80,34},{116,34},
            {26,52},{62,52},{98,52},{8,70},{44,70},{80,70},{116,70},
            {26,88},{62,88},{98,88},{8,106},{44,106},{80,106},{116,106},
            {26,124},{62,124},{98,124}
    };
    private final Container reactor;
    private final ContainerData data;

    public ZirnoxMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(10));
    }

    public ZirnoxMenu(int id, Inventory inventory, Container reactor, ContainerData data) {
        super(ModMenus.REACTOR_ZIRNOX.get(), id);
        checkContainerSize(reactor, ZirnoxBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, 10);
        this.reactor = reactor;
        this.data = data;
        for (int slot = 0; slot < ROD_POSITIONS.length; slot++) {
            int[] p = ROD_POSITIONS[slot]; addSlot(new RestrictedSlot(reactor, slot, p[0], p[1]));
        }
        addSlot(new RestrictedSlot(reactor, ZirnoxBlockEntity.CO2_INPUT, 143, 124));
        addSlot(new OutputSlot(reactor, ZirnoxBlockEntity.CO2_OUTPUT, 143, 142));
        addSlot(new RestrictedSlot(reactor, ZirnoxBlockEntity.WATER_INPUT, 179, 124));
        addSlot(new OutputSlot(reactor, ZirnoxBlockEntity.WATER_OUTPUT, 179, 142));
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 174 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 232));
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof ZirnoxBlockEntity reactor
                ? reactor : new SimpleContainer(ZirnoxBlockEntity.SLOT_COUNT);
    }

    public int heat() { return data.get(0); }
    public int pressure() { return data.get(1); }
    public boolean isOn() { return data.get(2) != 0; }
    public boolean redstoneLocked() { return data.get(3) != 0; }
    public int steam() { return data.get(4); }
    public int carbonDioxide() { return data.get(5); }
    public int water() { return data.get(6); }
    public int activeRods() { return data.get(9); }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (!(reactor instanceof ZirnoxBlockEntity entity)) return false;
        if (id == 0) entity.toggle(); else if (id == 1) entity.vent(); else return false;
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        if (index < ZirnoxBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(stack, ZirnoxBlockEntity.SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof ZirnoxRodItem) {
            if (!moveItemStackTo(stack, 0, ZirnoxBlockEntity.FUEL_SLOTS, false)) return ItemStack.EMPTY;
        } else if (reactor.canPlaceItem(ZirnoxBlockEntity.CO2_INPUT, stack)) {
            if (!moveItemStackTo(stack, ZirnoxBlockEntity.FUEL_SLOTS, ZirnoxBlockEntity.FUEL_SLOTS + 1, false)) return ItemStack.EMPTY;
        } else if (reactor.canPlaceItem(ZirnoxBlockEntity.WATER_INPUT, stack)) {
            // Menu slot 26 corresponds to container slot 25.
            if (!moveItemStackTo(stack, 26, 27, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack); return copy;
    }

    @Override public boolean stillValid(Player player) { return reactor.stillValid(player); }

    private final class RestrictedSlot extends Slot {
        private RestrictedSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return reactor.canPlaceItem(getContainerSlot(), stack); }
    }
    private static final class OutputSlot extends Slot {
        private OutputSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
