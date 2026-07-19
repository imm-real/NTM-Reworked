package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.OilDerrickBlockEntity;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.item.MachineUpgradeItem;
import com.hbm.ntm.registry.ModFluids;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

public final class OilDerrickMenu extends AbstractContainerMenu {
    private final Container derrick;
    private final ContainerData data;

    public OilDerrickMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(7));
    }

    public OilDerrickMenu(int id, Inventory inventory, Container derrick, ContainerData data) {
        super(ModMenus.MACHINE_WELL.get(), id);
        checkContainerSize(derrick, OilDerrickBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, 7);
        this.derrick = derrick;
        this.data = data;

        addSlot(new RestrictedSlot(derrick, OilDerrickBlockEntity.BATTERY, 8, 53));
        addSlot(new RestrictedSlot(derrick, OilDerrickBlockEntity.OIL_CONTAINER_INPUT, 80, 17));
        addSlot(new OutputSlot(derrick, OilDerrickBlockEntity.OIL_CONTAINER_OUTPUT, 80, 53));
        addSlot(new RestrictedSlot(derrick, OilDerrickBlockEntity.GAS_CONTAINER_INPUT, 125, 17));
        addSlot(new OutputSlot(derrick, OilDerrickBlockEntity.GAS_CONTAINER_OUTPUT, 125, 53));
        addSlot(new RestrictedSlot(derrick, 5, 152, 17));
        addSlot(new RestrictedSlot(derrick, 6, 152, 35));
        addSlot(new RestrictedSlot(derrick, 7, 152, 53));

        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof OilDerrickBlockEntity derrick
                ? derrick : new SimpleContainer(OilDerrickBlockEntity.SLOT_COUNT);
    }

    public OilDerrickBlockEntity blockEntity() {
        return derrick instanceof OilDerrickBlockEntity entity ? entity : null;
    }
    public long power() { return (data.get(0) & 0xFFFFFFFFL) | (long) data.get(1) << 32; }
    public long maxPower() { return (data.get(2) & 0xFFFFFFFFL) | (long) data.get(3) << 32; }
    public int indicator() { return data.get(4); }
    public int oil() { return data.get(5); }
    public int gas() { return data.get(6); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < OilDerrickBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(stack, OilDerrickBlockEntity.SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof MachineUpgradeItem) {
            if (!moveItemStackTo(stack, 5, 8, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof HeBatteryItem) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (canFillWith(stack, ModFluids.OIL.get())) {
            if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
        } else if (canFillWith(stack, ModFluids.GAS.get())) {
            if (!moveItemStackTo(stack, 3, 4, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    private static boolean canFillWith(ItemStack stack, net.minecraft.world.level.material.Fluid fluid) {
        IFluidHandlerItem handler = stack.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
        return handler != null && handler.fill(new FluidStack(fluid, 1_000),
                IFluidHandler.FluidAction.SIMULATE) == 1_000;
    }

    @Override public boolean stillValid(Player player) { return derrick.stillValid(player); }

    private final class RestrictedSlot extends Slot {
        private RestrictedSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return derrick.canPlaceItem(getContainerSlot(), stack); }
    }

    private static final class OutputSlot extends Slot {
        private OutputSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
