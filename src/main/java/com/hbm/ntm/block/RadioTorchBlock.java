package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.RadioTorchBlockEntity;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.ror.RorInteractive;
import com.hbm.ntm.ror.RorValueProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class RadioTorchBlock extends BaseEntityBlock {
    public enum Kind { SENDER, RECEIVER, COUNTER, LOGIC, READER, CONTROLLER }

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private final Kind kind;
    private final MapCodec<RadioTorchBlock> codec;

    public RadioTorchBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        this.codec = MapCodec.unit(this);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(LIT, false));
    }

    public Kind kind() { return kind; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return codec; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public PushReaction getPistonPushReaction(BlockState state) { return PushReaction.DESTROY; }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        BlockState supportState = level.getBlockState(support);
        BlockEntity attached = level.getBlockEntity(support);
        if (kind == Kind.READER) return attached instanceof RorValueProvider;
        if (kind == Kind.CONTROLLER) return attached instanceof RorInteractive;
        if (kind == Kind.COUNTER && attached instanceof Container) return true;
        return supportState.isFaceSturdy(level, support, facing)
                || supportState.hasAnalogOutputSignal() || supportState.isSignalSource();
    }

    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                                net.minecraft.world.level.LevelAccessor level, BlockPos pos,
                                                BlockPos neighborPos) {
        return direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)
                ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                             CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case UP -> box(6, 0, 6, 10, 10, 10);
            case DOWN -> box(6, 6, 6, 10, 16, 10);
            case NORTH -> box(6, 6, 6, 10, 10, 16);
            case SOUTH -> box(6, 6, 0, 10, 10, 10);
            case WEST -> box(6, 6, 6, 16, 10, 10);
            case EAST -> box(0, 6, 6, 10, 10, 10);
        };
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                      CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof RadioTorchBlockEntity radio) {
            serverPlayer.openMenu(radio, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected boolean isSignalSource(BlockState state) {
        return kind == Kind.RECEIVER || kind == Kind.LOGIC;
    }

    @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof RadioTorchBlockEntity radio ? radio.output() : 0;
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadioTorchBlockEntity(pos, state);
    }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.RADIO_TORCH.get(), RadioTorchBlockEntity::tick);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }
}
