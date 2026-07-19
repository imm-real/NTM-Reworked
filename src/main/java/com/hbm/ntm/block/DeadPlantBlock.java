package com.hbm.ntm.block;

import com.hbm.ntm.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Five dead plants for making oil fields look welcoming. */
public final class DeadPlantBlock extends BushBlock {
    public static final MapCodec<DeadPlantBlock> CODEC = simpleCodec(DeadPlantBlock::new);
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 4);

    public DeadPlantBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(VARIANT, 0));
    }

    @Override protected MapCodec<? extends BushBlock> codec() { return CODEC; }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                || state.is(net.minecraft.world.level.block.Blocks.DIRT)
                || state.is(ModBlocks.DIRT_OILY.get())
                || state.is(ModBlocks.DIRT_DEAD.get());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VARIANT);
    }
}
