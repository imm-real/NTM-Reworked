package com.hbm.ntm.armor;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.hazard.HazardProtection;
import com.hbm.ntm.item.EnvsuitArmorItem;
import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.radiation.RadiationSystem;
import com.hbm.ntm.registry.ModArmorMaterials;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnvsuitArmorGameTests {
    private EnvsuitArmorGameTests() {
    }

    @GameTest(template = "empty")
    public static void materialAndRenderPassesMatchTheSourceSuit(GameTestHelper helper) {
        var material = ModArmorMaterials.ENVSUIT.value();
        helper.assertTrue(material.defense().get(ArmorItem.Type.HELMET) == 3
                        && material.defense().get(ArmorItem.Type.CHESTPLATE) == 8
                        && material.defense().get(ArmorItem.Type.LEGGINGS) == 6
                        && material.defense().get(ArmorItem.Type.BOOTS) == 3,
                "M1TTY must preserve source defense 3/8/6/3");
        helper.assertTrue(material.enchantmentValue() == 10,
                "M1TTY must preserve source enchantability 10");

        List<String> layers = material.layers().stream()
                .map(layer -> layer.texture(false).getPath())
                .toList();
        helper.assertTrue(layers.equals(List.of(
                        "textures/models/armor/envsuit_helmet_layer_1.png",
                        "textures/models/armor/envsuit_chest_layer_1.png",
                        "textures/models/armor/envsuit_arm_layer_1.png",
                        "textures/models/armor/envsuit_leg_layer_1.png",
                        "textures/models/armor/envsuit_lamp_layer_1.png")),
                "M1TTY material layers must stay ordered for the grouped OBJ renderer");
        helper.assertTrue(new ItemStack(ModItems.ENVSUIT_HELMET.get()).getMaxDamage() == 1
                        && new ItemStack(ModItems.ENVSUIT_PLATE.get()).getMaxDamage() == 1
                        && new ItemStack(ModItems.ENVSUIT_LEGS.get()).getMaxDamage() == 1
                        && new ItemStack(ModItems.ENVSUIT_BOOTS.get()).getMaxDamage() == 1,
                "Powered M1TTY pieces must retain the source one-point nominal durability");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void energyStoragePreservesComponentsAndConvertsArmorWear(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(ModItems.ENVSUIT_PLATE.get());
        EnvsuitArmorItem item = (EnvsuitArmorItem) stack.getItem();
        helper.assertTrue(item.getCharge(stack) == 100_000L
                        && item.getMaxCharge(stack) == 100_000L
                        && item.getChargeRate(stack) == 1_000L
                        && item.getDischargeRate(stack) == 0L,
                "A fresh M1TTY piece must start full with source battery rates");

        CompoundTag data = new CompoundTag();
        data.putString("component_sentinel", "kept");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        item.setCharge(stack, 50_000L);
        helper.assertTrue(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                        .copyTag().getString("component_sentinel").equals("kept"),
                "Writing M1TTY charge must preserve armor mods and other custom data");
        item.charge(stack, Long.MAX_VALUE);
        helper.assertTrue(item.getCharge(stack) == 100_000L, "M1TTY charging must clamp without overflow");
        item.discharge(stack, Long.MAX_VALUE);
        helper.assertTrue(item.getCharge(stack) == 0L, "M1TTY discharge must clamp at zero");

        item.setCharge(stack, 100_000L);
        int vanillaDamage = item.damageItem(stack, 4, player, ignored -> { });
        helper.assertTrue(vanillaDamage == 0 && item.getCharge(stack) == 99_000L
                        && stack.getDamageValue() == 0,
                "Four armor-wear points must consume 1,000 HE without accruing durability");
        var creative = helper.makeMockPlayer(GameType.CREATIVE);
        item.damageItem(stack, 4, creative, ignored -> { });
        helper.assertTrue(item.getCharge(stack) == 99_000L,
                "Creative wearers must not consume powered-armor charge from durability damage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void passiveProtectionIgnoresChargeButBonusesDoNot(GameTestHelper helper) {
        var wearer = helper.makeMockPlayer(GameType.SURVIVAL);
        equipFullSuit(wearer);
        helper.assertTrue(EnvsuitArmorItem.hasFullSet(wearer)
                        && EnvsuitArmorItem.hasFullPoweredSet(wearer),
                "A fresh exact M1TTY set must enable its powered full-set bonus");
        helper.assertTrue(Math.abs(RadiationSystem.calculateResistance(wearer) - 1.0F) < 0.000001F,
                "A full M1TTY set must preserve the source 1.0 radiation coefficient");

        ItemStack helmet = wearer.getItemBySlot(EquipmentSlot.HEAD);
        EnvsuitArmorItem helmetItem = (EnvsuitArmorItem) helmet.getItem();
        for (HazardProtection protection : HazardProtection.values()) {
            boolean expected = protection != HazardProtection.GAS_INERT;
            helper.assertTrue(helmetItem.hbm$protects(helmet, wearer, protection) == expected,
                    "M1TTY helmet FULL_PACKAGE coverage mismatch for " + protection);
        }
        ItemStack boots = wearer.getItemBySlot(EquipmentSlot.FEET);
        helper.assertTrue(!((EnvsuitArmorItem) boots.getItem()).hbm$protects(
                        boots, wearer, HazardProtection.PARTICLE_FINE),
                "FULL_PACKAGE protection belongs to the source helmet, not every suit piece");

        helmetItem.setCharge(helmet, 0L);
        helper.assertTrue(EnvsuitArmorItem.hasFullSet(wearer)
                        && !EnvsuitArmorItem.hasFullPoweredSet(wearer)
                        && Math.abs(RadiationSystem.calculateResistance(wearer) - 1.0F) < 0.000001F
                        && helmetItem.hbm$protects(helmet, wearer, HazardProtection.BACTERIA),
                "Depletion must disable only full-set bonuses, not passive radiation/hazard protection");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void damageTablePreservesExactCategoryAndCatchAllPrecedence(GameTestHelper helper) {
        var wearer = helper.makeMockPlayer(GameType.SURVIVAL);
        equipFullSuit(wearer);
        helper.assertTrue(EnvsuitArmorEvents.applyDamageTable(
                        wearer, wearer.damageSources().drown(), 20.0F) == 0.0F,
                "M1TTY drowning resistance must fully cancel drowning");
        helper.assertTrue(EnvsuitArmorEvents.applyDamageTable(
                        wearer, wearer.damageSources().fall(), 9.0F) == 1.0F,
                "M1TTY fall resistance must apply threshold 5 then 75% resistance");
        helper.assertTrue(EnvsuitArmorEvents.applyDamageTable(
                        wearer, wearer.damageSources().onFire(), 6.0F) == 1.0F,
                "M1TTY fire resistance must apply threshold 2 then 75% resistance");

        var attacker = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(EnvsuitArmorEvents.applyDamageTable(
                        wearer, wearer.damageSources().playerAttack(attacker), 10.0F) == 9.0F,
                "M1TTY ordinary catch-all resistance must reduce damage by 10%");
        helper.assertTrue(EnvsuitArmorEvents.applyDamageTable(
                        wearer, wearer.damageSources().generic(), 10.0F) == 10.0F,
                "M1TTY catch-all resistance must not affect bypass-armor damage");
        helper.assertTrue(EnvsuitArmorEvents.applyDamageTable(
                        wearer, wearer.damageSources().source(ModDamageTypes.ELECTRIC), 10.0F) == 45.0F,
                "Powered chest electric vulnerability must apply x5 before the 10% suit resistance");

        wearer.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        helper.assertTrue(EnvsuitArmorEvents.applyDamageTable(
                        wearer, wearer.damageSources().fall(), 9.0F) == 9.0F,
                "Custom resistance must require the exact complete set, independent of charge");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void poweredFullSetAppliesMovementAndUnderwaterBonuses(GameTestHelper helper) {
        var wearer = helper.makeMockPlayer(GameType.SURVIVAL);
        equipFullSuit(wearer);
        wearer.setSprinting(true);
        EnvsuitArmorEvents.applyTick(wearer);
        helper.assertTrue(wearer.getEffect(MobEffects.MOVEMENT_SPEED) != null
                        && wearer.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 1
                        && wearer.getEffect(MobEffects.JUMP) != null
                        && wearer.getEffect(MobEffects.JUMP).getAmplifier() == 0,
                "A powered full set must apply Speed II and Jump Boost I");
        helper.assertTrue(wearer.getAttribute(Attributes.MOVEMENT_SPEED)
                        .hasModifier(EnvsuitArmorEvents.SPRINT_SPEED_MODIFIER),
                "Sprinting in a powered full set must add the source flat 0.1 speed modifier");

        wearer.setAirSupply(1);
        wearer.zza = 1.0F;
        wearer.setDeltaMovement(Vec3.ZERO);
        EnvsuitArmorEvents.applyWaterBonuses(wearer);
        helper.assertTrue(wearer.getAirSupply() == 300
                        && wearer.getEffect(MobEffects.NIGHT_VISION) != null
                        && wearer.getEffect(MobEffects.NIGHT_VISION).getDuration() == 300
                        && wearer.getDeltaMovement().lengthSqr() > 0.0099D,
                "Underwater M1TTY must refill air, grant 15 seconds of night vision, and thrust along look");

        ((EnvsuitArmorItem) wearer.getItemBySlot(EquipmentSlot.CHEST).getItem())
                .setCharge(wearer.getItemBySlot(EquipmentSlot.CHEST), 0L);
        EnvsuitArmorEvents.applyTick(wearer);
        helper.assertTrue(!wearer.getAttribute(Attributes.MOVEMENT_SPEED)
                        .hasModifier(EnvsuitArmorEvents.SPRINT_SPEED_MODIFIER),
                "A depleted suit piece must immediately remove the sprint modifier");
        helper.succeed();
    }

    private static void equipFullSuit(net.minecraft.world.entity.player.Player player) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.ENVSUIT_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.ENVSUIT_PLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.ENVSUIT_LEGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.ENVSUIT_BOOTS.get()));
    }
}
