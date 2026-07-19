package com.hbm.ntm.armor;

import com.hbm.ntm.item.ArmorCladdingItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class ArmorModHandler {
    public static final int HELMET_ONLY = 0;
    public static final int PLATE_ONLY = 1;
    public static final int LEGS_ONLY = 2;
    public static final int BOOTS_ONLY = 3;
    public static final int SERVOS = 4;
    public static final int CLADDING = 5;
    public static final int KEVLAR = 6;
    public static final int EXTRA = 7;
    public static final int BATTERY = 8;
    public static final int MOD_SLOTS = 9;
    public static final String MOD_COMPOUND_KEY = "ntm_armor_mods";
    public static final String MOD_SLOT_KEY = "mod_slot_";
    public static final String LEGACY_CLADDING_KEY = "hfr_cladding";

    private ArmorModHandler() { }

    public static boolean isApplicable(ItemStack armor, ItemStack mod) {
        return !armor.isEmpty() && armor.getItem() instanceof ArmorItem
                && !mod.isEmpty() && mod.getItem() instanceof ArmorCladdingItem;
    }

    public static void applyMod(ItemStack armor, ItemStack mod, HolderLookup.Provider registries) {
        if (!isApplicable(armor, mod)) return;
        CompoundTag root = data(armor);
        CompoundTag mods = root.getCompound(MOD_COMPOUND_KEY);
        mods.put(MOD_SLOT_KEY + CLADDING, mod.copyWithCount(1).save(registries));
        root.put(MOD_COMPOUND_KEY, mods);
        armor.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    public static void removeMod(ItemStack armor, int slot) {
        if (armor.isEmpty()) return;
        CompoundTag root = data(armor);
        if (!root.contains(MOD_COMPOUND_KEY)) return;
        CompoundTag mods = root.getCompound(MOD_COMPOUND_KEY);
        mods.remove(MOD_SLOT_KEY + slot);
        if (mods.isEmpty()) root.remove(MOD_COMPOUND_KEY); else root.put(MOD_COMPOUND_KEY, mods);
        armor.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    public static boolean hasMods(ItemStack armor) {
        return !armor.isEmpty() && data(armor).contains(MOD_COMPOUND_KEY);
    }

    public static ItemStack pryMod(ItemStack armor, int slot, HolderLookup.Provider registries) {
        if (!hasMods(armor)) return ItemStack.EMPTY;
        CompoundTag mods = data(armor).getCompound(MOD_COMPOUND_KEY);
        String key = MOD_SLOT_KEY + slot;
        if (!mods.contains(key)) return ItemStack.EMPTY;
        ItemStack result = ItemStack.parseOptional(registries, mods.getCompound(key));
        if (result.isEmpty()) removeMod(armor, slot);
        return result;
    }

    public static ItemStack[] pryMods(ItemStack armor, HolderLookup.Provider registries) {
        ItemStack[] result = new ItemStack[MOD_SLOTS];
        for (int slot = 0; slot < MOD_SLOTS; slot++) result[slot] = pryMod(armor, slot, registries);
        return result;
    }

    public static float claddingResistance(ItemStack armor, HolderLookup.Provider registries) {
        CompoundTag root = data(armor);
        float legacy = root.getFloat(LEGACY_CLADDING_KEY);
        if (legacy > 0.0F) return legacy;
        ItemStack cladding = pryMod(armor, CLADDING, registries);
        return cladding.getItem() instanceof ArmorCladdingItem item
                && item.effect() == ArmorCladdingItem.Effect.RADIATION ? item.radiationResistance() : 0.0F;
    }

    public static boolean hasCladdingEffect(ItemStack armor, ArmorCladdingItem.Effect effect,
                                             HolderLookup.Provider registries) {
        ItemStack cladding = pryMod(armor, CLADDING, registries);
        return cladding.getItem() instanceof ArmorCladdingItem item && item.effect() == effect;
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
}
