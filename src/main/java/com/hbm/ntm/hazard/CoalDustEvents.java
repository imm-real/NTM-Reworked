package com.hbm.ntm.hazard;

import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class CoalDustEvents {
    private CoalDustEvents() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CoalDustEvents::onBlockBreak);
    }

    private static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isCoalDustSource(event.getState())) return;
        BlockPos source = event.getPos();
        for (Direction direction : Direction.values()) {
            BlockPos target = source.relative(direction);
            if (spawnsFromRoll(level.random.nextInt(2), level.getBlockState(target).isAir())) {
                level.setBlock(target, ModBlocks.legacy("gas_coal").get().defaultBlockState(), 3);
            }
        }
    }

    static boolean isCoalDustSource(BlockState state) {
        return state.is(BlockTags.COAL_ORES) || state.is(Blocks.COAL_BLOCK)
                || state.is(ModBlocks.legacy("ore_lignite").get());
    }

    static boolean spawnsFromRoll(int roll, boolean air) {
        return roll == 0 && air;
    }
}
