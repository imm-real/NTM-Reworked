package com.hbm.ntm.armor;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.hazard.HazardProtection;
import com.hbm.ntm.item.DntArmorItem;
import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.radiation.RadiationSystem;
import com.hbm.ntm.registry.ModArmorMaterials;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Compile-time checks for the DNT Nano Suit's expensive promises. */
@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DntArmorGameTests {
    private DntArmorGameTests() {
    }

    @GameTest(template = "empty")
    public static void materialBatteryAndRenderLayersMatchSource(GameTestHelper helper) {
        var material = ModArmorMaterials.DNT_NANO.value();
        helper.assertTrue(material.defense().get(ArmorItem.Type.HELMET) == 3
                        && material.defense().get(ArmorItem.Type.CHESTPLATE) == 8
                        && material.defense().get(ArmorItem.Type.LEGGINGS) == 6
                        && material.defense().get(ArmorItem.Type.BOOTS) == 3,
                "DNT must retain source defense 3/8/6/3");
        helper.assertTrue(material.enchantmentValue() == 0,
                "DNT must retain source enchantability zero");
        helper.assertTrue(material.layers().stream().map(layer -> layer.texture(false).getPath()).toList()
                        .equals(List.of("textures/models/armor/dnt_helmet_layer_1.png",
                                "textures/models/armor/dnt_chest_layer_1.png",
                                "textures/models/armor/dnt_arm_layer_1.png",
                                "textures/models/armor/dnt_leg_layer_1.png")),
                "DNT grouped OBJ texture passes must remain in source order");

        ItemStack plate = new ItemStack(ModItems.DNS_PLATE.get());
        DntArmorItem item = (DntArmorItem) plate.getItem();
        helper.assertTrue(item.getCharge(plate) == 1_000_000_000L
                        && item.getChargeRate(plate) == 1_000_000L
                        && item.getDischargeRate(plate) == 0L,
                "Fresh DNT armor must preserve the billion-HE source battery");
        item.damageItem(plate, 4, helper.makeMockPlayer(GameType.SURVIVAL), ignored -> { });
        helper.assertTrue(item.getCharge(plate) == 999_600_000L && plate.getDamageValue() == 0,
                "DNT wear must convert each point to 100,000 HE without vanilla durability");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void passiveAndPoweredProtectionStayDistinct(GameTestHelper helper) {
        var wearer = helper.makeMockPlayer(GameType.SURVIVAL);
        equip(wearer);
        helper.assertTrue(DntArmorItem.hasFullPoweredSet(wearer), "Fresh exact DNT set must be powered");
        helper.assertTrue(Math.abs(RadiationSystem.calculateResistance(wearer) - 5.0F) < 0.000001F,
                "DNT must preserve the source 5.0 radiation coefficient");
        ItemStack helmet = wearer.getItemBySlot(EquipmentSlot.HEAD);
        DntArmorItem helmetItem = (DntArmorItem) helmet.getItem();
        for (HazardProtection protection : HazardProtection.values()) {
            helper.assertTrue(helmetItem.hbm$protects(helmet, wearer, protection)
                            == (protection != HazardProtection.GAS_INERT),
                    "DNT FULL_PACKAGE mismatch for " + protection);
        }
        helmetItem.setCharge(helmet, 0L);
        helper.assertTrue(DntArmorItem.hasFullSet(wearer) && !DntArmorItem.hasFullPoweredSet(wearer)
                        && Math.abs(RadiationSystem.calculateResistance(wearer) - 5.0F) < 0.000001F,
                "Charge depletion may disable powered bonuses, not passive protection");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void resistanceAndJetMovementMatchSource(GameTestHelper helper) {
        var wearer = helper.makeMockPlayer(GameType.SURVIVAL);
        equip(wearer);
        helper.assertTrue(DntArmorEvents.applyDamageTable(
                        wearer, wearer.damageSources().playerAttack(helper.makeMockPlayer(GameType.SURVIVAL)), 5000.0F) == 0.0F,
                "Powered DNT must cancel non-explosion attacks");
        helper.assertTrue(DntArmorEvents.applyDamageTable(
                        wearer, wearer.damageSources().source(ModDamageTypes.NUCLEAR_BLAST), 200.0F) == 0.001F,
                "DNT explosion threshold, 99% resistance, and powered x0.001 must retain source order");

        wearer.setDeltaMovement(Vec3.ZERO);
        helper.assertTrue(DntArmorEvents.applyJetpackMovement(wearer, true, true)
                        && Math.abs(wearer.getDeltaMovement().y - 0.2D) < 0.000001D,
                "Space thrust must add 0.2 vertical speed");
        wearer.setDeltaMovement(0.2D, -0.5D, 0.2D);
        wearer.zza = 0.0F;
        DntArmorEvents.applyJetpackMovement(wearer, true, false);
        helper.assertTrue(Math.abs(wearer.getDeltaMovement().x - 0.21D) < 0.000001D
                        && Math.abs(wearer.getDeltaMovement().y + 0.3D) < 0.000001D,
                "Automatic hover must apply source horizontal acceleration and descent braking");
        helper.succeed();
    }

    private static void equip(net.minecraft.world.entity.player.Player player) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.DNS_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.DNS_PLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.DNS_LEGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.DNS_BOOTS.get()));
    }
}
