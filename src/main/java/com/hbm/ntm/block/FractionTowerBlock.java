package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.FractionTowerBlockEntity;
import com.hbm.ntm.blockentity.FractionTowerProxyBlockEntity;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Source three-by-three-by-three stackable Fractioning Tower segment. */
public final class FractionTowerBlock extends BaseEntityBlock {
    public static final MapCodec<FractionTowerBlock> CODEC = simpleCodec(FractionTowerBlock::new);
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, 2);
    public static final IntegerProperty PART_Z = IntegerProperty.create("part_z", 0, 2);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, 2);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FULL = box(0D, 0D, 0D, 16D, 16D, 16D);

    public FractionTowerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART_X, 1).setValue(PART_Z, 1)
                .setValue(PART_Y, 0));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos clicked = context.getClickedPos();
        BlockPos core = clicked.relative(context.getHorizontalDirection());
        for (BlockPos part : partPositions(core)) {
            if (!part.equals(clicked) && !context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return stateForPart(clicked, core);
    }

    @Override public void setPlacedBy(Level level, BlockPos position, BlockState state,
                                      LivingEntity placer, ItemStack stack) {
        BlockPos core = corePosition(position, state);
        for (BlockPos part : partPositions(core)) {
            level.setBlock(part, stateForPart(part, core), Block.UPDATE_ALL);
        }
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position,
                                             CollisionContext context) { return FULL; }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                         BlockPos position, Player player, InteractionHand hand,
                                                         BlockHitResult hit) {
        if (player.isShiftKeyDown() || !(stack.getItem() instanceof FluidIdentifierItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        BlockPos core = corePosition(position, state);
        if (!(level.getBlockEntity(core) instanceof FractionTowerBlockEntity tower)) {
            return ItemInteractionResult.FAIL;
        }
        if (level.getBlockEntity(core.below(3)) instanceof FractionTowerBlockEntity) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal(
                        "You can only change the type in the bottom segment!").withStyle(ChatFormatting.RED), false);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        FluidIdentifierItem.Selection selection = FluidIdentifierItem.primary(stack);
        if (!level.isClientSide) {
            tower.configureInput(selection);
            player.displayClientMessage(Component.literal("Changed type to ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable(selection.translationKey()))
                    .append("!"), false);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position,
                                                          Player player, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos position,
                                      BlockState newState, boolean moved) {
        if (state.is(newState.getBlock())) {
            super.onRemove(state, level, position, newState, moved);
            return;
        }
        BlockPos core = corePosition(position, state);
        if (!REMOVING.get()) {
            REMOVING.set(true);
            try {
                for (BlockPos part : partPositions(core)) {
                    if (!part.equals(position) && level.getBlockState(part).is(this)) {
                        level.setBlock(part, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            } finally {
                REMOVING.set(false);
            }
        }
        super.onRemove(state, level, position, newState, moved);
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        if (isCore(state)) return new FractionTowerBlockEntity(position, state);
        return isProxy(state) ? new FractionTowerProxyBlockEntity(position, state) : null;
    }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.MACHINE_FRACTION_TOWER.get(),
                FractionTowerBlockEntity::tick) : null;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART_X, PART_Z, PART_Y);
    }

    public static boolean isCore(BlockState state) {
        return state.getValue(PART_X) == 1 && state.getValue(PART_Z) == 1
                && state.getValue(PART_Y) == 0;
    }

    public static boolean isProxy(BlockState state) {
        if (state.getValue(PART_Y) != 0) return false;
        int x = state.getValue(PART_X);
        int z = state.getValue(PART_Z);
        return (x == 1) != (z == 1);
    }

    public static BlockPos corePosition(BlockPos position, BlockState state) {
        return position.offset(1 - state.getValue(PART_X), -state.getValue(PART_Y),
                1 - state.getValue(PART_Z));
    }

    public static List<BlockPos> partPositions(BlockPos core) {
        List<BlockPos> positions = new ArrayList<>(27);
        for (int y = 0; y <= 2; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) positions.add(core.offset(x, y, z));
            }
        }
        return positions;
    }

    public static List<Connection> connections(BlockPos core) {
        return List.of(new Connection(core.west(), Direction.WEST),
                new Connection(core.east(), Direction.EAST),
                new Connection(core.north(), Direction.NORTH),
                new Connection(core.south(), Direction.SOUTH));
    }

    public static boolean canConnectAt(BlockState state, @Nullable Direction side) {
        if (!isProxy(state) || side == null || !side.getAxis().isHorizontal()) return false;
        int x = state.getValue(PART_X);
        int z = state.getValue(PART_Z);
        return x == 0 && side == Direction.WEST || x == 2 && side == Direction.EAST
                || z == 0 && side == Direction.NORTH || z == 2 && side == Direction.SOUTH;
    }

    private BlockState stateForPart(BlockPos part, BlockPos core) {
        BlockPos delta = part.subtract(core);
        return defaultBlockState().setValue(PART_X, delta.getX() + 1)
                .setValue(PART_Z, delta.getZ() + 1).setValue(PART_Y, delta.getY());
    }

    public record Connection(BlockPos port, Direction outward) { }
}
