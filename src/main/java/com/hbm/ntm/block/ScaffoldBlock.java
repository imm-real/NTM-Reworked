package com.hbm.ntm.block;

import com.hbm.ntm.item.ScaffoldBlockItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Four scaffold variants, each willing to face four ways. */
public final class ScaffoldBlock extends Block {
    public static final EnumProperty<Variant> VARIANT = EnumProperty.create("variant", Variant.class);
    public static final IntegerProperty ORIENTATION = IntegerProperty.create("orientation", 0, 3);

    private static final VoxelShape VERTICAL_Z = box(0, 0, 2, 16, 16, 14);
    private static final VoxelShape HORIZONTAL = box(0, 2, 0, 16, 14, 16);
    private static final VoxelShape VERTICAL_X = box(2, 0, 0, 14, 16, 16);

    public ScaffoldBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(VARIANT, Variant.STEEL).setValue(ORIENTATION, 0));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(ORIENTATION,
                orientationFor(context.getClickedFace(), context.getHorizontalDirection()));
    }

    public static int orientationFor(Direction clickedFace, Direction playerHorizontalDirection) {
        if (clickedFace.getAxis() == Direction.Axis.Y) {
            return playerHorizontalDirection.getAxis() == Direction.Axis.Z ? 0 : 2;
        }
        return clickedFace.getAxis() == Direction.Axis.Z ? 1 : 3;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(ORIENTATION)) {
            case 0 -> VERTICAL_Z;
            case 2 -> VERTICAL_X;
            default -> HORIZONTAL;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return ScaffoldBlockItem.create(ModItems.STEEL_SCAFFOLD_ITEM.get(), state.getValue(VARIANT), 1);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VARIANT, ORIENTATION);
    }

    public enum Variant implements StringRepresentable {
        STEEL("steel", 0),
        RED("red", 1),
        WHITE("white", 2),
        YELLOW("yellow", 3);

        private final String id;
        private final int legacyMetadata;

        Variant(String id, int legacyMetadata) {
            this.id = id;
            this.legacyMetadata = legacyMetadata;
        }

        @Override public String getSerializedName() { return id; }
        public int legacyMetadata() { return legacyMetadata; }
    }
}
