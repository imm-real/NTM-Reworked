package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.SteamTurbineBlockEntity;
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

/** Seven turbine slots and no room for an eighth opinion. */
public final class SteamTurbineMenu extends AbstractContainerMenu {
    private final Container turbine;
    private final ContainerData data;

    public SteamTurbineMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(10));
    }

    public SteamTurbineMenu(int id, Inventory inventory, Container turbine, ContainerData data) {
        super(ModMenus.MACHINE_TURBINE.get(), id);
        checkContainerSize(turbine, SteamTurbineBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, 10);
        this.turbine = turbine;
        this.data = data;

        addSlot(new RestrictedSlot(turbine, SteamTurbineBlockEntity.IDENTIFIER_INPUT, 8, 17));
        addSlot(new OutputSlot(turbine, SteamTurbineBlockEntity.IDENTIFIER_OUTPUT, 8, 53));
        addSlot(new RestrictedSlot(turbine, SteamTurbineBlockEntity.INPUT_CONTAINER, 44, 17));
        addSlot(new OutputSlot(turbine, SteamTurbineBlockEntity.INPUT_CONTAINER_OUTPUT, 44, 53));
        addSlot(new RestrictedSlot(turbine, SteamTurbineBlockEntity.BATTERY, 98, 53));
        addSlot(new RestrictedSlot(turbine, SteamTurbineBlockEntity.OUTPUT_CONTAINER, 152, 17));
        addSlot(new OutputSlot(turbine, SteamTurbineBlockEntity.OUTPUT_CONTAINER_OUTPUT, 152, 53));

        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof SteamTurbineBlockEntity turbine
                ? turbine : new SimpleContainer(SteamTurbineBlockEntity.SLOT_COUNT);
    }

    public long power() { return (data.get(0) & 0xFFFFFFFFL) | (long) data.get(1) << 32; }
    public long maxPower() { return (data.get(2) & 0xFFFFFFFFL) | (long) data.get(3) << 32; }
    public int inputAmount() { return data.get(4); }
    public int inputCapacity() { return data.get(5); }
    public int outputAmount() { return data.get(6); }
    public int outputCapacity() { return data.get(7); }
    public FluidIdentifierItem.Selection inputSelection() { return selection(data.get(8)); }
    public FluidIdentifierItem.Selection outputSelection() { return selection(data.get(9)); }

    private static FluidIdentifierItem.Selection selection(int ordinal) {
        FluidIdentifierItem.Selection[] values = FluidIdentifierItem.Selection.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FluidIdentifierItem.Selection.NONE;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < SteamTurbineBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(stack, SteamTurbineBlockEntity.SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof HeBatteryItem) {
            if (!moveItemStackTo(stack, SteamTurbineBlockEntity.BATTERY,
                    SteamTurbineBlockEntity.BATTERY + 1, false)) return ItemStack.EMPTY;
        } else if (turbine.canPlaceItem(SteamTurbineBlockEntity.INPUT_CONTAINER, stack)) {
            if (!moveItemStackTo(stack, SteamTurbineBlockEntity.INPUT_CONTAINER,
                    SteamTurbineBlockEntity.INPUT_CONTAINER + 1, false)) return ItemStack.EMPTY;
        } else if (turbine.canPlaceItem(SteamTurbineBlockEntity.OUTPUT_CONTAINER, stack)) {
            if (!moveItemStackTo(stack, SteamTurbineBlockEntity.OUTPUT_CONTAINER,
                    SteamTurbineBlockEntity.OUTPUT_CONTAINER + 1, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof FluidIdentifierItem) {
            if (!moveItemStackTo(stack, SteamTurbineBlockEntity.IDENTIFIER_INPUT,
                    SteamTurbineBlockEntity.IDENTIFIER_INPUT + 1, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return turbine.stillValid(player); }

    private final class RestrictedSlot extends Slot {
        private RestrictedSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return turbine.canPlaceItem(getContainerSlot(), stack); }
    }

    private static final class OutputSlot extends Slot {
        private OutputSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
