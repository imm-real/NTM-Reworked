package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.MachineShredderBlockEntity;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.item.ShredderBladeItem;
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

public final class MachineShredderMenu extends AbstractContainerMenu {
    private static final int MACHINE_SLOT_COUNT = 30;
    private final Container shredder;
    private final ContainerData data;

    public MachineShredderMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, findShredder(inventory, buffer.readBlockPos()), new SimpleContainerData(2));
    }

    public MachineShredderMenu(int containerId, Inventory inventory, Container shredder, ContainerData data) {
        super(ModMenus.MACHINE_SHREDDER.get(), containerId);
        checkContainerSize(shredder, MACHINE_SLOT_COUNT);
        checkContainerDataCount(data, 2);
        this.shredder = shredder;
        this.data = data;

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new Slot(shredder, column + row * 3, 44 + column * 18, 18 + row * 18));
            }
        }
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new OutputSlot(inventory.player, shredder, 9 + column + row * 3,
                        116 + column * 18, 18 + row * 18));
            }
        }
        addSlot(new Slot(shredder, MachineShredderBlockEntity.BLADE_LEFT, 44, 108));
        addSlot(new Slot(shredder, MachineShredderBlockEntity.BLADE_RIGHT, 80, 108));
        addSlot(new Slot(shredder, MachineShredderBlockEntity.BATTERY, 8, 108));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 151 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 209));
        }
        addDataSlots(data);
    }

    private static Container findShredder(Inventory inventory, BlockPos position) {
        if (inventory.player.level().getBlockEntity(position) instanceof MachineShredderBlockEntity shredder) {
            return shredder;
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
        } else if (copy.getItem() instanceof HeBatteryItem) {
            if (!moveItemStackTo(stack, MachineShredderBlockEntity.BATTERY,
                    MachineShredderBlockEntity.BATTERY + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (copy.getItem() instanceof ShredderBladeItem) {
            if (!moveItemStackTo(stack, MachineShredderBlockEntity.BLADE_LEFT,
                    MachineShredderBlockEntity.BLADE_RIGHT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, MachineShredderBlockEntity.INPUT_START,
                MachineShredderBlockEntity.INPUT_END, false)) {
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
        return shredder.stillValid(player);
    }

    public int progress() {
        return data.get(0);
    }

    public long power() {
        return data.get(1);
    }

    public int bladeState(int slot) {
        ItemStack blade = shredder.getItem(slot);
        if (!(blade.getItem() instanceof ShredderBladeItem)) {
            return 0;
        }
        if (blade.getMaxDamage() == 0) {
            return 1;
        }
        if (blade.getDamageValue() < blade.getMaxDamage() / 2) {
            return 1;
        }
        return blade.getDamageValue() != blade.getMaxDamage() ? 2 : 3;
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
