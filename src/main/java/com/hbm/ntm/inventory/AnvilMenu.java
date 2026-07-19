package com.hbm.ntm.inventory;

import com.hbm.ntm.anvil.AnvilRecipes;
import com.hbm.ntm.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class AnvilMenu extends AbstractContainerMenu {
    private final SimpleContainer input = new SimpleContainer(8) {
        @Override public void setChanged() { super.setChanged(); updateSmithing(); }
    };
    private final ResultContainer output = new ResultContainer();
    private final Inventory playerInventory;
    private final int tier;
    private AnvilRecipes.Smithing activeSmithing;

    public AnvilMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readVarInt());
    }

    public AnvilMenu(int id, Inventory inventory, int tier) {
        super(ModMenus.ANVIL.get(), id);
        this.playerInventory = inventory;
        this.tier = tier;
        addSlot(new Slot(input, 0, 17, 27));
        addSlot(new Slot(input, 1, 53, 27));
        addSlot(new Slot(output, 0, 89, 27) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public void onTake(Player player, ItemStack stack) {
                AnvilRecipes.Smithing matched = activeSmithing;
                if (matched != null) {
                    input.removeItem(0, matched.leftConsumed());
                    input.removeItem(1, matched.rightConsumed());
                }
                super.onTake(player, stack);
                updateSmithing();
            }
        });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 140 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 198));
        updateSmithing();
    }

    public int tier() { return tier; }
    public Inventory playerInventory() { return playerInventory; }
    public ItemStack input(int index) { return input.getItem(index); }
    public ItemStack result() { return output.getItem(0); }

    private void updateSmithing() {
        activeSmithing = AnvilRecipes.findSmithing(input.getItem(0), input.getItem(1), tier);
        output.setItem(0, activeSmithing == null ? ItemStack.EMPTY : activeSmithing.output().get().copy());
        broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot source = slots.get(index);
        if (!source.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = source.getItem();
        ItemStack copy = stack.copy();
        if (index == 2) {
            if (!moveItemStackTo(stack, 3, slots.size(), true)) return ItemStack.EMPTY;
            source.onQuickCraft(stack, copy);
        } else if (index < 2) {
            if (!moveItemStackTo(stack, 3, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, 2, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) source.set(ItemStack.EMPTY); else source.setChanged();
        source.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return true; }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) clearContainer(player, input);
    }
}
