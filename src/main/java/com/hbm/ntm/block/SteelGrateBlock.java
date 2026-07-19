package com.hbm.ntm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Source Steel Grate standard placement levels, each one eighth of a block thick. */
public final class SteelGrateBlock extends Block {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 7);
    private static final VoxelShape[] SHAPES = new VoxelShape[8];

    static {
        for (int level = 0; level < SHAPES.length; level++) {
            double bottom = level * 2D;
            SHAPES[level] = box(0D, bottom, 0D, 16D, bottom + 2D, 16D);
        }
    }

    public SteelGrateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 0));
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        int level = face == Direction.DOWN ? 7 : face == Direction.UP ? 0
                : Math.max(0, Math.min(7, (int) Math.floor(context.getClickLocation().y * 8D)
                - context.getClickedPos().getY() * 8));
        return defaultBlockState().setValue(LEVEL, level);
    }

    /** Maps an ordinary placement to its grate orientation. */
    public static int placementLevel(Direction face, double localHitY) {
        if (face == Direction.DOWN) return 7;
        if (face == Direction.UP) return 0;
        return Math.max(0, Math.min(7, (int) Math.floor(localHitY * 8D)));
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position,
                                             CollisionContext context) {
        return SHAPES[state.getValue(LEVEL)];
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos position,
                                                      CollisionContext context) {
        return getShape(state, level, position, context);
    }

    @Override protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos position) {
        return true;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }
}
