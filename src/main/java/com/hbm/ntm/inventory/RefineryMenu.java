package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.RefineryBlockEntity;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.InfiniteFluidBarrelItem;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.registry.ModItems;
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

public final class RefineryMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 10;
    private static final int MACHINE_SLOT_COUNT = 13;

    private final Container refinery;
    private final ContainerData data;

    public RefineryMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(DATA_COUNT));
    }

    public RefineryMenu(int id, Inventory inventory, Container refinery, ContainerData data) {
        super(ModMenus.MACHINE_REFINERY.get(), id);
        checkContainerSize(refinery, RefineryBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, DATA_COUNT);
        this.refinery = refinery;
        this.data = data;

        addSlot(new BatterySlot(refinery, 0, 186, 72));
        addSlot(new InputContainerSlot(refinery, 1, 8, 99));
        addSlot(new OutputSlot(refinery, 2, 8, 119));
        addSlot(new EmptyContainerSlot(refinery, 3, 86, 99, false));
        addSlot(new OutputSlot(refinery, 4, 86, 119));
        addSlot(new EmptyContainerSlot(refinery, 5, 106, 99, false));
        addSlot(new OutputSlot(refinery, 6, 106, 119));
        addSlot(new EmptyContainerSlot(refinery, 7, 126, 99, false));
        addSlot(new OutputSlot(refinery, 8, 126, 119));
        addSlot(new EmptyContainerSlot(refinery, 9, 146, 99, true));
        addSlot(new OutputSlot(refinery, 10, 146, 119));
        addSlot(new OutputSlot(refinery, 11, 58, 119));
        addSlot(new IdentifierSlot(refinery, 12, 186, 106));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        8 + column * 18, 150 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 208));
        }
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof RefineryBlockEntity refinery
                ? refinery : new SimpleContainer(RefineryBlockEntity.SLOT_COUNT);
    }

    public RefineryBlockEntity blockEntity() {
        return refinery instanceof RefineryBlockEntity entity ? entity : null;
    }

    public long power() {
        return unsignedLowHigh(data.get(0), data.get(1));
    }

    public long maxPower() {
        return unsignedLowHigh(data.get(2), data.get(3));
    }

    public int inputAmount() { return data.get(4); }
    public int heavyOilAmount() { return data.get(5); }
    public int naphthaAmount() { return data.get(6); }
    public int lightOilAmount() { return data.get(7); }
    public int petroleumAmount() { return data.get(8); }
    public FluidIdentifierItem.Selection configuredFluid() {
        FluidIdentifierItem.Selection[] selections = FluidIdentifierItem.Selection.values();
        int ordinal = data.get(9);
        return ordinal >= 0 && ordinal < selections.length
                ? selections[ordinal] : FluidIdentifierItem.Selection.NONE;
    }
    public boolean hasRecipe() {
        return configuredFluid() == FluidIdentifierItem.Selection.HOTOIL;
    }

    private static long unsignedLowHigh(int low, int high) {
        return (low & 0xFFFFFFFFL) | (long) high << 32;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index < MACHINE_SLOT_COUNT) {
            if (!moveItemStackTo(stack, MACHINE_SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, 1, false)
                && !moveItemStackTo(stack, 1, 2, false)
                && !moveItemStackTo(stack, 3, 4, false)
                && !moveItemStackTo(stack, 5, 6, false)
                && !moveItemStackTo(stack, 7, 8, false)
                && !moveItemStackTo(stack, 9, 10, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return refinery.stillValid(player);
    }

    private static final class BatterySlot extends Slot {
        private BatterySlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof HeBatteryItem;
        }
    }

    private static final class InputContainerSlot extends Slot {
        private InputContainerSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override public boolean mayPlace(ItemStack stack) {
            return container.canPlaceItem(getContainerSlot(), stack);
        }
    }

    private static final class EmptyContainerSlot extends Slot {
        private final boolean gas;

        private EmptyContainerSlot(Container container, int slot, int x, int y, boolean gas) {
            super(container, slot, x, y);
            this.gas = gas;
        }

        @Override public boolean mayPlace(ItemStack stack) {
            return InfiniteFluidBarrelItem.is(stack) || stack.is(ModItems.FLUID_TANK_EMPTY.get())
                    || stack.is(gas ? ModItems.GAS_EMPTY.get() : ModItems.CANISTER_EMPTY.get());
        }
    }

    private static final class IdentifierSlot extends Slot {
        private IdentifierSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof FluidIdentifierItem;
        }
    }

    private static final class OutputSlot extends Slot {
        private OutputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
