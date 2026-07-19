package com.hbm.ntm.block;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.BreedingReactorBlockEntity;
import com.hbm.ntm.blockentity.BreedingReactorProxyBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** One breeding reactor occupying three blocks and several regulations. */
public final class BreedingReactorBlock extends BaseEntityBlock {
    public static final ResourceLocation ID = id("machine_reactor_breeding");
    public static final ResourceLocation CORE_BE_ID = id("machine_reactor_breeding");
    public static final ResourceLocation PROXY_BE_ID = id("machine_reactor_breeding_proxy");
    public static final MapCodec<BreedingReactorBlock> CODEC = simpleCodec(BreedingReactorBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    private static final VoxelShape FULL = box(0, 0, 0, 16, 16, 16);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);

    public BreedingReactorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.LOWER));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        if (!context.getLevel().getBlockState(pos.above()).canBeReplaced(context)
                || !context.getLevel().getBlockState(pos.above(2)).canBeReplaced(context)) return null;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        level.setBlock(pos.above(), state.setValue(PART, Part.MIDDLE), Block.UPDATE_ALL);
        level.setBlock(pos.above(2), state.setValue(PART, Part.UPPER), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof BreedingReactorBlockEntity reactor
                && stack.has(DataComponents.CUSTOM_NAME)) reactor.setCustomName(stack.getHoverName());
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                             CollisionContext context) { return FULL; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        BlockPos core = corePosition(pos, state);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(core) instanceof BreedingReactorBlockEntity reactor) {
            serverPlayer.openMenu(reactor, buffer -> buffer.writeBlockPos(core));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (state.is(next.getBlock())) { super.onRemove(state, level, pos, next, moved); return; }
        BlockPos core = corePosition(pos, state);
        if (!level.isClientSide && level.getBlockEntity(core) instanceof BreedingReactorBlockEntity reactor) {
            Containers.dropContents(level, core, reactor);
        }
        if (!REMOVING.get()) {
            REMOVING.set(true);
            try {
                for (int y = 0; y < 3; y++) {
                    BlockPos part = core.above(y);
                    if (!part.equals(pos) && level.getBlockState(part).is(this)) {
                        level.setBlock(part, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            } finally { REMOVING.set(false); }
        }
        super.onRemove(state, level, pos, next, moved);
    }

    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS) != null) return List.of();
        return BuiltInRegistries.ITEM.getOptional(ID).map(item -> List.of(new ItemStack(item))).orElse(List.of());
    }
    @Override public boolean dropFromExplosion(Explosion explosion) { return false; }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.LOWER ? new BreedingReactorBlockEntity(pos, state)
                : new BreedingReactorProxyBlockEntity(pos, state);
    }

    @SuppressWarnings("unchecked")
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(PART) != Part.LOWER
                || !CORE_BE_ID.equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type))) return null;
        return (l, p, s, entity) -> BreedingReactorBlockEntity.tick(l, p, s, (BreedingReactorBlockEntity) entity);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    public static BlockPos corePosition(BlockPos pos, BlockState state) {
        return pos.below(state.getValue(PART).offset());
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path); }

    public enum Part implements StringRepresentable {
        LOWER("lower", 0), MIDDLE("middle", 1), UPPER("upper", 2);
        private final String name; private final int offset;
        Part(String name, int offset) { this.name = name; this.offset = offset; }
        @Override public String getSerializedName() { return name; }
        public int offset() { return offset; }
    }
}
