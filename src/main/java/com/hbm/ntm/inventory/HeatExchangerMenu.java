package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.HeatExchangerBlockEntity;
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

public final class HeatExchangerMenu extends AbstractContainerMenu {
    private final Container exchanger;
    private final ContainerData data;

    public HeatExchangerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(7));
    }

    public HeatExchangerMenu(int id, Inventory inventory, Container exchanger, ContainerData data) {
        super(ModMenus.HEATER_HEATEX.get(), id);
        checkContainerSize(exchanger, HeatExchangerBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, 7);
        this.exchanger = exchanger;
        this.data = data;

        addSlot(new Slot(exchanger, HeatExchangerBlockEntity.IDENTIFIER, 80, 72) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof FluidIdentifierItem; }
        });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 122 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 180));
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos position) {
        return inventory.player.level().getBlockEntity(position) instanceof HeatExchangerBlockEntity exchanger
                ? exchanger : new SimpleContainer(HeatExchangerBlockEntity.SLOT_COUNT);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof FluidIdentifierItem) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override public boolean stillValid(Player player) { return exchanger.stillValid(player); }
    public HeatExchangerBlockEntity blockEntity() {
        return exchanger instanceof HeatExchangerBlockEntity entity ? entity : null;
    }
    public int heatEnergy() { return data.get(0); }
    public int inputAmount() { return data.get(1); }
    public int outputAmount() { return data.get(2); }
    public FluidIdentifierItem.Selection inputSelection() { return selection(data.get(3)); }
    public FluidIdentifierItem.Selection outputSelection() { return selection(data.get(4)); }
    public int amountToCool() { return data.get(5); }
    public int tickDelay() { return data.get(6); }

    private static FluidIdentifierItem.Selection selection(int ordinal) {
        FluidIdentifierItem.Selection[] values = FluidIdentifierItem.Selection.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FluidIdentifierItem.Selection.NONE;
    }
}
