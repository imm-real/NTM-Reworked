package com.hbm.ntm.hazard;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.RadonGasBlock;
import com.hbm.ntm.block.UraniumOutgassingOreBlock;
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
import net.minecraft.world.level.block.RenderShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RadonGasGameTests {
    private RadonGasGameTests() { }

    @GameTest(template = "empty")
    public static void allActiveUraniumOresOutgasOnHarvestAndRandomTick(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        int x = 2;
        for (String id : new String[]{"ore_uranium", "ore_gneiss_uranium", "ore_nether_uranium"}) {
            var ore = ModBlocks.legacy(id).get();
            check(helper, ore instanceof UraniumOutgassingOreBlock && ore.defaultBlockState().isRandomlyTicking(),
                    id + " must retain source random adjacent Radon outgassing");
            BlockPos pos = helper.absolutePos(new BlockPos(x, 2, 2));
            ore.playerDestroy(helper.getLevel(), player, pos, ore.defaultBlockState(), null, ItemStack.EMPTY);
            check(helper, helper.getLevel().getBlockState(pos).is(ModBlocks.legacy("gas_radon").get()),
                    id + " must leave Radon Gas when harvested");
            x += 2;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void radonAppliesExactBypassRadiationAndAsbestosDose(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.tickCount = 200;
        RadonGasBlock gas = (RadonGasBlock) ModBlocks.legacy("gas_radon").get();
        gas.defaultBlockState().entityInside(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), player);
        var data = RadiationSystem.data(player);
        check(helper, Math.abs(data.radiation() - 0.05F) < 0.0001F
                        && Math.abs(data.radEnv() - 0.05F) < 0.0001F && data.asbestos() == 1,
                "Unprotected Radon must add 0.05 bypass RAD, 0.05 environmental RAD, and one asbestos unit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fineFilterBlocksRadonAndTakesExactWear(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.tickCount = 200;
        ItemStack helmet = new ItemStack(ModItems.HAZMAT_HELMET.get());
        HazmatArmorItem.installFilter(helmet, new ItemStack(ModItems.GAS_MASK_FILTER.get()));
        player.setItemSlot(EquipmentSlot.HEAD, helmet);

        RadonGasBlock gas = (RadonGasBlock) ModBlocks.legacy("gas_radon").get();
        gas.defaultBlockState().entityInside(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), player);
        var data = RadiationSystem.data(player);
        check(helper, data.radiation() == 0.0F && data.radEnv() == 0.0F && data.asbestos() == 0,
                "Fine-particle protection must block every Radon collision dose");
        check(helper, HazmatArmorItem.installedFilter(helmet).getDamageValue() == 1,
                "Each protected Radon collision must wear the filter by exactly one");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void radonGasPreservesSourcePhysicalTraits(GameTestHelper helper) {
        var gas = ModBlocks.legacy("gas_radon").get().defaultBlockState();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        check(helper, gas.getRenderShape() == RenderShape.INVISIBLE
                        && gas.getShape(helper.getLevel(), pos).isEmpty()
                        && gas.getCollisionShape(helper.getLevel(), pos).isEmpty(),
                "Radon Gas must remain invisible, shapeless, and non-colliding");
        check(helper, ModBlocks.legacy("gas_radon").get().getExplosionResistance() == 0.0F,
                "Radon Gas must preserve zero source resistance");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
