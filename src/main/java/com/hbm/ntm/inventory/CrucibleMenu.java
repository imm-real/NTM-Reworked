package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.CrucibleBlockEntity;
import com.hbm.ntm.recipe.CrucibleRecipes;
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

public final class CrucibleMenu extends AbstractContainerMenu {
    private final Container crucible;
    private final ContainerData data;

    public CrucibleMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(5));
    }

    public CrucibleMenu(int id, Inventory inventory, Container crucible, ContainerData data) {
        super(ModMenus.MACHINE_CRUCIBLE.get(), id);
        checkContainerSize(crucible, CrucibleBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, 5);
        this.crucible = crucible;
        this.data = data;
        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) {
            addSlot(new InputSlot(crucible, 1 + column + row * 3, 107 + column * 18, 18 + row * 18));
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 132 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 190));
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof CrucibleBlockEntity crucible
                ? crucible : new SimpleContainer(CrucibleBlockEntity.SLOT_COUNT);
    }

    public CrucibleBlockEntity blockEntity() { return crucible instanceof CrucibleBlockEntity entity ? entity : null; }
    public int progress() { return data.get(0); }
    public int heat() { return data.get(1); }
    public int selectedRecipe() { return data.get(2); }
    public boolean steelRecipe() { return selectedRecipe() == CrucibleBlockEntity.RECIPE_STEEL; }
    public int recipeTotal() { return data.get(3); }
    public int wasteTotal() { return data.get(4); }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (id < CrucibleBlockEntity.RECIPE_NONE || id > CrucibleRecipes.lastId()
                || !(crucible instanceof CrucibleBlockEntity entity)) return false;
        entity.selectRecipe(id);
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < 9) {
            if (!moveItemStackTo(stack, 9, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, 9, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return crucible.stillValid(player); }

    private final class InputSlot extends Slot {
        private InputSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return crucible.canPlaceItem(getContainerSlot(), stack); }
        @Override public int getMaxStackSize() { return 1; }
    }
}
