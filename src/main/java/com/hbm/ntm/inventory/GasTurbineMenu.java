package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.GasTurbineBlockEntity;
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

public final class GasTurbineMenu extends AbstractContainerMenu {
    private final Container turbine;
    private final ContainerData data;

    public GasTurbineMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(14));
    }

    public GasTurbineMenu(int id, Inventory inventory, Container turbine, ContainerData data) {
        super(ModMenus.MACHINE_TURBINE_GAS.get(), id);
        checkContainerSize(turbine, GasTurbineBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, 14);
        this.turbine = turbine;
        this.data = data;

        addSlot(new RestrictedSlot(turbine, GasTurbineBlockEntity.BATTERY, 8, 109));
        addSlot(new RestrictedSlot(turbine, GasTurbineBlockEntity.FLUID_IDENTIFIER, 36, 17));
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 141 + row * 18));
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 199));
        }
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos position) {
        return inventory.player.level().getBlockEntity(position) instanceof GasTurbineBlockEntity turbine
                ? turbine : new SimpleContainer(GasTurbineBlockEntity.SLOT_COUNT);
    }

    public GasTurbineBlockEntity blockEntity() {
        return turbine instanceof GasTurbineBlockEntity entity ? entity : null;
    }

    public long power() { return (data.get(0) & 0xFFFFFFFFL) | (long) data.get(1) << 32; }
    public int fuel() { return data.get(2); }
    public int lubricant() { return data.get(3); }
    public int water() { return data.get(4); }
    public int steam() { return data.get(5); }
    public FluidIdentifierItem.Selection selectedFuel() {
        int ordinal = data.get(6);
        FluidIdentifierItem.Selection[] values = FluidIdentifierItem.Selection.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FluidIdentifierItem.Selection.GAS;
    }
    public int rpm() { return data.get(7); }
    public int temperature() { return data.get(8); }
    public int state() { return data.get(9); }
    public boolean autoMode() { return data.get(10) != 0; }
    public int slider() { return data.get(11); }
    public int output() { return data.get(12); }
    public int counter() { return data.get(13); }
    public int throttle() { return slider() * 100 / GasTurbineBlockEntity.MAX_SLIDER; }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < GasTurbineBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(stack, GasTurbineBlockEntity.SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof HeBatteryItem) {
            if (!moveItemStackTo(stack, GasTurbineBlockEntity.BATTERY,
                    GasTurbineBlockEntity.BATTERY + 1, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof FluidIdentifierItem
                && FluidIdentifierItem.primary(stack) == FluidIdentifierItem.Selection.GAS) {
            if (!moveItemStackTo(stack, GasTurbineBlockEntity.FLUID_IDENTIFIER,
                    GasTurbineBlockEntity.FLUID_IDENTIFIER + 1, false)) return ItemStack.EMPTY;
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
}
