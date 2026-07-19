package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.BlastFurnaceBlockEntity;
import com.hbm.ntm.recipe.BlastFurnaceRecipes;
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

/** Source slot layout for the five-slot Blast Furnace. */
public final class BlastFurnaceMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 6;
    private static final int MACHINE_SLOT_COUNT = BlastFurnaceBlockEntity.SLOT_COUNT;
    private final Container furnace;
    private final ContainerData data;

    public BlastFurnaceMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(DATA_COUNT));
    }

    public BlastFurnaceMenu(int id, Inventory inventory, Container furnace, ContainerData data) {
        super(ModMenus.MACHINE_BLAST_FURNACE.get(), id);
        checkContainerSize(furnace, MACHINE_SLOT_COUNT);
        checkContainerDataCount(data, DATA_COUNT);
        this.furnace = furnace;
        this.data = data;

        addSlot(new Slot(furnace, BlastFurnaceBlockEntity.FUEL, 80, 81) {
            @Override public boolean mayPlace(ItemStack stack) {
                return BlastFurnaceBlockEntity.burnTime(stack) > 0;
            }
        });
        addSlot(new Slot(furnace, BlastFurnaceBlockEntity.INPUT_FIRST, 80, 27));
        addSlot(new Slot(furnace, BlastFurnaceBlockEntity.INPUT_SECOND, 80, 45));
        addSlot(output(furnace, BlastFurnaceBlockEntity.OUTPUT_FIRST, 134, 72));
        addSlot(output(furnace, BlastFurnaceBlockEntity.OUTPUT_SECOND, 134, 90));

        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 140 + row * 18));
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 198));
        }
        addDataSlots(data);
    }

    private static Slot output(Container furnace, int slot, int x, int y) {
        return new Slot(furnace, slot, x, y) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        };
    }

    private static Container find(Inventory inventory, BlockPos position) {
        return inventory.player.level().getBlockEntity(position) instanceof BlastFurnaceBlockEntity furnace
                ? furnace : new SimpleContainer(MACHINE_SLOT_COUNT);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < MACHINE_SLOT_COUNT) {
            if (!moveItemStackTo(stack, MACHINE_SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (BlastFurnaceBlockEntity.burnTime(stack) > 0) {
            if (!moveItemStackTo(stack, BlastFurnaceBlockEntity.FUEL,
                    BlastFurnaceBlockEntity.FUEL + 1, false)) return ItemStack.EMPTY;
        } else if (BlastFurnaceRecipes.isValidInput(stack)) {
            if (!moveItemStackTo(stack, BlastFurnaceBlockEntity.INPUT_FIRST,
                    BlastFurnaceBlockEntity.INPUT_SECOND + 1, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return furnace.stillValid(player); }
    public double progress() { return data.get(0) / 1_000_000D; }
    public double speed() { return data.get(1) / 1_000D; }
    public int fuel() { return data.get(2); }
    public int air() { return data.get(3); }
    public int flue() { return data.get(4); }
    public boolean progressing() { return data.get(5) != 0; }
}
