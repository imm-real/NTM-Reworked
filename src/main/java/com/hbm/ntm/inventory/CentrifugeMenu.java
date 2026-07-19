package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.CentrifugeBlockEntity;
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

public final class CentrifugeMenu extends AbstractContainerMenu {
    private final Container centrifuge;
    private final ContainerData data;

    public CentrifugeMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(4));
    }

    public CentrifugeMenu(int id, Inventory inventory, Container centrifuge, ContainerData data) {
        super(ModMenus.MACHINE_CENTRIFUGE.get(), id);
        checkContainerSize(centrifuge, CentrifugeBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, 4);
        this.centrifuge = centrifuge;
        this.data = data;

        addSlot(new RestrictedSlot(centrifuge, CentrifugeBlockEntity.INPUT, 36, 50));
        addSlot(new RestrictedSlot(centrifuge, CentrifugeBlockEntity.BATTERY, 9, 50));
        for (int slot = CentrifugeBlockEntity.OUTPUT_START; slot < CentrifugeBlockEntity.OUTPUT_END; slot++) {
            addSlot(new OutputSlot(centrifuge, slot, 63 + (slot - CentrifugeBlockEntity.OUTPUT_START) * 20, 50));
        }
        addSlot(new RestrictedSlot(centrifuge, 6, 149, 22));
        addSlot(new RestrictedSlot(centrifuge, 7, 149, 40));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 104 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 162));
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos position) {
        return inventory.player.level().getBlockEntity(position) instanceof CentrifugeBlockEntity centrifuge
                ? centrifuge : new SimpleContainer(CentrifugeBlockEntity.SLOT_COUNT);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < CentrifugeBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(stack, CentrifugeBlockEntity.SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof HeBatteryItem) {
            if (!moveItemStackTo(stack, CentrifugeBlockEntity.BATTERY,
                    CentrifugeBlockEntity.BATTERY + 1, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof MachineUpgradeItem) {
            if (!moveItemStackTo(stack, CentrifugeBlockEntity.UPGRADE_START,
                    CentrifugeBlockEntity.UPGRADE_END, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, CentrifugeBlockEntity.INPUT,
                CentrifugeBlockEntity.INPUT + 1, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return centrifuge.stillValid(player); }
    public int progress() { return data.get(0); }
    public long power() { return (data.get(1) & 0xFFFFFFFFL) | (long) data.get(2) << 32; }
    public boolean active() { return data.get(3) != 0; }

    private final class RestrictedSlot extends Slot {
        private RestrictedSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return centrifuge.canPlaceItem(getContainerSlot(), stack); }
    }

    private static final class OutputSlot extends Slot {
        private OutputSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
