package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.TurbofanBlockEntity;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.item.MachineUpgradeItem;
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

public final class TurbofanMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 13;

    private final Container turbofan;
    private final ContainerData data;

    public TurbofanMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(DATA_COUNT));
    }

    public TurbofanMenu(int id, Inventory inventory, Container turbofan, ContainerData data) {
        super(ModMenus.MACHINE_TURBOFAN.get(), id);
        checkContainerSize(turbofan, TurbofanBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, DATA_COUNT);
        this.turbofan = turbofan;
        this.data = data;

        addSlot(new RestrictedSlot(turbofan, TurbofanBlockEntity.FUEL_INPUT, 17, 17));
        addSlot(new OutputSlot(turbofan, TurbofanBlockEntity.CONTAINER_OUTPUT, 17, 53));
        addSlot(new RestrictedSlot(turbofan, TurbofanBlockEntity.AFTERBURNER, 98, 71));
        addSlot(new RestrictedSlot(turbofan, TurbofanBlockEntity.BATTERY, 143, 71));
        addSlot(new RestrictedSlot(turbofan, TurbofanBlockEntity.IDENTIFIER, 44, 71));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 121 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 179));
        }
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos position) {
        return inventory.player.level().getBlockEntity(position) instanceof TurbofanBlockEntity turbofan
                ? turbofan : new SimpleContainer(TurbofanBlockEntity.SLOT_COUNT);
    }

    public TurbofanBlockEntity blockEntity() {
        return turbofan instanceof TurbofanBlockEntity entity ? entity : null;
    }

    public long power() { return (data.get(0) & 0xFFFFFFFFL) | (long) data.get(1) << 32; }
    public long maxPower() { return (data.get(2) & 0xFFFFFFFFL) | (long) data.get(3) << 32; }
    public int fuelAmount() { return data.get(4); }
    public int fuelCapacity() { return TurbofanBlockEntity.FUEL_CAPACITY; }
    public FluidIdentifierItem.Selection selectedFluid() {
        FluidIdentifierItem.Selection[] values = FluidIdentifierItem.Selection.values();
        int ordinal = data.get(5);
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FluidIdentifierItem.Selection.NONE;
    }
    public int afterburner() { return data.get(6); }
    public boolean wasOn() { return data.get(7) != 0; }
    public boolean showBlood() { return data.get(8) != 0; }
    public int bloodAmount() { return data.get(9); }
    public int bloodCapacity() { return TurbofanBlockEntity.BLOOD_CAPACITY; }
    public int smokeAmount() { return data.get(10); }
    public int output() { return data.get(11); }
    public int consumption() { return data.get(12); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index < TurbofanBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(stack, TurbofanBlockEntity.SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof HeBatteryItem) {
            if (!moveItemStackTo(stack, TurbofanBlockEntity.BATTERY,
                    TurbofanBlockEntity.BATTERY + 1, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof FluidIdentifierItem) {
            if (!moveItemStackTo(stack, TurbofanBlockEntity.IDENTIFIER,
                    TurbofanBlockEntity.IDENTIFIER + 1, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof MachineUpgradeItem) {
            if (!moveItemStackTo(stack, TurbofanBlockEntity.AFTERBURNER,
                    TurbofanBlockEntity.AFTERBURNER + 1, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, TurbofanBlockEntity.FUEL_INPUT,
                TurbofanBlockEntity.FUEL_INPUT + 1, false)) return ItemStack.EMPTY;

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return turbofan.stillValid(player);
    }

    private final class RestrictedSlot extends Slot {
        private RestrictedSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return turbofan.canPlaceItem(getContainerSlot(), stack);
        }
    }

    private static final class OutputSlot extends Slot {
        private OutputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
