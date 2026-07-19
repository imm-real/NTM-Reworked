package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.RadGenBlockEntity;
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

/** Twelve things go in, twelve irradiated things come out. */
public final class RadGenMenu extends AbstractContainerMenu {
    private final Container radGen;
    private final ContainerData data;

    public RadGenMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, findRadGen(inventory, buffer.readBlockPos()), new SimpleContainerData(40));
    }

    public RadGenMenu(int id, Inventory inventory, Container radGen, ContainerData data) {
        super(ModMenus.MACHINE_RADGEN.get(), id);
        checkContainerSize(radGen, RadGenBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, 40);
        this.radGen = radGen;
        this.data = data;

        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new Slot(radGen, column + row * 3, 8 + column * 18, 17 + row * 18));
            }
        }
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new OutputSlot(radGen, RadGenBlockEntity.LANE_COUNT + column + row * 3,
                        116 + column * 18, 17 + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 102 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 160));
        }
        addDataSlots(data);
    }

    private static Container findRadGen(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof RadGenBlockEntity radGen
                ? radGen : new SimpleContainer(RadGenBlockEntity.SLOT_COUNT);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < RadGenBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(stack, RadGenBlockEntity.SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, RadGenBlockEntity.LANE_COUNT, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return radGen.stillValid(player); }

    public long power() { return data.get(0) & 0xFFFFFFFFL | (long) data.get(1) << 32; }
    public int output() { return data.get(2); }
    public boolean isOn() { return data.get(3) != 0; }
    public int progress(int lane) { return data.get(4 + lane); }
    public int maxProgress(int lane) { return data.get(16 + lane); }
    public int production(int lane) { return data.get(28 + lane); }

    private static final class OutputSlot extends Slot {
        private OutputSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
