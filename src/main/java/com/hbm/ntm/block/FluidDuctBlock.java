package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.FluidDuctBlockEntity;
import com.hbm.ntm.item.FluidDuctItem;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Six-way typed fluid duct with nowhere to keep a drink. */
public final class FluidDuctBlock extends BaseEntityBlock {
    public static final MapCodec<FluidDuctBlock> CODEC = simpleCodec(FluidDuctBlock::new);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final EnumProperty<FluidIdentifierItem.Selection> TYPE =
            EnumProperty.create("fluid", FluidIdentifierItem.Selection.class);
    public static final EnumProperty<DuctRenderShape> RENDER_SHAPE =
            EnumProperty.create("render_shape", DuctRenderShape.class);
    private static final Map<Direction, BooleanProperty> PROPERTY = new EnumMap<>(Direction.class);
    private static final Map<Integer, VoxelShape> SHAPES = new HashMap<>();
    private static final VoxelShape CORE = box(5, 5, 5, 11, 11, 11);

    static {
        PROPERTY.put(Direction.NORTH, NORTH);
        PROPERTY.put(Direction.EAST, EAST);
        PROPERTY.put(Direction.SOUTH, SOUTH);
        PROPERTY.put(Direction.WEST, WEST);
        PROPERTY.put(Direction.UP, UP);
        PROPERTY.put(Direction.DOWN, DOWN);
    }

    public FluidDuctBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, true).setValue(EAST, true)
                .setValue(SOUTH, true).setValue(WEST, true).setValue(UP, true).setValue(DOWN, true)
                .setValue(TYPE, FluidIdentifierItem.Selection.NONE)
                .setValue(RENDER_SHAPE, DuctRenderShape.ISOLATED));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected SoundType getSoundType(BlockState state) {
        return new SoundType(0.85F, 0.85F, SoundType.METAL.getBreakSound(), SoundType.METAL.getStepSound(),
                ModSounds.PIPE_PLACED.get(), SoundType.METAL.getHitSound(), SoundType.METAL.getFallSound());
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                             CollisionContext context) {
        int mask = mask(state);
        return SHAPES.computeIfAbsent(mask, FluidDuctBlock::shapeForMask);
    }

    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        FluidIdentifierItem.Selection type = context.getItemInHand().getItem() instanceof FluidDuctItem
                ? FluidDuctItem.selection(context.getItemInHand()) : FluidIdentifierItem.Selection.NONE;
        return connections(context.getLevel(), context.getClickedPos(), defaultBlockState().setValue(TYPE, type));
    }

    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                               LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return connections(level, pos, state);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                                      ItemStack stack) {
        level.setBlock(pos, connections(level, pos, state), Block.UPDATE_ALL);
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                        BlockPos pos, Player player, InteractionHand hand,
                                                        BlockHitResult hit) {
        if (!(stack.getItem() instanceof FluidIdentifierItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        FluidIdentifierItem.Selection next = FluidIdentifierItem.primary(stack);
        if (next == FluidIdentifierItem.Selection.NONE) return ItemInteractionResult.FAIL;
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) retagConnected(level, pos, state.getValue(TYPE), next, 64);
            else setType(level, pos, state, next);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return FluidDuctItem.create(ModItems.FLUID_DUCT.get(), state.getValue(TYPE), 1);
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidDuctBlockEntity(pos, state);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, TYPE, RENDER_SHAPE);
    }

    private BlockState connections(BlockGetter level, BlockPos pos, BlockState state) {
        FluidIdentifierItem.Selection type = state.getValue(TYPE);
        boolean[] actual = new boolean[6];
        int count = 0;
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighbor = level.getBlockState(neighborPos);
            boolean connected;
            if (neighbor.getBlock() instanceof FluidDuctBlock) {
                connected = neighbor.getValue(TYPE) == type;
            } else {
                connected = level instanceof Level world
                        && world.getCapability(Capabilities.FluidHandler.BLOCK,
                        neighborPos, direction.getOpposite()) != null;
            }
            actual[direction.ordinal()] = connected;
            if (connected) count++;
        }
        if (count == 0) {
            for (Direction direction : Direction.values()) state = state.setValue(PROPERTY.get(direction), true);
            state = state.setValue(RENDER_SHAPE, DuctRenderShape.ISOLATED);
        } else {
            for (Direction direction : Direction.values()) {
                boolean connected = actual[direction.ordinal()];
                if (count == 1 && actual[direction.getOpposite().ordinal()]) connected = true;
                state = state.setValue(PROPERTY.get(direction), connected);
            }
            if (count == 1) {
                DuctRenderShape shape = actual[Direction.EAST.ordinal()] || actual[Direction.WEST.ordinal()]
                        ? DuctRenderShape.X
                        : actual[Direction.UP.ordinal()] || actual[Direction.DOWN.ordinal()]
                        ? DuctRenderShape.Y : DuctRenderShape.Z;
                state = state.setValue(RENDER_SHAPE, shape);
            } else {
                state = state.setValue(RENDER_SHAPE, DuctRenderShape.JUNCTION);
            }
        }
        return state;
    }

    private void setType(Level level, BlockPos pos, BlockState state, FluidIdentifierItem.Selection type) {
        if (state.getValue(TYPE) == type) return;
        level.setBlock(pos, connections(level, pos, state.setValue(TYPE, type)), Block.UPDATE_ALL);
        for (Direction direction : Direction.values()) level.updateNeighborsAt(pos.relative(direction), this);
    }

    private void retagConnected(Level level, BlockPos origin, FluidIdentifierItem.Selection oldType,
                                FluidIdentifierItem.Selection newType, int depthLimit) {
        record Visit(BlockPos pos, int depth) { }
        ArrayDeque<Visit> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(new Visit(origin, 0));
        visited.add(origin);
        while (!queue.isEmpty()) {
            Visit visit = queue.removeFirst();
            BlockState state = level.getBlockState(visit.pos());
            if (!(state.getBlock() instanceof FluidDuctBlock) || state.getValue(TYPE) != oldType) continue;
            setType(level, visit.pos(), state, newType);
            if (visit.depth() >= depthLimit) continue;
            for (Direction direction : Direction.values()) {
                BlockPos next = visit.pos().relative(direction);
                if (visited.add(next)) queue.addLast(new Visit(next, visit.depth() + 1));
            }
        }
    }

    private static int mask(BlockState state) {
        int mask = 0;
        for (Direction direction : Direction.values()) if (state.getValue(PROPERTY.get(direction))) mask |= 1 << direction.ordinal();
        return mask;
    }

    private static VoxelShape shapeForMask(int mask) {
        VoxelShape shape = CORE;
        if ((mask & 1 << Direction.NORTH.ordinal()) != 0) shape = Shapes.joinUnoptimized(shape, box(5, 5, 0, 11, 11, 5), BooleanOp.OR);
        if ((mask & 1 << Direction.SOUTH.ordinal()) != 0) shape = Shapes.joinUnoptimized(shape, box(5, 5, 11, 11, 11, 16), BooleanOp.OR);
        if ((mask & 1 << Direction.WEST.ordinal()) != 0) shape = Shapes.joinUnoptimized(shape, box(0, 5, 5, 5, 11, 11), BooleanOp.OR);
        if ((mask & 1 << Direction.EAST.ordinal()) != 0) shape = Shapes.joinUnoptimized(shape, box(11, 5, 5, 16, 11, 11), BooleanOp.OR);
        if ((mask & 1 << Direction.DOWN.ordinal()) != 0) shape = Shapes.joinUnoptimized(shape, box(5, 0, 5, 11, 5, 11), BooleanOp.OR);
        if ((mask & 1 << Direction.UP.ordinal()) != 0) shape = Shapes.joinUnoptimized(shape, box(5, 11, 5, 11, 16, 11), BooleanOp.OR);
        return shape.optimize();
    }

    public enum DuctRenderShape implements StringRepresentable {
        ISOLATED("isolated"),
        JUNCTION("junction"),
        X("x"),
        Y("y"),
        Z("z");

        private final String serializedName;

        DuctRenderShape(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override public String getSerializedName() {
            return serializedName;
        }
    }
}
