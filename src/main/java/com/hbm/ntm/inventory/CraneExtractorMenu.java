package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.CraneExtractorBlockEntity;
import com.hbm.ntm.item.MachineUpgradeItem;
import com.hbm.ntm.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class CraneExtractorMenu extends AbstractContainerMenu {
    private final Container extractor;
    private final ContainerData data;

    public CraneExtractorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(3));
    }

    public CraneExtractorMenu(int id, Inventory inventory, Container extractor, ContainerData data) {
        super(ModMenus.CRANE_EXTRACTOR.get(), id);
        checkContainerSize(extractor, CraneExtractorBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, 3);
        this.extractor = extractor;
        this.data = data;
        extractor.startOpen(inventory.player);

        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) {
            addSlot(new FilterSlot(extractor, column + row * 3, 71 + column * 18, 17 + row * 18));
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) {
            addSlot(new Slot(extractor, CraneExtractorBlockEntity.BUFFER_START + column + row * 3,
                    8 + column * 18, 17 + row * 18));
        }
        addSlot(new UpgradeSlot(extractor, CraneExtractorBlockEntity.STACK_UPGRADE, 152, 23,
                MachineUpgradeItem.Type.STACK));
        addSlot(new UpgradeSlot(extractor, CraneExtractorBlockEntity.EJECTOR_UPGRADE, 152, 47,
                MachineUpgradeItem.Type.EJECTOR));

        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 26 + column * 18, 103 + row * 18));
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 26 + column * 18, 161));
        }
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof CraneExtractorBlockEntity extractor
                ? extractor : new SimpleContainer(CraneExtractorBlockEntity.SLOT_COUNT);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < CraneExtractorBlockEntity.FILTER_END
                && extractor instanceof CraneExtractorBlockEntity blockEntity) {
            if (button == 1 && blockEntity.getItem(slotId).isEmpty()) return;
            if (button == 1) blockEntity.nextFilterMode(slotId);
            else blockEntity.setFilter(slotId, getCarried());
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || index < CraneExtractorBlockEntity.FILTER_END) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int machineSlots = CraneExtractorBlockEntity.SLOT_COUNT;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof MachineUpgradeItem upgrade
                && upgrade.type() == MachineUpgradeItem.Type.STACK) {
            if (!moveItemStackTo(stack, CraneExtractorBlockEntity.STACK_UPGRADE,
                    CraneExtractorBlockEntity.STACK_UPGRADE + 1, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof MachineUpgradeItem upgrade
                && upgrade.type() == MachineUpgradeItem.Type.EJECTOR) {
            if (!moveItemStackTo(stack, CraneExtractorBlockEntity.EJECTOR_UPGRADE,
                    CraneExtractorBlockEntity.EJECTOR_UPGRADE + 1, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, CraneExtractorBlockEntity.BUFFER_START,
                CraneExtractorBlockEntity.BUFFER_END, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(extractor instanceof CraneExtractorBlockEntity blockEntity)) return false;
        if (id == 0) blockEntity.toggleWhitelist();
        else if (id == 1) blockEntity.toggleMaxEject();
        else return false;
        return true;
    }

    @Override public boolean stillValid(Player player) { return extractor.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); extractor.stopOpen(player); }
    public boolean whitelist() { return data.get(0) != 0; }
    public boolean maxEject() { return data.get(1) != 0; }
    public boolean exactFilter(int slot) { return (data.get(2) & 1 << slot) != 0; }

    private static final class FilterSlot extends Slot {
        private FilterSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player player) { return false; }
        @Override public int getMaxStackSize() { return 1; }
    }

    private static final class UpgradeSlot extends Slot {
        private final MachineUpgradeItem.Type type;
        private UpgradeSlot(Container container, int slot, int x, int y, MachineUpgradeItem.Type type) {
            super(container, slot, x, y);
            this.type = type;
        }
        @Override public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof MachineUpgradeItem upgrade && upgrade.type() == type;
        }
        @Override public int getMaxStackSize() { return 1; }
    }
}
