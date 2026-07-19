package com.hbm.ntm.block;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SandbagsGameTests {
    private SandbagsGameTests() { }

    @GameTest(template = "empty")
    public static void sandbagsRecipeAndPhysicalConstantsMatchSource(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager().byKey(
                ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "sandbags")).orElseThrow();
        var result = recipe.value().getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModItems.SANDBAGS_ITEM.get()) && result.getCount() == 4,
                "One Insulator and three sand ingredients must produce four Sandbags");
        helper.assertTrue(ModBlocks.SANDBAGS.get().getExplosionResistance() == 18.0F,
                "Sandbags must preserve effective source resistance 18");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sandbagsConnectToFullBlocksAndOtherSandbags(GameTestHelper helper) {
        BlockPos center = new BlockPos(3, 2, 3);
        helper.setBlock(center, ModBlocks.SANDBAGS.get());
        AABB isolated = bounds(helper, center);
        helper.assertTrue(isolated.minX == 0.25D && isolated.maxX == 0.75D
                        && isolated.minZ == 0.25D && isolated.maxZ == 0.75D,
                "Isolated Sandbags must retain the source 0.25..0.75 horizontal bounds");

        helper.setBlock(center.west(), Blocks.STONE);
        helper.setBlock(center.east(), ModBlocks.SANDBAGS.get());
        AABB connected = bounds(helper, center);
        helper.assertTrue(connected.minX == 0.0D && connected.maxX == 1.0D,
                "Sandbags must extend to opaque blocks and neighboring Sandbags");
        helper.assertTrue(connected.minY == 0.0D && connected.maxY == 1.0D,
                "Sandbags must remain full block height");
        helper.succeed();
    }

    private static AABB bounds(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        var state = helper.getBlockState(relative);
        return state.getShape(helper.getLevel(), absolute).bounds();
    }
}
