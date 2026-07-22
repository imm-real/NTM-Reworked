package com.hbm.ntm.inventory;

import com.hbm.ntm.registry.ModMenus;
import com.hbm.ntm.weapon.WeaponModManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class WeaponModifierMenu extends AbstractContainerMenu {
    private final SimpleContainer gun = new SimpleContainer(1);
    private final SimpleContainer mods = new SimpleContainer(WeaponModManager.TABLE_SLOTS);
    private final Inventory inventory;
    private boolean loading;
    private int config;

    public WeaponModifierMenu(int id, Inventory inventory, RegistryFriendlyByteBuf ignored) {
        this(id, inventory);
    }

    public WeaponModifierMenu(int id, Inventory inventory) {
        super(ModMenus.WEAPON_MODIFIER.get(), id);
        this.inventory = inventory;
        addSlot(new GunSlot());
        for (int slot = 0; slot < WeaponModManager.TABLE_SLOTS; slot++) {
            addSlot(new ModSlot(slot, 44 + slot * 18, 108));
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 158 + row * 18));
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 216));
        }
    }

    public ItemStack gunStack() { return gun.getItem(0); }
    public int config() { return config; }
    public int configCount() { return WeaponModManager.configCount(gunStack()); }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= configCount()) return false;
        config = id;
        loadInstalled();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot source = slots.get(index);
        if (!source.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = source.getItem();
        ItemStack copy = stack.copy();
        if (index < 8) {
            if (!moveItemStackTo(stack, 8, slots.size(), true)) return ItemStack.EMPTY;
        } else if (WeaponModManager.configCount(stack) > 0) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (WeaponModManager.isMod(stack)) {
            if (!moveItemStackTo(stack, 1, 8, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) source.set(ItemStack.EMPTY); else source.setChanged();
        source.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return true; }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide) return;
        ItemStack currentGun = gun.removeItemNoUpdate(0);
        WeaponModManager.uninstall(currentGun, config);
        if (!currentGun.isEmpty()) player.drop(currentGun, false);
        for (int slot = 0; slot < WeaponModManager.TABLE_SLOTS; slot++) {
            ItemStack mod = mods.removeItemNoUpdate(slot);
            if (!mod.isEmpty()) player.drop(mod, false);
        }
    }

    private void loadInstalled() {
        loading = true;
        for (int slot = 0; slot < WeaponModManager.TABLE_SLOTS; slot++) mods.setItem(slot, ItemStack.EMPTY);
        List<ItemStack> installed = WeaponModManager.installedMods(gunStack(), config);
        for (int slot = 0; slot < installed.size() && slot < WeaponModManager.TABLE_SLOTS; slot++) {
            mods.setItem(slot, installed.get(slot));
        }
        loading = false;
    }

    private void preview() {
        if (loading || gunStack().isEmpty()) return;
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < WeaponModManager.TABLE_SLOTS; slot++) stacks.add(mods.getItem(slot));
        WeaponModManager.install(gunStack(), config, stacks);
        gun.setChanged();
    }

    private final class GunSlot extends Slot {
        private GunSlot() { super(gun, 0, 8, 108); }
        @Override public boolean mayPlace(ItemStack stack) {
            return WeaponModManager.configCount(stack) > 0;
        }
        @Override public int getMaxStackSize() { return 1; }
        @Override public void set(ItemStack stack) {
            super.set(stack);
            if (!loading) loadInstalled();
        }
        @Override public void onTake(Player player, ItemStack stack) {
            preview();
            for (int slot = 0; slot < WeaponModManager.TABLE_SLOTS; slot++) {
                if (WeaponModManager.isApplicable(stack, mods.getItem(slot), config)) {
                    mods.setItem(slot, ItemStack.EMPTY);
                }
            }
            super.onTake(player, stack);
        }
    }

    private final class ModSlot extends Slot {
        private ModSlot(int slot, int x, int y) { super(mods, slot, x, y); }
        @Override public int getMaxStackSize() { return 1; }
        @Override public boolean mayPlace(ItemStack stack) {
            return WeaponModManager.isApplicable(gunStack(), stack, config);
        }
        @Override public void set(ItemStack stack) { super.set(stack); preview(); }
        @Override public void onTake(Player player, ItemStack stack) { super.onTake(player, stack); preview(); }
    }
}
