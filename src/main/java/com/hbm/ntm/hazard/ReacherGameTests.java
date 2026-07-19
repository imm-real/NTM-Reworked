package com.hbm.ntm.hazard;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.radiation.RadiationData;
import com.hbm.ntm.radiation.RadiationSystem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReacherGameTests {
    private ReacherGameTests() {
    }

    @GameTest(template = "empty")
    public static void reacherPreservesNormalAnd528RadiationFunctions(GameTestHelper helper) {
        float radiation = 3.5F / 20.0F;
        double shifted = radiation + 2.0D;
        float expectedNormal = (float) (Math.sqrt(radiation + 1.0D / (shifted * shifted)) - 1.0D / shifted);
        helper.assertTrue(Math.abs(HazardSystem.reacherRadiation(radiation, false) - expectedNormal) < 0.000001F,
                "Normal-mode Reacher radiation must use BobMathUtil.squirt exactly");
        helper.assertTrue(Math.abs(HazardSystem.reacherRadiation(radiation, true) - radiation / 49.0F) < 0.000001F,
                "528-mode Reacher radiation must use the source inverse-square distance-seven divisor");
        helper.assertTrue(HazardSystem.reacherBlocksHeat(true, false)
                        && !HazardSystem.reacherBlocksHeat(true, true)
                        && !HazardSystem.reacherBlocksHeat(false, false),
                "The Reacher must block heat only when present outside 528 mode");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void carriedReacherTransformsActualInventoryRadiation(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.tickCount = 200;
        player.getInventory().add(new ItemStack(ModItems.REACHER.get()));
        HazardSystem.applyToHolder(new ItemStack(ModItems.URANIUM_INGOT.get()), player);

        float raw = 0.35F / 20.0F;
        float expected = HazardSystem.reacherRadiation(raw, false);
        RadiationData data = RadiationSystem.data(player);
        helper.assertTrue(Math.abs(data.radiation() - expected) < 0.000001F
                        && Math.abs(data.radEnv() - expected) < 0.000001F,
                "A Reacher anywhere in the source main inventory must transform carried-item exposure");
        helper.assertTrue(new ItemStack(ModItems.REACHER.get()).getMaxStackSize() == 1,
                "The Tungsten Reacher must retain stack size one");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modernOffhandDoesNotExpandSourceReacherInventoryScope(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.tickCount = 200;
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.REACHER.get()));
        HazardSystem.applyToHolder(new ItemStack(ModItems.URANIUM_INGOT.get()), player);

        float raw = 0.35F / 20.0F;
        helper.assertTrue(Math.abs(RadiationSystem.data(player).radiation() - raw) < 0.000001F,
                "The modern offhand must not expand the source main-inventory Reacher search");
        helper.assertTrue(HazardSystem.reacherBlocksHeat(true, false)
                        && !HazardSystem.reacherBlocksHeat(true, true),
                "Heat blocking must remain enabled only in normal mode");
        helper.succeed();
    }
}
