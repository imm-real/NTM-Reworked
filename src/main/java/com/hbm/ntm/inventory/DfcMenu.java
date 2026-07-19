package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.DfcBlockEntity;
import com.hbm.ntm.dfc.DfcKind;
import com.hbm.ntm.item.DfcCatalystItem;
import com.hbm.ntm.item.DfcCoreItem;
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
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class DfcMenu extends AbstractContainerMenu {
    private final Container machine;
    private final ContainerData data;
    private final DfcKind kind;

    public static DfcMenu core(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        return client(id, inventory, buffer, DfcKind.CORE);
    }
    public static DfcMenu emitter(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        return client(id, inventory, buffer, DfcKind.EMITTER);
    }
    public static DfcMenu injector(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        return client(id, inventory, buffer, DfcKind.INJECTOR);
    }
    public static DfcMenu receiver(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        return client(id, inventory, buffer, DfcKind.RECEIVER);
    }
    public static DfcMenu stabilizer(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        return client(id, inventory, buffer, DfcKind.STABILIZER);
    }

    private static DfcMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buffer, DfcKind kind) {
        BlockPos pos = buffer.readBlockPos();
        Container container = inventory.player.level().getBlockEntity(pos) instanceof DfcBlockEntity dfc
                && dfc.kind() == kind ? dfc : new SimpleContainer(kind.slots());
        return new DfcMenu(id, inventory, container, new SimpleContainerData(DfcBlockEntity.DATA_COUNT), kind);
    }

    public DfcMenu(int id, Inventory inventory, Container machine, ContainerData data, DfcKind kind) {
        super(type(kind), id);
        checkContainerSize(machine, kind.slots());
        checkContainerDataCount(data, DfcBlockEntity.DATA_COUNT);
        this.machine = machine;
        this.data = data;
        this.kind = kind;
        addMachineSlots();
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        addDataSlots(data);
    }

    private void addMachineSlots() {
        switch (kind) {
            case CORE -> {
                addSlot(new RestrictedSlot(machine, 0, 62, 53));
                addSlot(new RestrictedSlot(machine, 1, 80, 53));
                addSlot(new RestrictedSlot(machine, 2, 98, 53));
            }
            case INJECTOR -> {
                addSlot(new RestrictedSlot(machine, 0, 26, 17));
                addSlot(new OutputSlot(machine, 1, 26, 53));
                addSlot(new RestrictedSlot(machine, 2, 134, 17));
                addSlot(new OutputSlot(machine, 3, 134, 53));
            }
            case STABILIZER -> addSlot(new RestrictedSlot(machine, 0, 80, 17));
            default -> { }
        }
    }

    private static MenuType<DfcMenu> type(DfcKind kind) {
        return switch (kind) {
            case CORE -> ModMenus.DFC_CORE.get();
            case EMITTER -> ModMenus.DFC_EMITTER.get();
            case INJECTOR -> ModMenus.DFC_INJECTOR.get();
            case RECEIVER -> ModMenus.DFC_RECEIVER.get();
            case STABILIZER -> ModMenus.DFC_STABILIZER.get();
        };
    }

    public DfcKind kind() { return kind; }
    public DfcBlockEntity blockEntity() { return machine instanceof DfcBlockEntity dfc ? dfc : null; }
    public long power() { return unsignedLong(0, 1); }
    public long maxPower() { return unsignedLong(2, 3); }
    public int watts() { return data.get(4); }
    public int beam() { return data.get(5); }
    public boolean on() { return data.get(6) != 0; }
    public long signal() { return unsignedLong(7, 8); }
    public int tank0() { return data.get(9); }
    public int tank0Type() { return data.get(10); }
    public int tank1() { return data.get(11); }
    public int tank1Type() { return data.get(12); }
    public int field() { return data.get(13); }
    public int heat() { return data.get(14); }
    public int color() { return data.get(15); }
    public boolean meltdown() { return data.get(16) != 0; }
    public int consumption() { return data.get(17); }

    private long unsignedLong(int low, int high) {
        return data.get(low) & 0xFFFFFFFFL | (long) data.get(high) << 32;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int machineSlots = kind.slots();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = switch (kind) {
                case CORE -> stack.getItem() instanceof DfcCatalystItem
                        ? moveItemStackTo(stack, 0, 3, false)
                        : stack.getItem() instanceof DfcCoreItem && moveItemStackTo(stack, 1, 2, false);
                case INJECTOR -> moveItemStackTo(stack, 0, 1, false) || moveItemStackTo(stack, 2, 3, false);
                case STABILIZER -> stack.is(ModItems.AMS_LENS.get()) && moveItemStackTo(stack, 0, 1, false);
                default -> false;
            };
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return machine.stillValid(player); }

    private final class RestrictedSlot extends Slot {
        private RestrictedSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return machine.canPlaceItem(getContainerSlot(), stack); }
    }
    private static final class OutputSlot extends Slot {
        private OutputSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
