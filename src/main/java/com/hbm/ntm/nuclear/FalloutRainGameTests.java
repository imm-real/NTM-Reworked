package com.hbm.ntm.nuclear;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FalloutRainGameTests {
    private FalloutRainGameTests() { }

    @GameTest(template = "empty")
    public static void innerFalloutConvertsThreeSolidLayersToSlakedSellafite(GameTestHelper helper) {
        BlockPos column = new BlockPos(2, 5, 2);
        helper.setBlock(column, Blocks.DIRT);
        helper.setBlock(column.below(), Blocks.STONE);
        helper.setBlock(column.below(2), Blocks.DEEPSLATE);
        helper.setBlock(column.below(3), Blocks.STONE);

        int converted = FalloutRainEntity.convertColumnToSlakedSellafite(
                helper.getLevel(), helper.absolutePos(column));

        check(helper, converted == 3, "Fallout must convert exactly the first three source-solid layers");
        check(helper, helper.getBlockState(column).is(ModBlocks.SELLAFIELD_SLAKED.get()),
                "Surface dirt must become Slaked Sellafite");
        check(helper, helper.getBlockState(column.below()).is(ModBlocks.SELLAFIELD_SLAKED.get()),
                "First subsurface stone must become Slaked Sellafite");
        check(helper, helper.getBlockState(column.below(2)).is(ModBlocks.SELLAFIELD_SLAKED.get()),
                "Second subsurface stone must become Slaked Sellafite");
        check(helper, helper.getBlockState(column.below(3)).is(Blocks.STONE),
                "Fallout must stop after three solid layers");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sellafiteConversionLeavesLiquidsAndUnbreakableBlocksAlone(GameTestHelper helper) {
        BlockPos column = new BlockPos(2, 5, 2);
        helper.setBlock(column, Blocks.WATER);
        helper.setBlock(column.below(), Blocks.BEDROCK);
        helper.setBlock(column.below(2), Blocks.BEDROCK);
        helper.setBlock(column.below(3), Blocks.BEDROCK);
        helper.setBlock(column.below(4), Blocks.STONE);

        int converted = FalloutRainEntity.convertColumnToSlakedSellafite(
                helper.getLevel(), helper.absolutePos(column));

        check(helper, converted == 0, "Bedrock must consume the first solid depth without conversion");
        check(helper, helper.getBlockState(column).is(Blocks.WATER), "Fallout must not replace liquids");
        check(helper, helper.getBlockState(column.below()).is(Blocks.BEDROCK), "Fallout must preserve bedrock");
        check(helper, helper.getBlockState(column.below(4)).is(Blocks.STONE),
                "Terrain below a bedrock cap must remain untouched");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
