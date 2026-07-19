package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.DieselGeneratorBlockEntity;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.HeBatteryItem;
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

public final class DieselGeneratorMenu extends AbstractContainerMenu {
    private final Container generator;
    private final ContainerData data;

    public DieselGeneratorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(9));
    }

    public DieselGeneratorMenu(int id, Inventory inventory, Container generator, ContainerData data) {
        super(ModMenus.MACHINE_DIESEL.get(), id);
        checkContainerSize(generator, DieselGeneratorBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, 9);
        this.generator = generator;
        this.data = data;

        addSlot(new RestrictedSlot(generator, DieselGeneratorBlockEntity.FUEL_INPUT, 44, 17));
        addSlot(new OutputSlot(generator, DieselGeneratorBlockEntity.CONTAINER_OUTPUT, 44, 53));
        addSlot(new RestrictedSlot(generator, DieselGeneratorBlockEntity.BATTERY, 116, 53));
        addSlot(new RestrictedSlot(generator, DieselGeneratorBlockEntity.IDENTIFIER_INPUT, 8, 17));
        addSlot(new OutputSlot(generator, DieselGeneratorBlockEntity.IDENTIFIER_OUTPUT, 8, 53));

        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof DieselGeneratorBlockEntity generator
                ? generator : new SimpleContainer(DieselGeneratorBlockEntity.SLOT_COUNT);
    }

    public DieselGeneratorBlockEntity blockEntity() {
        return generator instanceof DieselGeneratorBlockEntity entity ? entity : null;
    }
    public long power() { return (data.get(0) & 0xFFFFFFFFL) | (long) data.get(1) << 32; }
    public long maxPower() { return (data.get(2) & 0xFFFFFFFFL) | (long) data.get(3) << 32; }
    public int fuelAmount() { return data.get(4); }
    public FluidIdentifierItem.Selection selectedFluid() {
        int ordinal = data.get(5);
        FluidIdentifierItem.Selection[] values = FluidIdentifierItem.Selection.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FluidIdentifierItem.Selection.NONE;
    }
    public boolean active() { return data.get(6) != 0; }
    public boolean acceptableFuel() { return data.get(7) != 0; }
    public int smokeAmount() { return data.get(8); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < DieselGeneratorBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(stack, DieselGeneratorBlockEntity.SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof FluidIdentifierItem) {
            if (!moveItemStackTo(stack, DieselGeneratorBlockEntity.IDENTIFIER_INPUT,
                    DieselGeneratorBlockEntity.IDENTIFIER_INPUT + 1, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof HeBatteryItem) {
            if (!moveItemStackTo(stack, DieselGeneratorBlockEntity.BATTERY,
                    DieselGeneratorBlockEntity.BATTERY + 1, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, DieselGeneratorBlockEntity.FUEL_INPUT,
                DieselGeneratorBlockEntity.FUEL_INPUT + 1, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return generator.stillValid(player); }

    private final class RestrictedSlot extends Slot {
        private RestrictedSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return generator.canPlaceItem(getContainerSlot(), stack); }
    }

    private static final class OutputSlot extends Slot {
        private OutputSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
