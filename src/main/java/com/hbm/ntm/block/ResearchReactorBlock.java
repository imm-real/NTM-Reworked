package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.ResearchReactorBlockEntity;
import com.hbm.ntm.blockentity.ResearchReactorProxyBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Research reactor core plus two blocks of personal space. */
public final class ResearchReactorBlock extends BaseEntityBlock {
    public static final MapCodec<ResearchReactorBlock> CODEC = simpleCodec(ResearchReactorBlock::new);
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);
    public static final ResourceLocation BLOCK_ID = ResourceLocation.fromNamespaceAndPath("hbm", "machine_reactor_small");
    public static final ResourceLocation BLOCK_ENTITY_ID = ResourceLocation.fromNamespaceAndPath("hbm", "reactor_research");
    public static final ResourceLocation PROXY_BLOCK_ENTITY_ID = ResourceLocation.fromNamespaceAndPath("hbm", "reactor_research_proxy");
    private static final VoxelShape FULL = box(0, 0, 0, 16, 16, 16);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);

    public ResearchReactorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, 0));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos core = context.getClickedPos();
        for (int y = 1; y <= 2; y++) {
            if (!context.getLevel().getBlockState(core.above(y)).canBeReplaced(context)) return null;
        }
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) return;
        for (int y = 0; y <= 2; y++) {
            level.setBlock(pos.above(y), defaultBlockState().setValue(PART, y), Block.UPDATE_ALL);
        }
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                             CollisionContext context) { return FULL; }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        BlockPos core = corePosition(pos, state);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(core) instanceof ResearchReactorBlockEntity reactor) {
            // FBI progression can return with the progression system.
            serverPlayer.openMenu(reactor, buffer -> buffer.writeBlockPos(core));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos,
                            RandomSource random) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!level.getFluidState(pos.relative(direction)).is(FluidTags.WATER)) continue;
            double x = pos.getX() + 0.5D + direction.getStepX() + random.nextDouble() - 0.5D;
            double y = pos.getY() + 0.5D + random.nextDouble() - 0.5D;
            double z = pos.getZ() + 0.5D + direction.getStepZ() + random.nextDouble() - 0.5D;
            if (direction.getStepX() != 0) {
                x = pos.getX() + 0.5D + direction.getStepX() * 0.5D
                        + random.nextDouble() * 0.125D * direction.getStepX();
            }
            if (direction.getStepZ() != 0) {
                z = pos.getZ() + 0.5D + direction.getStepZ() * 0.5D
                        + random.nextDouble() * 0.125D * direction.getStepZ();
            }
            level.addParticle(ParticleTypes.BUBBLE, x, y, z, 0.0D, 0.2D, 0.0D);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState next, boolean moved) {
        if (state.is(next.getBlock())) {
            super.onRemove(state, level, pos, next, moved);
            return;
        }
        BlockPos core = corePosition(pos, state);
        if (!REMOVING.get()) {
            REMOVING.set(true);
            try {
                if (level.getBlockEntity(core) instanceof ResearchReactorBlockEntity reactor) {
                    Containers.dropContents(level, core, reactor);
                }
                for (int y = 0; y <= 2; y++) {
                    BlockPos part = core.above(y);
                    if (!part.equals(pos) && level.getBlockState(part).is(this)) {
                        level.removeBlock(part, false);
                    }
                }
            } finally {
                REMOVING.set(false);
            }
        }
        super.onRemove(state, level, pos, next, moved);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS) != null) return List.of();
        return List.of(new ItemStack(BuiltInRegistries.ITEM.get(BLOCK_ID)));
    }

    @Override public boolean dropFromExplosion(Explosion explosion) { return false; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new ResearchReactorBlockEntity(pos, state)
                : new ResearchReactorProxyBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        BlockEntityType<?> expected = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(BLOCK_ENTITY_ID);
        if (state.getValue(PART) != 0 || type != expected) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<ResearchReactorBlockEntity>)
                ResearchReactorBlockEntity::tick;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    public static BlockPos corePosition(BlockPos pos, BlockState state) {
        return pos.below(state.getValue(PART));
    }
}
