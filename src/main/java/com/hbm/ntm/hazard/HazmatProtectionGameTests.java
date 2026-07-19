package com.hbm.ntm.hazard;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.item.HazmatArmorItem;
import com.hbm.ntm.item.HazmatFilterItem;
import com.hbm.ntm.item.MaskFilterStorage;
import com.hbm.ntm.item.ProtectiveMaskItem;
import com.hbm.ntm.radiation.RadiationData;
import com.hbm.ntm.radiation.RadiationSystem;
import com.hbm.ntm.registry.ModArmorMaterials;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HazmatProtectionGameTests {
    private HazmatProtectionGameTests() {
    }

    @GameTest(template = "empty")
    public static void yellowSuitPreservesArmorDurabilityAndRadiationConstants(GameTestHelper helper) {
        var defense = ModArmorMaterials.HAZMAT.value().defense();
        helper.assertTrue(defense.get(ArmorItem.Type.HELMET) == 2
                        && defense.get(ArmorItem.Type.CHESTPLATE) == 5
                        && defense.get(ArmorItem.Type.LEGGINGS) == 4
                        && defense.get(ArmorItem.Type.BOOTS) == 1,
                "Yellow Hazmat armor must preserve source defense 2/5/4/1");
        helper.assertTrue(new ItemStack(ModItems.HAZMAT_HELMET.get()).getMaxDamage() == 660
                        && new ItemStack(ModItems.HAZMAT_PLATE.get()).getMaxDamage() == 960
                        && new ItemStack(ModItems.HAZMAT_LEGS.get()).getMaxDamage() == 900
                        && new ItemStack(ModItems.HAZMAT_BOOTS.get()).getMaxDamage() == 780,
                "Yellow Hazmat pieces must use vanilla slot bases with durability multiplier 60");

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.HAZMAT_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.HAZMAT_PLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.HAZMAT_LEGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.HAZMAT_BOOTS.get()));
        helper.assertTrue(Math.abs(RadiationSystem.calculateResistance(player) - 0.6F) < 0.000001F,
                "A full yellow Hazmat suit must contribute the source coefficient 0.6");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void filterFamiliesPreserveFullSourceCoverage(GameTestHelper helper) {
        helper.assertTrue(protects(HazmatFilterItem.FilterType.STANDARD, HazardProtection.PARTICLE_COARSE)
                        && protects(HazmatFilterItem.FilterType.STANDARD, HazardProtection.PARTICLE_FINE)
                        && protects(HazmatFilterItem.FilterType.STANDARD, HazardProtection.GAS_LUNG)
                        && protects(HazmatFilterItem.FilterType.STANDARD, HazardProtection.GAS_BLISTERING)
                        && protects(HazmatFilterItem.FilterType.STANDARD, HazardProtection.BACTERIA)
                        && !protects(HazmatFilterItem.FilterType.STANDARD, HazardProtection.GAS_MONOXIDE),
                "Standard filters must preserve the source coarse/fine/lung/blistering/bacteria coverage");
        helper.assertTrue(protects(HazmatFilterItem.FilterType.COMBO, HazardProtection.PARTICLE_COARSE)
                        && protects(HazmatFilterItem.FilterType.COMBO, HazardProtection.PARTICLE_FINE)
                        && protects(HazmatFilterItem.FilterType.COMBO, HazardProtection.GAS_LUNG)
                        && protects(HazmatFilterItem.FilterType.COMBO, HazardProtection.GAS_BLISTERING)
                        && protects(HazmatFilterItem.FilterType.COMBO, HazardProtection.BACTERIA)
                        && protects(HazmatFilterItem.FilterType.COMBO, HazardProtection.GAS_MONOXIDE),
                "Combo filters must add monoxide protection to the full standard coverage");
        helper.assertTrue(protects(HazmatFilterItem.FilterType.MONO, HazardProtection.PARTICLE_COARSE)
                        && !protects(HazmatFilterItem.FilterType.MONO, HazardProtection.PARTICLE_FINE)
                        && protects(HazmatFilterItem.FilterType.MONO, HazardProtection.GAS_MONOXIDE)
                        && protects(HazmatFilterItem.FilterType.RAG, HazardProtection.PARTICLE_COARSE)
                        && !protects(HazmatFilterItem.FilterType.RAG, HazardProtection.PARTICLE_FINE)
                        && protects(HazmatFilterItem.FilterType.PISS, HazardProtection.PARTICLE_COARSE)
                        && protects(HazmatFilterItem.FilterType.PISS, HazardProtection.GAS_LUNG)
                        && !protects(HazmatFilterItem.FilterType.PISS, HazardProtection.PARTICLE_FINE),
                "Catalytic and makeshift filter families must preserve their distinct source coverage");
        for (HazmatFilterItem.FilterType type : HazmatFilterItem.FilterType.values()) {
            helper.assertTrue(new ItemStack(type.item()).getMaxDamage() == 20_000,
                    "Every source gas-mask filter must retain 20,000 durability");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void standaloneMasksPreserveSourceDurabilityAndProtectionRules(GameTestHelper helper) {
        helper.assertTrue(new ItemStack(ModItems.GOGGLES.get()).getMaxDamage() == 165
                        && new ItemStack(ModItems.GAS_MASK.get()).getMaxDamage() == 165
                        && new ItemStack(ModItems.GAS_MASK_M65.get()).getMaxDamage() == 165
                        && new ItemStack(ModItems.GAS_MASK_MONO.get()).getMaxDamage() == 165
                        && new ItemStack(ModItems.GAS_MASK_OLDE.get()).getMaxDamage() == 165,
                "Iron-based source masks must retain helmet durability 11*15");
        helper.assertTrue(new ItemStack(ModItems.MASK_RAG.get()).getMaxDamage() == 1_650
                        && new ItemStack(ModItems.MASK_PISS.get()).getMaxDamage() == 1_650,
                "Improvised masks must retain the source rag-material durability multiplier 150");

        var wearer = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gasMask = new ItemStack(ModItems.GAS_MASK.get());
        MaskFilterStorage.install(gasMask, new ItemStack(ModItems.GAS_MASK_FILTER_COMBO.get()));
        ProtectiveMaskItem gasMaskItem = (ProtectiveMaskItem) gasMask.getItem();
        helper.assertTrue(gasMaskItem.hbm$protects(gasMask, wearer, HazardProtection.SAND)
                        && gasMaskItem.hbm$protects(gasMask, wearer, HazardProtection.LIGHT)
                        && gasMaskItem.hbm$protects(gasMask, wearer, HazardProtection.GAS_LUNG)
                        && gasMaskItem.hbm$protects(gasMask, wearer, HazardProtection.GAS_MONOXIDE)
                        && !gasMaskItem.hbm$protects(gasMask, wearer, HazardProtection.GAS_BLISTERING),
                "The full gas mask must combine intrinsic eye protection, its filter, and blistering blacklist");

        ItemStack halfMask = new ItemStack(ModItems.GAS_MASK_MONO.get());
        MaskFilterStorage.install(halfMask, new ItemStack(ModItems.GAS_MASK_FILTER_COMBO.get()));
        ProtectiveMaskItem halfMaskItem = (ProtectiveMaskItem) halfMask.getItem();
        helper.assertTrue(halfMaskItem.hbm$protects(halfMask, wearer, HazardProtection.PARTICLE_FINE)
                        && halfMaskItem.hbm$protects(halfMask, wearer, HazardProtection.GAS_MONOXIDE)
                        && !halfMaskItem.hbm$protects(halfMask, wearer, HazardProtection.GAS_LUNG)
                        && !halfMaskItem.hbm$protects(halfMask, wearer, HazardProtection.BACTERIA)
                        && !halfMaskItem.hbm$protects(halfMask, wearer, HazardProtection.GAS_BLISTERING),
                "The source half mask must reject eye/skin attacking hazards even with a combo filter");

        ItemStack dampMask = new ItemStack(ModItems.MASK_RAG.get());
        ItemStack trenchMask = new ItemStack(ModItems.MASK_PISS.get());
        helper.assertTrue(((ProtectiveMaskItem) dampMask.getItem()).hbm$protects(
                                dampMask, wearer, HazardProtection.PARTICLE_COARSE)
                        && !((ProtectiveMaskItem) dampMask.getItem()).hbm$protects(
                                dampMask, wearer, HazardProtection.GAS_LUNG)
                        && ((ProtectiveMaskItem) trenchMask.getItem()).hbm$protects(
                                trenchMask, wearer, HazardProtection.GAS_LUNG),
                "Damp and soaked improvised masks must retain their source protection difference");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void installingSwappingAndRemovingFiltersPreservesStacks(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack helmet = new ItemStack(ModItems.HAZMAT_HELMET.get());
        player.setItemSlot(EquipmentSlot.HEAD, helmet);

        ItemStack standard = new ItemStack(ModItems.GAS_MASK_FILTER.get());
        standard.setDamageValue(123);
        player.setItemInHand(InteractionHand.MAIN_HAND, standard);
        ItemStack consumed = standard.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getObject();
        helper.assertTrue(consumed.isEmpty() && HazmatArmorItem.installedFilter(helmet).is(ModItems.GAS_MASK_FILTER.get())
                        && HazmatArmorItem.installedFilter(helmet).getDamageValue() == 123,
                "Installing into an empty hood must consume the held filter and retain its wear");

        ItemStack combo = new ItemStack(ModItems.GAS_MASK_FILTER_COMBO.get());
        combo.setDamageValue(456);
        player.setItemInHand(InteractionHand.MAIN_HAND, combo);
        ItemStack previous = combo.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getObject();
        helper.assertTrue(previous.is(ModItems.GAS_MASK_FILTER.get()) && previous.getDamageValue() == 123
                        && HazmatArmorItem.installedFilter(helmet).is(ModItems.GAS_MASK_FILTER_COMBO.get())
                        && HazmatArmorItem.installedFilter(helmet).getDamageValue() == 456,
                "Swapping filters must return the old worn filter and install an exact copy of the new one");

        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, helmet);
        player.setShiftKeyDown(true);
        helmet.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        boolean returnedCombo = player.getInventory().items.stream().anyMatch(stack ->
                stack.is(ModItems.GAS_MASK_FILTER_COMBO.get()) && stack.getDamageValue() == 456);
        helper.assertTrue(HazmatArmorItem.installedFilter(helmet).isEmpty() && returnedCombo,
                "Sneak-use must remove the installed worn filter and return it to inventory");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyFilterInstallationConsumesCreativeStackLikeSource(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.HAZMAT_HELMET.get()));
        ItemStack filter = new ItemStack(ModItems.GAS_MASK_FILTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, filter);
        ItemStack result = filter.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getObject();
        helper.assertTrue(filter.isEmpty() && result.isEmpty(),
                "Source ItemFilter manually consumes an empty-slot installation even in creative mode");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void filterBreaksOnlyAfterExceedingTwentyThousandDamage(GameTestHelper helper) {
        ItemStack helmet = new ItemStack(ModItems.HAZMAT_HELMET.get());
        HazmatArmorItem.installFilter(helmet, new ItemStack(ModItems.GAS_MASK_FILTER.get()));
        HazmatArmorItem.damageFilter(helmet, 20_000);
        helper.assertTrue(!HazmatArmorItem.installedFilter(helmet).isEmpty()
                        && HazmatArmorItem.installedFilter(helmet).getDamageValue() == 20_000,
                "The source filter remains installed at exactly maximum damage");
        HazmatArmorItem.damageFilter(helmet, 1);
        helper.assertTrue(HazmatArmorItem.installedFilter(helmet).isEmpty(),
                "The source filter is removed only once damage exceeds its maximum");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void particleHazardsUseSourceProtectionAndWearRules(GameTestHelper helper) {
        var protectedPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack protectedHelmet = new ItemStack(ModItems.HAZMAT_HELMET.get());
        HazmatArmorItem.installFilter(protectedHelmet, new ItemStack(ModItems.GAS_MASK_FILTER.get()));
        protectedPlayer.setItemSlot(EquipmentSlot.HEAD, protectedHelmet);
        HazardSystem.applyToHolder(new ItemStack(ModItems.get("powder_asbestos").get(), 4), protectedPlayer);
        RadiationData protectedData = RadiationSystem.data(protectedPlayer);
        helper.assertTrue(protectedData.asbestos() == 0
                        && HazmatArmorItem.installedFilter(protectedHelmet).getDamageValue() == 3,
                "Fine protection must block asbestos and wear by hazard level, independent of stack size");

        ItemStack coal = new ItemStack(ModItems.get("powder_coal").get(), 64);
        HazardSystem.applyToHolder(coal, protectedPlayer);
        helper.assertTrue(protectedData.blackLung() == 0
                        && HazmatArmorItem.installedFilter(protectedHelmet).getDamageValue() == 6,
                "A 64-stack must guarantee the source coal-filter wear roll and damage by level three");
        helper.assertTrue(HazardSystem.coalFilterWearDenominator(1) == 64
                        && HazardSystem.coalFilterWearDenominator(64) == 1
                        && HazardSystem.coalFilterWearDenominator(80) == 1,
                "Coal filter wear must use max(65-stackSize, 1)");

        var coarseOnlyPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack coarseOnlyHelmet = new ItemStack(ModItems.HAZMAT_HELMET.get());
        HazmatArmorItem.installFilter(coarseOnlyHelmet, new ItemStack(ModItems.GAS_MASK_FILTER_MONO.get()));
        coarseOnlyPlayer.setItemSlot(EquipmentSlot.HEAD, coarseOnlyHelmet);
        HazardSystem.applyToHolder(new ItemStack(ModItems.get("powder_asbestos").get()), coarseOnlyPlayer);
        helper.assertTrue(RadiationSystem.data(coarseOnlyPlayer).asbestos() == 3
                        && HazmatArmorItem.installedFilter(coarseOnlyHelmet).getDamageValue() == 0,
                "A coarse-only filter must not stop or wear from fine asbestos particles");
        helper.succeed();
    }

    private static boolean protects(HazmatFilterItem.FilterType type, HazardProtection protection) {
        return ((HazmatFilterItem) type.item()).protects(protection);
    }
}
