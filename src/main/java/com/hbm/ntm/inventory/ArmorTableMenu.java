package com.hbm.ntm.inventory;

import com.hbm.ntm.armor.ArmorModHandler;
import com.hbm.ntm.item.ArmorCladdingItem;
import com.hbm.ntm.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

public final class ArmorTableMenu extends AbstractContainerMenu {
    private static final int[] MOD_X = {48, 84, 120, 156, 156, 120, 84, 48, 30};
    private static final int[] MOD_Y = {27, 27, 27, 45, 81, 99, 99, 99, 63};
    private final SimpleContainer upgrades = new SimpleContainer(ArmorModHandler.MOD_SLOTS);
    private final ResultContainer armor = new ResultContainer();
    private final Inventory playerInventory;
    private boolean loadingArmor;

    public ArmorTableMenu(int id, Inventory inventory, RegistryFriendlyByteBuf ignored) {
        this(id, inventory);
    }

    public ArmorTableMenu(int id, Inventory inventory) {
        super(ModMenus.ARMOR_TABLE.get(), id);
        playerInventory = inventory;
        for (int slot = 0; slot < ArmorModHandler.MOD_SLOTS; slot++) {
            addSlot(new UpgradeSlot(slot, MOD_X[slot], MOD_Y[slot]));
        }
        addSlot(new ArmorSlot(66, 63));
        for (int i = 0; i < 4; i++) {
            addSlot(new Slot(inventory, inventory.getContainerSize() - 1 - i, 5, 36 + i * 18) {
                @Override public int getMaxStackSize() { return 1; }
                @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof ArmorItem; }
            });
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 30 + column * 18, 140 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 30 + column * 18, 198));
    }

    public ItemStack armorStack() { return armor.getItem(0); }
    public ItemStack modStack(int slot) { return upgrades.getItem(slot); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot source = slots.get(index);
        if (!source.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = source.getItem();
        ItemStack copy = stack.copy();
        if (index <= ArmorModHandler.MOD_SLOTS) {
            if (index == ArmorModHandler.MOD_SLOTS
                    && moveItemStackTo(stack, ArmorModHandler.MOD_SLOTS + 1,
                    ArmorModHandler.MOD_SLOTS + 5, false)) {
                // Moved to the four convenience armor slots.
            } else if (!moveItemStackTo(stack, ArmorModHandler.MOD_SLOTS + 5, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            source.onTake(player, stack);
        } else if (stack.getItem() instanceof ArmorItem) {
            if (!moveItemStackTo(stack, ArmorModHandler.MOD_SLOTS, ArmorModHandler.MOD_SLOTS + 1, false))
                return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof ArmorCladdingItem) {
            Slot target = slots.get(ArmorModHandler.CLADDING);
            if (!target.mayPlace(stack)
                    || !moveItemStackTo(stack, ArmorModHandler.CLADDING, ArmorModHandler.CLADDING + 1, false))
                return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) source.set(ItemStack.EMPTY); else source.setChanged();
        return copy;
    }

    @Override public boolean stillValid(Player player) { return true; }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide) return;
        ItemStack armorStack = armor.getItem(0);
        for (int slot = 0; slot < ArmorModHandler.MOD_SLOTS; slot++) {
            ItemStack mod = upgrades.removeItemNoUpdate(slot);
            if (!mod.isEmpty()) {
                player.drop(mod, false);
                ArmorModHandler.removeMod(armorStack, slot);
            }
        }
        armorStack = armor.removeItemNoUpdate(0);
        if (!armorStack.isEmpty()) player.drop(armorStack, false);
    }

    private final class ArmorSlot extends Slot {
        private ArmorSlot(int x, int y) { super(armor, 0, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof ArmorItem; }
        @Override public int getMaxStackSize() { return 1; }

        @Override
        public void set(ItemStack stack) {
            super.set(stack);
            if (loadingArmor) return;
            loadingArmor = true;
            for (int slot = 0; slot < ArmorModHandler.MOD_SLOTS; slot++) upgrades.setItem(slot, ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                ItemStack[] mods = ArmorModHandler.pryMods(stack, playerInventory.player.registryAccess());
                for (int slot = 0; slot < ArmorModHandler.MOD_SLOTS; slot++) upgrades.setItem(slot, mods[slot]);
            }
            loadingArmor = false;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            super.onTake(player, stack);
            for (int slot = 0; slot < ArmorModHandler.MOD_SLOTS; slot++) {
                ItemStack mod = upgrades.getItem(slot);
                if (ArmorModHandler.isApplicable(stack, mod)) upgrades.setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private final class UpgradeSlot extends Slot {
        private final int modSlot;
        private UpgradeSlot(int modSlot, int x, int y) {
            super(upgrades, modSlot, x, y);
            this.modSlot = modSlot;
        }
        @Override public int getMaxStackSize() { return 1; }
        @Override public boolean mayPlace(ItemStack stack) {
            return modSlot == ArmorModHandler.CLADDING && stack.getItem() instanceof ArmorCladdingItem
                    && ArmorModHandler.isApplicable(armor.getItem(0), stack);
        }
        @Override public void set(ItemStack stack) {
            super.set(stack);
            if (!loadingArmor && !stack.isEmpty() && mayPlace(stack)) {
                ArmorModHandler.applyMod(armor.getItem(0), stack, playerInventory.player.registryAccess());
                armor.setChanged();
            }
        }
        @Override public void onTake(Player player, ItemStack stack) {
            super.onTake(player, stack);
            if (!loadingArmor) {
                ArmorModHandler.removeMod(armor.getItem(0), modSlot);
                armor.setChanged();
            }
        }
    }
}
