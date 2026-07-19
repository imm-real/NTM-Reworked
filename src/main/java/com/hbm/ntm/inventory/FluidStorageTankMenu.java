package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.FluidStorageTankBlockEntity;
import com.hbm.ntm.item.FluidIdentifierItem;
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

public final class FluidStorageTankMenu extends AbstractContainerMenu {
    private final Container tank;
    private final ContainerData data;

    public FluidStorageTankMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(5));
    }

    public FluidStorageTankMenu(int id, Inventory inventory, Container tank, ContainerData data) {
        super(ModMenus.MACHINE_FLUIDTANK.get(), id);
        checkContainerSize(tank, FluidStorageTankBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, 5);
        this.tank = tank;
        this.data = data;

        addSlot(new RestrictedSlot(tank, FluidStorageTankBlockEntity.IDENTIFIER_INPUT, 8, 17));
        addSlot(new OutputSlot(tank, FluidStorageTankBlockEntity.IDENTIFIER_OUTPUT, 8, 53));
        addSlot(new RestrictedSlot(tank, FluidStorageTankBlockEntity.FILLED_INPUT, 35, 17));
        addSlot(new OutputSlot(tank, FluidStorageTankBlockEntity.EMPTY_OUTPUT, 35, 53));
        addSlot(new RestrictedSlot(tank, FluidStorageTankBlockEntity.EMPTY_INPUT, 125, 17));
        addSlot(new OutputSlot(tank, FluidStorageTankBlockEntity.FILLED_OUTPUT, 125, 53));

        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos position) {
        return inventory.player.level().getBlockEntity(position) instanceof FluidStorageTankBlockEntity tank
                ? tank : new SimpleContainer(FluidStorageTankBlockEntity.SLOT_COUNT);
    }

    public FluidStorageTankBlockEntity blockEntity() {
        return tank instanceof FluidStorageTankBlockEntity entity ? entity : null;
    }
    public int amount() { return data.get(0); }
    public FluidIdentifierItem.Selection selection() {
        int ordinal = data.get(1);
        FluidIdentifierItem.Selection[] values = FluidIdentifierItem.Selection.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FluidIdentifierItem.Selection.NONE;
    }
    public int mode() { return Math.clamp(data.get(2), 0, 3); }
    public boolean damaged() { return data.get(3) != 0; }
    public boolean onFire() { return data.get(4) != 0; }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < FluidStorageTankBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(stack, FluidStorageTankBlockEntity.SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof FluidIdentifierItem) {
            if (!moveItemStackTo(stack, FluidStorageTankBlockEntity.IDENTIFIER_INPUT,
                    FluidStorageTankBlockEntity.IDENTIFIER_INPUT + 1, false)) return ItemStack.EMPTY;
        } else if (tank.canPlaceItem(FluidStorageTankBlockEntity.FILLED_INPUT, stack)) {
            if (!moveItemStackTo(stack, FluidStorageTankBlockEntity.FILLED_INPUT,
                    FluidStorageTankBlockEntity.FILLED_INPUT + 1, false)) return ItemStack.EMPTY;
        } else if (tank.canPlaceItem(FluidStorageTankBlockEntity.EMPTY_INPUT, stack)) {
            if (!moveItemStackTo(stack, FluidStorageTankBlockEntity.EMPTY_INPUT,
                    FluidStorageTankBlockEntity.EMPTY_INPUT + 1, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return tank.stillValid(player); }

    private final class RestrictedSlot extends Slot {
        private RestrictedSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return tank.canPlaceItem(getContainerSlot(), stack); }
    }
    private static final class OutputSlot extends Slot {
        private OutputSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
