package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.CombinationOvenBlockEntity;
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

/** Four-slot Combination Oven menu. */
public final class CombinationOvenMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 5;
    private static final int MACHINE_SLOT_COUNT = 4;
    private final Container oven;
    private final ContainerData data;

    public CombinationOvenMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(DATA_COUNT));
    }

    public CombinationOvenMenu(int id, Inventory inventory, Container oven, ContainerData data) {
        super(ModMenus.FURNACE_COMBINATION.get(), id);
        checkContainerSize(oven, CombinationOvenBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, DATA_COUNT);
        this.oven = oven;
        this.data = data;

        addSlot(new Slot(oven, CombinationOvenBlockEntity.INPUT, 26, 36));
        addSlot(new OutputSlot(oven, CombinationOvenBlockEntity.OUTPUT, 89, 36));
        addSlot(new Slot(oven, CombinationOvenBlockEntity.CONTAINER_INPUT, 136, 18));
        addSlot(new OutputSlot(oven, CombinationOvenBlockEntity.CONTAINER_OUTPUT, 136, 54));

        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 104 + row * 18));
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 162));
        }
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos position) {
        return inventory.player.level().getBlockEntity(position) instanceof CombinationOvenBlockEntity oven
                ? oven : new SimpleContainer(CombinationOvenBlockEntity.SLOT_COUNT);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < MACHINE_SLOT_COUNT) {
            if (!moveItemStackTo(stack, MACHINE_SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, CombinationOvenBlockEntity.INPUT,
                CombinationOvenBlockEntity.INPUT + 1, false)
                && !moveItemStackTo(stack, CombinationOvenBlockEntity.CONTAINER_INPUT,
                CombinationOvenBlockEntity.CONTAINER_INPUT + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return oven.stillValid(player); }
    public int progress() { return data.get(0); }
    public int heat() { return data.get(1); }
    public int fluidAmount() { return data.get(2); }
    public FluidIdentifierItem.Selection fluidSelection() {
        int ordinal = data.get(3);
        FluidIdentifierItem.Selection[] values = FluidIdentifierItem.Selection.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FluidIdentifierItem.Selection.NONE;
    }
    public boolean wasOn() { return data.get(4) != 0; }

    private static final class OutputSlot extends Slot {
        private OutputSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
