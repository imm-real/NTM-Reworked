package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.FluidBurnerBlockEntity;
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

public final class FluidBurnerMenu extends AbstractContainerMenu {
    private final Container burner;
    private final ContainerData data;

    public FluidBurnerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(6));
    }

    public FluidBurnerMenu(int id, Inventory inventory, Container burner, ContainerData data) {
        super(ModMenus.HEATER_OILBURNER.get(), id);
        checkContainerSize(burner, FluidBurnerBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, 6);
        this.burner = burner;
        this.data = data;

        addSlot(new Slot(burner, FluidBurnerBlockEntity.CONTAINER_INPUT, 26, 17) {
            @Override public boolean mayPlace(ItemStack stack) { return FluidBurnerBlockEntity.isFluidContainer(stack); }
        });
        addSlot(new Slot(burner, FluidBurnerBlockEntity.CONTAINER_OUTPUT, 26, 53) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        addSlot(new Slot(burner, FluidBurnerBlockEntity.IDENTIFIER, 44, 71) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof FluidIdentifierItem; }
        });

        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 121 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 179));
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof FluidBurnerBlockEntity burner
                ? burner : new SimpleContainer(FluidBurnerBlockEntity.SLOT_COUNT);
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (id != 0 || !(burner instanceof FluidBurnerBlockEntity entity)) return false;
        entity.toggleOn();
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < FluidBurnerBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(stack, FluidBurnerBlockEntity.SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof FluidIdentifierItem) {
            if (!moveItemStackTo(stack, FluidBurnerBlockEntity.IDENTIFIER,
                    FluidBurnerBlockEntity.IDENTIFIER + 1, false)) return ItemStack.EMPTY;
        } else if (FluidBurnerBlockEntity.isFluidContainer(stack)) {
            if (!moveItemStackTo(stack, FluidBurnerBlockEntity.CONTAINER_INPUT,
                    FluidBurnerBlockEntity.CONTAINER_INPUT + 1, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override public boolean stillValid(Player player) { return burner.stillValid(player); }

    public int heatEnergy() { return data.get(0); }
    public int fuelAmount() { return data.get(1); }
    public FluidIdentifierItem.Selection selectedFluid() {
        int ordinal = data.get(2);
        FluidIdentifierItem.Selection[] values = FluidIdentifierItem.Selection.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FluidIdentifierItem.Selection.NONE;
    }
    public boolean isOn() { return data.get(3) != 0; }
    public int setting() { return data.get(4); }
    public int smokeAmount() { return data.get(5); }
}
