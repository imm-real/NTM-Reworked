package com.hbm.ntm.hazard;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.CoalDustGasBlock;
import com.hbm.ntm.item.HazmatArmorItem;
import com.hbm.ntm.radiation.RadiationSystem;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoalDustGasGameTests {
    private CoalDustGasGameTests() { }

    @GameTest(template = "empty")
    public static void coalDustSourcesPreserveSourceBlocksAndModernCoalOreVariant(GameTestHelper helper) {
        check(helper, CoalDustEvents.isCoalDustSource(Blocks.COAL_ORE.defaultBlockState()),
                "Coal Ore must emit airborne Coal Dust");
        check(helper, CoalDustEvents.isCoalDustSource(Blocks.DEEPSLATE_COAL_ORE.defaultBlockState()),
                "Modern Deepslate Coal Ore must share the source Coal Ore behavior");
        check(helper, CoalDustEvents.isCoalDustSource(Blocks.COAL_BLOCK.defaultBlockState()),
                "Coal Blocks must emit airborne Coal Dust");
        check(helper, CoalDustEvents.isCoalDustSource(ModBlocks.legacy("ore_lignite").get().defaultBlockState()),
                "Lignite Ore must emit airborne Coal Dust");
        check(helper, !CoalDustEvents.isCoalDustSource(Blocks.STONE.defaultBlockState()),
                "Unrelated blocks must not emit airborne Coal Dust");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void coalDustUsesExactPerFaceSpawnRoll(GameTestHelper helper) {
        check(helper, CoalDustEvents.spawnsFromRoll(0, true),
                "Roll zero beside air must spawn Coal Dust");
        check(helper, !CoalDustEvents.spawnsFromRoll(1, true)
                        && !CoalDustEvents.spawnsFromRoll(0, false),
                "The other half-roll and blocked faces must not spawn Coal Dust");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void coalDustGasAppliesExactUnprotectedBlackLungDose(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        CoalDustGasBlock gas = (CoalDustGasBlock) ModBlocks.legacy("gas_coal").get();
        gas.defaultBlockState().entityInside(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), player);
        check(helper, RadiationSystem.data(player).blackLung() == 10,
                "One unprotected Coal Dust collision must add exactly ten black-lung units");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void coarseFilterBlocksCoalDustWithoutWear(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack helmet = new ItemStack(ModItems.HAZMAT_HELMET.get());
        HazmatArmorItem.installFilter(helmet, new ItemStack(ModItems.GAS_MASK_FILTER.get()));
        player.setItemSlot(EquipmentSlot.HEAD, helmet);

        CoalDustGasBlock gas = (CoalDustGasBlock) ModBlocks.legacy("gas_coal").get();
        gas.defaultBlockState().entityInside(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), player);
        check(helper, RadiationSystem.data(player).blackLung() == 0,
                "Coarse-particle protection must block airborne Coal Dust");
        check(helper, HazmatArmorItem.installedFilter(helmet).getDamageValue() == 0,
                "Source airborne Coal Dust must not wear the blocking filter");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
