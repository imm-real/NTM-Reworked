package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.ChargeBlockEntity;
import com.hbm.ntm.entity.PrimedExplosiveEntity;
import com.hbm.ntm.explosion.ChargeExplosion;
import com.hbm.ntm.explosion.DetonationResult;
import com.hbm.ntm.explosion.RemoteDetonatable;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.registry.ModSounds;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class ChargeBlock extends BaseEntityBlock implements EntityBlock, PrimedExplosiveBlock, RemoteDetonatable {
    public static final MapCodec<ChargeBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            ChargeType.CODEC.fieldOf("charge_type").forGetter(ChargeBlock::chargeType)
    ).apply(instance, ChargeBlock::new));
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final VoxelShape DOWN = Block.box(0, 10, 0, 16, 16, 16);
    private static final VoxelShape UP = Block.box(0, 0, 0, 16, 6, 16);
    private static final VoxelShape NORTH = Block.box(0, 0, 10, 16, 16, 16);
    private static final VoxelShape SOUTH = Block.box(0, 0, 0, 16, 16, 6);
    private static final VoxelShape WEST = Block.box(10, 0, 0, 16, 16, 16);
    private static final VoxelShape EAST = Block.box(0, 0, 0, 6, 16, 16);

    private final ChargeType chargeType;

    public ChargeBlock(Properties properties, ChargeType chargeType) {
        super(properties);
        this.chargeType = chargeType;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    public ChargeType chargeType() {
        return chargeType;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case DOWN -> DOWN;
            case UP -> UP;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof ChargeBlockEntity charge) || charge.started()) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            if (charge.arm()) {
                level.playSound(null, pos, ModSounds.CHARGE_START.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        } else {
            charge.cycleTimer();
            level.playSound(null, pos, ModSounds.TECH_BOOP.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.CONSUME;
    }

    public boolean useDefuser(ServerLevel level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof ChargeBlockEntity charge)) {
            return false;
        }
        if (charge.pause()) {
            level.playSound(null, pos, ModSounds.CHARGE_START.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            return true;
        }

        charge.setSafeRemoval(true);
        level.removeBlock(pos, false);
        popResource(level, pos, new net.minecraft.world.item.ItemStack(asItem()));
        return true;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            boolean safe = blockEntity instanceof ChargeBlockEntity charge && charge.safeRemoval();
            if (!safe) {
                ChargeExplosion.detonate((ServerLevel) level, pos, chargeType);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public void detonate(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ChargeBlockEntity charge) {
            charge.setSafeRemoval(true);
        }
        level.removeBlock(pos, false);
        ChargeExplosion.detonate(level, pos, chargeType);
    }

    @Override
    public DetonationResult detonateRemotely(ServerLevel level, BlockPos position) {
        detonate(level, position);
        return DetonationResult.DETONATED;
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        if (!level.isClientSide) {
            PrimedExplosiveEntity primed = new PrimedExplosiveEntity(
                    level,
                    pos.getX() + 0.5D,
                    pos.getY() + 0.5D,
                    pos.getZ() + 0.5D,
                    explosion.getIndirectSourceEntity() instanceof LivingEntity living ? living : null,
                    defaultBlockState(),
                    0
            );
            level.addFreshEntity(primed);
        }
    }

    @Override
    public void detonatePrimed(ServerLevel level, double x, double y, double z, PrimedExplosiveEntity entity) {
        detonate(level, BlockPos.containing(x, y, z));
    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChargeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type, com.hbm.ntm.registry.ModBlockEntities.CHARGE.get(), ChargeBlockEntity::tick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
