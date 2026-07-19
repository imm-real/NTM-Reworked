package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.ArcFurnaceBlockEntity;
import com.hbm.ntm.item.ArcElectrodeItem;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.item.MachineUpgradeItem;
import com.hbm.ntm.recipe.ArcFurnaceRecipes;
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

public final class ArcFurnaceMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 12;
    private final Container furnace;
    private final ContainerData data;

    public ArcFurnaceMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(DATA_COUNT));
    }

    public ArcFurnaceMenu(int id, Inventory inventory, Container furnace, ContainerData data) {
        super(ModMenus.MACHINE_ARC_FURNACE.get(), id);
        checkContainerSize(furnace, ArcFurnaceBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, DATA_COUNT);
        this.furnace = furnace;
        this.data = data;

        for (int index = 0; index < 3; index++) addSlot(new RestrictedSlot(index, 62 + index * 18, 22));
        addSlot(new RestrictedSlot(ArcFurnaceBlockEntity.BATTERY, 8, 108));
        addSlot(new RestrictedSlot(ArcFurnaceBlockEntity.UPGRADE, 152, 108));
        for (int row = 0; row < 4; row++) for (int column = 0; column < 5; column++) {
            addSlot(new FurnaceInputSlot(ArcFurnaceBlockEntity.GRID_START + column + row * 5,
                    44 + column * 18, 54 + row * 18));
        }
        for (int column = 0; column < 5; column++) {
            addSlot(new FurnaceInputSlot(ArcFurnaceBlockEntity.QUEUE_START + column, 44 + column * 18, 129));
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 174 + row * 18));
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 232));
        }
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos position) {
        return inventory.player.level().getBlockEntity(position) instanceof ArcFurnaceBlockEntity furnace
                ? furnace : new SimpleContainer(ArcFurnaceBlockEntity.SLOT_COUNT);
    }

    public ArcFurnaceBlockEntity blockEntity() {
        return furnace instanceof ArcFurnaceBlockEntity entity ? entity : null;
    }
    public float progress() { return data.get(0) / 10_000F; }
    public float lid() { return data.get(1) / 10_000F; }
    public long power() { return (data.get(2) & 0xFFFFFFFFL) | (long) data.get(3) << 32; }
    public long maxPower() { return (data.get(4) & 0xFFFFFFFFL) | (long) data.get(5) << 32; }
    public boolean progressing() { return data.get(6) != 0; }
    public boolean hasMaterial() { return data.get(7) != 0; }
    public int electrodeState(int index) { return data.get(8 + index); }
    public int delay() { return data.get(11); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < ArcFurnaceBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(stack, ArcFurnaceBlockEntity.SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof HeBatteryItem) {
            if (!moveItemStackTo(stack, ArcFurnaceBlockEntity.BATTERY,
                    ArcFurnaceBlockEntity.BATTERY + 1, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof ArcElectrodeItem) {
            if (!moveItemStackTo(stack, ArcFurnaceBlockEntity.ELECTRODE_START,
                    ArcFurnaceBlockEntity.ELECTRODE_END, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof MachineUpgradeItem) {
            if (!moveItemStackTo(stack, ArcFurnaceBlockEntity.UPGRADE,
                    ArcFurnaceBlockEntity.UPGRADE + 1, false)) return ItemStack.EMPTY;
        } else if (ArcFurnaceRecipes.find(stack) != null) {
            if (!moveItemStackTo(stack, ArcFurnaceBlockEntity.QUEUE_START,
                    ArcFurnaceBlockEntity.QUEUE_END, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return furnace.stillValid(player); }

    private class RestrictedSlot extends Slot {
        private RestrictedSlot(int slot, int x, int y) { super(furnace, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return furnace.canPlaceItem(getContainerSlot(), stack); }
    }

    private final class FurnaceInputSlot extends RestrictedSlot {
        private FurnaceInputSlot(int slot, int x, int y) { super(slot, x, y); }

        @Override public int getMaxStackSize(ItemStack stack) {
            ArcFurnaceRecipes.Recipe recipe = ArcFurnaceRecipes.find(stack);
            if (recipe == null) return stack.getMaxStackSize();
            int machineLimit = furnace instanceof ArcFurnaceBlockEntity entity ? entity.maxInputSize() : 1;
            return Math.min(machineLimit, stack.getMaxStackSize() / recipe.output().getCount());
        }
    }
}
