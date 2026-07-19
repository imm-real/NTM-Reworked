package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.BatterySocketBlockEntity;
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

public final class BatterySocketMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 9;
    private final Container socket;
    private final ContainerData data;

    public BatterySocketMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, findSocket(inventory, buffer.readBlockPos()), new SimpleContainerData(DATA_COUNT));
    }

    public BatterySocketMenu(int containerId, Inventory inventory, Container socket, ContainerData data) {
        super(ModMenus.BATTERY_SOCKET.get(), containerId);
        checkContainerSize(socket, 1);
        checkContainerDataCount(data, DATA_COUNT);
        this.socket = socket;
        this.data = data;
        addSlot(new Slot(socket, 0, 35, 35));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 99 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 157));
        }
        addDataSlots(data);
    }

    private static Container findSocket(Inventory inventory, BlockPos position) {
        if (inventory.player.level().getBlockEntity(position) instanceof BatterySocketBlockEntity socket) {
            return socket;
        }
        return new SimpleContainer(1);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof HeBatteryItem) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(socket instanceof BatterySocketBlockEntity batterySocket)) return false;
        return switch (id) {
            case 0 -> { batterySocket.cycleLowMode(); yield true; }
            case 1 -> { batterySocket.cycleHighMode(); yield true; }
            case 2 -> { batterySocket.cyclePriority(); yield true; }
            default -> false;
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return socket.stillValid(player);
    }

    public long power() { return join(data.get(0), data.get(1)); }
    public long maxPower() { return join(data.get(2), data.get(3)); }
    public long delta() { return join(data.get(4), data.get(5)); }
    public int lowMode() { return data.get(6); }
    public int highMode() { return data.get(7); }
    public int priority() { return data.get(8); }

    private static long join(int low, int high) {
        return Integer.toUnsignedLong(low) | ((long) high << 32);
    }
}
