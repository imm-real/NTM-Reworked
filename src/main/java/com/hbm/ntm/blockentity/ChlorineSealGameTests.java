package com.hbm.ntm.blockentity;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChlorineSealGameTests {
    private ChlorineSealGameTests() { }

    @GameTest(template = "empty")
    public static void sealUsesExactSourceWalkLimitAndDirectionOrder(GameTestHelper helper) {
        check(helper, ChlorineSealBlockEntity.MAX_SPREAD_INDEX == 50,
                "The powered Chlorine Seal must stop only after source spread index fifty");
        Direction[] expected = {Direction.EAST, Direction.WEST, Direction.UP,
                Direction.DOWN, Direction.SOUTH, Direction.NORTH};
        for (int roll = 0; roll < expected.length; roll++) {
            check(helper, ChlorineSealBlockEntity.sourceDirection(roll) == expected[roll],
                    "The Chlorine Seal random direction roll must preserve the source switch order");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sealCreatesItsDedicatedBlockEntity(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 2, 2);
        helper.setBlock(relative, ModBlocks.VENT_CHLORINE_SEAL.get());
        check(helper, helper.getBlockEntity(relative) instanceof ChlorineSealBlockEntity,
                "The Chlorine Seal must host its source-equivalent ticking block entity");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
