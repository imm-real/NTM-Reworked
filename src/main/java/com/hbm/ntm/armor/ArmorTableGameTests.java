package com.hbm.ntm.armor;

import com.hbm.ntm.inventory.ArmorTableMenu;
import com.hbm.ntm.item.ArmorCladdingItem;
import com.hbm.ntm.radiation.RadiationSystem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("hbm")
@PrefixGameTestTemplate(false)
public final class ArmorTableGameTests {
    private ArmorTableGameTests() { }

    @GameTest(template = "empty")
    public static void claddingUsesExactNestedNbtAndRoundTrips(GameTestHelper helper) {
        ItemStack armor = new ItemStack(Items.IRON_CHESTPLATE);
        ItemStack lead = new ItemStack(ModItems.CLADDING_LEAD.get());
        ArmorModHandler.applyMod(armor, lead, helper.getLevel().registryAccess());
        CompoundTag root = armor.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        check(helper, root.contains(ArmorModHandler.MOD_COMPOUND_KEY), "Armor must use ntm_armor_mods");
        check(helper, root.getCompound(ArmorModHandler.MOD_COMPOUND_KEY)
                        .contains(ArmorModHandler.MOD_SLOT_KEY + ArmorModHandler.CLADDING),
                "Cladding must use mod_slot_5");
        check(helper, ArmorModHandler.pryMod(armor, ArmorModHandler.CLADDING,
                helper.getLevel().registryAccess()).is(ModItems.CLADDING_LEAD.get()),
                "Installed Lead Cladding must round-trip as an ItemStack");
        ArmorModHandler.removeMod(armor, ArmorModHandler.CLADDING);
        check(helper, !ArmorModHandler.hasMods(armor), "Removing the only mod must clear ntm_armor_mods");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void radiationCladdingsAndLegacyPriorityMatchSource(GameTestHelper helper) {
        float[] expected = {0.025F, 0.005F, 0.1F, 0.2F, 0.5F};
        var items = new net.minecraft.world.item.Item[] {
                ModItems.CLADDING_PAINT.get(), ModItems.CLADDING_RUBBER.get(), ModItems.CLADDING_LEAD.get(),
                ModItems.CLADDING_DESH.get(), ModItems.CLADDING_GHIORSIUM.get()
        };
        for (int i = 0; i < items.length; i++) {
            ItemStack armor = new ItemStack(Items.LEATHER_HELMET);
            ArmorModHandler.applyMod(armor, new ItemStack(items[i]), helper.getLevel().registryAccess());
            check(helper, Math.abs(ArmorModHandler.claddingResistance(armor,
                    helper.getLevel().registryAccess()) - expected[i]) < 0.0001F,
                    "Cladding radiation resistance must match source value " + expected[i]);
        }
        ItemStack legacy = new ItemStack(Items.IRON_HELMET);
        ArmorModHandler.applyMod(legacy, new ItemStack(ModItems.CLADDING_GHIORSIUM.get()),
                helper.getLevel().registryAccess());
        CompoundTag tag = legacy.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putFloat(ArmorModHandler.LEGACY_CLADDING_KEY, 0.75F);
        legacy.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        check(helper, ArmorModHandler.claddingResistance(legacy, helper.getLevel().registryAccess()) == 0.75F,
                "Legacy hfr_cladding must override installed cladding");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void armorTableInstallsAndReabsorbsDisplayedCladding(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ArmorTableMenu menu = new ArmorTableMenu(0, player.getInventory());
        ItemStack armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
        menu.getSlot(ArmorModHandler.MOD_SLOTS).set(armor);
        menu.getSlot(ArmorModHandler.CLADDING).set(new ItemStack(ModItems.CLADDING_IRON.get()));
        check(helper, ArmorModHandler.hasCladdingEffect(armor, ArmorCladdingItem.Effect.IRON,
                helper.getLevel().registryAccess()), "Putting Iron Cladding in slot 5 must update armor NBT");
        ItemStack taken = menu.getSlot(ArmorModHandler.MOD_SLOTS).remove(1);
        menu.getSlot(ArmorModHandler.MOD_SLOTS).onTake(player, taken);
        check(helper, menu.modStack(ArmorModHandler.CLADDING).isEmpty(),
                "Taking armor must reabsorb the displayed installed cladding");
        check(helper, ArmorModHandler.hasCladdingEffect(taken, ArmorCladdingItem.Effect.IRON,
                helper.getLevel().registryAccess()), "Taken armor must retain installed cladding");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void equippedLeadCladdingAddsToRadiationResistance(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
        ArmorModHandler.applyMod(armor, new ItemStack(ModItems.CLADDING_LEAD.get()),
                helper.getLevel().registryAccess());
        player.setItemSlot(EquipmentSlot.CHEST, armor);
        check(helper, Math.abs(RadiationSystem.calculateResistance(player) - 0.1F) < 0.0001F,
                "Lead Cladding must add 0.1 radiation resistance to arbitrary armor");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
