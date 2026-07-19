package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.MicrowaveBlockEntity;
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

/** Microwave slots, still exactly where the texture expects them. */
public final class MicrowaveMenu extends AbstractContainerMenu {
    private final Container microwave;
    private final ContainerData data;

    public MicrowaveMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()),
                new SimpleContainerData(MicrowaveBlockEntity.DATA_COUNT));
    }

    public MicrowaveMenu(int id, Inventory inventory, Container microwave, ContainerData data) {
        super(ModMenus.MACHINE_MICROWAVE.get(), id);
        checkContainerSize(microwave, MicrowaveBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, MicrowaveBlockEntity.DATA_COUNT);
        this.microwave = microwave;
        this.data = data;

        addSlot(new Slot(microwave, MicrowaveBlockEntity.INPUT, 80, 35));
        addSlot(new CraftedOutputSlot(inventory.player, microwave, MicrowaveBlockEntity.OUTPUT, 140, 35));
        // Source used a plain Slot: any item may be put here manually, even though only HE batteries discharge.
        addSlot(new Slot(microwave, MicrowaveBlockEntity.BATTERY, 8, 53) {
            @Override public boolean mayPlace(ItemStack stack) { return true; }
        });

        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos position) {
        return inventory.player.level().getBlockEntity(position) instanceof MicrowaveBlockEntity microwave
                ? microwave : new SimpleContainer(MicrowaveBlockEntity.SLOT_COUNT);
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (!(microwave instanceof MicrowaveBlockEntity entity)) return false;
        if (id == 0) entity.adjustSpeed(1);
        else if (id == 1) entity.adjustSpeed(-1);
        else return false;
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < MicrowaveBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(stack, MicrowaveBlockEntity.SLOT_COUNT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, MicrowaveBlockEntity.INPUT,
                MicrowaveBlockEntity.INPUT + 1, true)
                && !moveItemStackTo(stack, MicrowaveBlockEntity.BATTERY,
                MicrowaveBlockEntity.BATTERY + 1, true)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return microwave.stillValid(player); }
    public int time() { return data.get(0); }
    public long power() { return data.get(1) & 0xFFFFFFFFL | (long) data.get(2) << 32; }
    public int speed() { return data.get(3); }

    private static final class CraftedOutputSlot extends Slot {
        private final Player player;
        private int crafted;

        private CraftedOutputSlot(Player player, Container container, int slot, int x, int y) {
            super(container, slot, x, y);
            this.player = player;
        }

        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public ItemStack remove(int amount) {
            if (hasItem()) crafted += Math.min(amount, getItem().getCount());
            return super.remove(amount);
        }
        @Override protected void onQuickCraft(ItemStack stack, int amount) {
            crafted += amount;
            finish(stack);
        }
        @Override public void onTake(Player player, ItemStack stack) {
            finish(stack);
            super.onTake(player, stack);
        }
        private void finish(ItemStack stack) {
            stack.onCraftedBy(player.level(), player, crafted);
            crafted = 0;
        }
    }
}
