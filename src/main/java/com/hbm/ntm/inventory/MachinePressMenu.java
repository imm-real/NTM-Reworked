package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.MachinePressBlockEntity;
import com.hbm.ntm.item.StampItem;
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

public final class MachinePressMenu extends AbstractContainerMenu {
    private static final int MACHINE_SLOT_COUNT = 13;
    private final Container press;
    private final ContainerData data;

    public MachinePressMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, findPress(playerInventory, buffer.readBlockPos()), new SimpleContainerData(3));
    }

    public MachinePressMenu(int containerId, Inventory playerInventory, Container press, ContainerData data) {
        super(ModMenus.MACHINE_PRESS.get(), containerId);
        checkContainerSize(press, MACHINE_SLOT_COUNT);
        checkContainerDataCount(data, 3);
        this.press = press;
        this.data = data;

        addSlot(new Slot(press, MachinePressBlockEntity.SLOT_FUEL, 26, 53));
        addSlot(new Slot(press, MachinePressBlockEntity.SLOT_STAMP, 80, 17));
        addSlot(new Slot(press, MachinePressBlockEntity.SLOT_INPUT, 80, 53));
        addSlot(new OutputSlot(playerInventory.player, press, MachinePressBlockEntity.SLOT_OUTPUT, 140, 35));
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(press, 4 + i, 8 + i * 18, 84));
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 132 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 190));
        }
        addDataSlots(data);
    }

    private static Container findPress(Inventory inventory, BlockPos position) {
        if (inventory.player.level().getBlockEntity(position) instanceof MachinePressBlockEntity press) {
            return press;
        }
        return new SimpleContainer(MACHINE_SLOT_COUNT);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < MACHINE_SLOT_COUNT) {
            if (!moveItemStackTo(stack, MACHINE_SLOT_COUNT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (MachinePressBlockEntity.fuelValue(copy) > 0) {
            if (!moveItemStackTo(stack, 0, 1, false) && !moveItemStackTo(stack, 4, 13, false)) {
                return ItemStack.EMPTY;
            }
        } else if (copy.getItem() instanceof StampItem) {
            if (!moveItemStackTo(stack, 1, 2, false) && !moveItemStackTo(stack, 4, 13, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 2, 3, false) && !moveItemStackTo(stack, 4, 13, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return press.stillValid(player);
    }

    public int speed() {
        return data.get(0);
    }

    public int burnTime() {
        return data.get(1);
    }

    public int press() {
        return data.get(2);
    }

    public double displayPress() {
        return press instanceof MachinePressBlockEntity blockEntity ? blockEntity.renderPress() : press();
    }

    private static final class OutputSlot extends Slot {
        private final Player player;
        private int removeCount;

        private OutputSlot(Player player, Container container, int slot, int x, int y) {
            super(container, slot, x, y);
            this.player = player;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public ItemStack remove(int amount) {
            if (hasItem()) {
                removeCount += Math.min(amount, getItem().getCount());
            }
            return super.remove(amount);
        }

        @Override
        protected void onQuickCraft(ItemStack stack, int amount) {
            removeCount += amount;
            checkTakeAchievements(stack);
        }

        @Override
        protected void checkTakeAchievements(ItemStack stack) {
            if (removeCount > 0) {
                stack.onCraftedBy(player.level(), player, removeCount);
            }
            removeCount = 0;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            checkTakeAchievements(stack);
            super.onTake(player, stack);
        }
    }
}
