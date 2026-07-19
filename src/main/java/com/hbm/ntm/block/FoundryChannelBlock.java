package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.FoundryChannelBlockEntity;
import com.hbm.ntm.item.FoundryScrapsItem;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/** Source open foundry channel with four-way visual and physical connections. */
public final class FoundryChannelBlock extends BaseEntityBlock {
    public static final MapCodec<FoundryChannelBlock> CODEC = simpleCodec(FoundryChannelBlock::new);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    private static final Map<Direction, BooleanProperty> PROPERTY = new EnumMap<>(Direction.class);
    private static final VoxelShape CORE = box(5, 0, 5, 11, 8, 11);

    static {
        PROPERTY.put(Direction.NORTH, NORTH);
        PROPERTY.put(Direction.EAST, EAST);
        PROPERTY.put(Direction.SOUTH, SOUTH);
        PROPERTY.put(Direction.WEST, WEST);
    }

    public FoundryChannelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false)
                .setValue(SOUTH, false).setValue(WEST, false));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                               LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!direction.getAxis().isHorizontal()) return state;
        return state.setValue(PROPERTY.get(direction), connects(level, pos, direction));
    }

    private static BlockState connections(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            state = state.setValue(PROPERTY.get(direction), connects(level, pos, direction));
        }
        return state;
    }

    private static boolean connects(BlockGetter level, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighbor = level.getBlockState(neighborPos);
        if (neighbor.is(ModBlocks.FOUNDRY_CHANNEL.get()) || neighbor.is(ModBlocks.FOUNDRY_MOLD.get())) return true;
        return (neighbor.is(ModBlocks.FOUNDRY_OUTLET.get()) || neighbor.is(ModBlocks.FOUNDRY_SLAGTAP.get()))
                && neighbor.getValue(FoundryOutletBlock.FACING) == direction;
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                             CollisionContext context) {
        VoxelShape shape = CORE;
        if (state.getValue(NORTH)) shape = Shapes.or(shape, box(5, 0, 0, 11, 8, 5));
        if (state.getValue(EAST)) shape = Shapes.or(shape, box(11, 0, 5, 16, 8, 11));
        if (state.getValue(SOUTH)) shape = Shapes.or(shape, box(5, 0, 11, 11, 8, 16));
        if (state.getValue(WEST)) shape = Shapes.or(shape, box(0, 0, 5, 5, 8, 11));
        return shape;
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                         Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ItemTags.SHOVELS)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FoundryChannelBlockEntity channel
                && channel.material() != null && channel.amount() > 0) {
            give(player, FoundryScrapsItem.create(ModItems.SCRAPS.get(), channel.material(), channel.amount()));
            channel.clearMolten();
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof FoundryChannelBlockEntity channel
                && channel.material() != null && channel.amount() > 0) {
            Containers.dropItemStack(level, pos.getX() + .5D, pos.getY() + .5D, pos.getZ() + .5D,
                    FoundryScrapsItem.create(ModItems.SCRAPS.get(), channel.material(), channel.amount()));
            channel.clearMolten();
        }
        super.onRemove(state, level, pos, next, moved);
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        player.inventoryMenu.broadcastChanges();
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FoundryChannelBlockEntity(pos, state);
    }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.FOUNDRY_CHANNEL.get(), FoundryChannelBlockEntity::tick);
    }
}
