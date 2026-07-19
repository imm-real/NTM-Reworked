package com.hbm.ntm.radiation;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.item.BloodBagItem;
import com.hbm.ntm.item.RadiationMedicineItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RadiationTreatmentGameTests {
    private RadiationTreatmentGameTests() {
    }

    @GameTest(template = "empty")
    public static void radawayTiersStackExactRemovalDurations(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        RadiationData data = RadiationSystem.data(player);

        ItemStack normal = new ItemStack(ModItems.RADAWAY.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, normal);
        ItemStack normalResult = normal.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getObject();
        helper.assertTrue(data.radAwayTicks() == 140 && normalResult.is(ModItems.IV_EMPTY.get()),
                "Normal RadAway must add 140 removal ticks and return one IV bag");

        ItemStack strong = new ItemStack(ModItems.RADAWAY_STRONG.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, strong);
        strong.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(data.radAwayTicks() == 490,
                "Strong RadAway must add 350 ticks to an existing RadAway duration");

        ItemStack elite = new ItemStack(ModItems.RADAWAY_FLUSH.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, elite);
        elite.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(data.radAwayTicks() == 990,
                "Elite RadAway must add its exact 500-tick duration");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void radawayRetainsManualCreativeConsumptionQuirk(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        ItemStack radaway = new ItemStack(ModItems.RADAWAY.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, radaway);
        ItemStack result = radaway.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getObject();
        helper.assertTrue(radaway.isEmpty() && result.is(ModItems.IV_EMPTY.get()),
                "Source RadAway manually decrements even in creative and still returns an IV bag");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void radxProvidesThreeMinutesOfPointTwoResistance(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack radx = new ItemStack(ModItems.RAD_X.get());
        ItemStack result = radx.getItem().finishUsingItem(radx, helper.getLevel(), player);
        RadiationData data = RadiationSystem.data(player);
        helper.assertTrue(result.isEmpty() && data.radXTicks() == 3_600,
                "Rad-X must be consumed after its ten-tick pill use and last exactly three minutes");
        helper.assertTrue(data.medicineResistance() == 0.2F,
                "Active Rad-X must contribute the source resistance coefficient 0.2");
        data.tickMedicine();
        helper.assertTrue(data.radXTicks() == 3_599,
                "Rad-X duration must decrement once per server living-entity tick");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void herbalPasteTreatsLungsAndRadiationWithExactSideEffects(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        RadiationData data = RadiationSystem.data(player);
        data.setRadiation(150.0F);
        data.setAsbestos(RadiationData.MAX_ASBESTOS);
        data.setBlackLung(RadiationData.MAX_BLACK_LUNG);
        ItemStack herbal = new ItemStack(ModItems.PILL_HERBAL.get());
        herbal.getItem().finishUsingItem(herbal, helper.getLevel(), player);

        helper.assertTrue(data.radiation() == 50.0F && data.asbestos() == 0
                        && data.blackLung() == RadiationData.MAX_BLACK_LUNG / 5,
                "Herbal Paste must remove 100 RAD, clear asbestos and reduce black lung to one fifth");
        helper.assertTrue(player.getEffect(MobEffects.CONFUSION).getDuration() == 200
                        && player.getEffect(MobEffects.WEAKNESS).getDuration() == 12_000
                        && player.getEffect(MobEffects.WEAKNESS).getAmplifier() == 2
                        && player.getEffect(MobEffects.DIG_SLOWDOWN).getDuration() == 12_000
                        && player.getEffect(MobEffects.POISON).getDuration() == 100,
                "Herbal Paste must retain its nausea, weakness, mining-fatigue and poison side effects");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ivBagPairTransfersExactlyFiveHealth(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setHealth(20.0F);
        ItemStack empty = new ItemStack(ModItems.IV_EMPTY.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, empty);
        ItemStack blood = empty.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getObject();
        helper.assertTrue(player.getHealth() == 15.0F && blood.is(ModItems.IV_BLOOD.get())
                        && ((BloodBagItem) blood.getItem()).type() == BloodBagItem.Type.BLOOD,
                "Using an IV bag must take five health and return a Blood Bag");

        player.setItemInHand(InteractionHand.MAIN_HAND, blood);
        ItemStack returned = blood.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getObject();
        helper.assertTrue(player.getHealth() == 20.0F && returned.is(ModItems.IV_EMPTY.get()),
                "Using a Blood Bag must heal five health and return the empty IV Bag");
        helper.succeed();
    }
}
