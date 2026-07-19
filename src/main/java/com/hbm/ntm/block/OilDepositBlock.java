package com.hbm.ntm.block;

import com.hbm.ntm.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Finite oil deposit that slumps downward into empty cells. */
public final class OilDepositBlock extends Block {
    public static final MapCodec<OilDepositBlock> CODEC = simpleCodec(OilDepositBlock::new);

    public OilDepositBlock(Properties properties) {
        super(properties);
    }

    @Override protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        settle(level, pos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        settle(level, pos);
    }

    private static void settle(Level level, BlockPos pos) {
        if (level.isClientSide || !level.getBlockState(pos.below()).is(ModBlocks.ORE_OIL_EMPTY.get())) return;
        level.setBlock(pos, ModBlocks.ORE_OIL_EMPTY.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos.below(), ModBlocks.ORE_OIL.get().defaultBlockState(), Block.UPDATE_ALL);
    }
}
