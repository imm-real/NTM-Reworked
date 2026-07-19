package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.AssemblyMachineBlockEntity;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.item.MachineUpgradeItem;
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

public final class AssemblyMachineMenu extends AbstractContainerMenu {
    private final Container assembler;
    private final ContainerData data;

    public AssemblyMachineMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(6));
    }

    public AssemblyMachineMenu(int id, Inventory inventory, Container assembler, ContainerData data) {
        super(ModMenus.MACHINE_ASSEMBLY_MACHINE.get(), id);
        checkContainerSize(assembler, AssemblyMachineBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, 6);
        this.assembler = assembler;
        this.data = data;
        addSlot(new RestrictedSlot(assembler, 0, 152, 81));
        addSlot(new RestrictedSlot(assembler, 1, 35, 126));
        addSlot(new RestrictedSlot(assembler, 2, 152, 108));
        addSlot(new RestrictedSlot(assembler, 3, 170, 108));
        for (int row = 0; row < 3; row++) for (int column = 0; column < 4; column++) {
            addSlot(new RestrictedSlot(assembler, 4 + column + row * 4, 8 + column * 18, 18 + row * 18));
        }
        addSlot(new OutputSlot(assembler, 16, 98, 45));
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 174 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 232));
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof AssemblyMachineBlockEntity assembler
                ? assembler : new SimpleContainer(AssemblyMachineBlockEntity.SLOT_COUNT);
    }

    public AssemblyMachineBlockEntity blockEntity() {
        return assembler instanceof AssemblyMachineBlockEntity machine ? machine : null;
    }

    public double progress() { return data.get(0) / 1_000_000D; }
    public long power() { return (data.get(1) & 0xFFFFFFFFL) | (long) data.get(2) << 32; }
    public long maxPower() { return (data.get(3) & 0xFFFFFFFFL) | (long) data.get(4) << 32; }
    public boolean active() { return data.get(5) != 0; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < AssemblyMachineBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(stack, AssemblyMachineBlockEntity.SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof HeBatteryItem) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (stack.is(ModItems.BLUEPRINTS.get())) {
            if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof MachineUpgradeItem) {
            if (!moveItemStackTo(stack, 2, 4, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 4, 16, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return assembler.stillValid(player); }

    private final class RestrictedSlot extends Slot {
        private RestrictedSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return assembler.canPlaceItem(getContainerSlot(), stack); }
    }

    private static final class OutputSlot extends Slot {
        private OutputSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
