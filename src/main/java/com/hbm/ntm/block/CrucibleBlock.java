package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.CrucibleBlockEntity;
import com.hbm.ntm.blockentity.CrucibleProxyBlockEntity;
import com.hbm.ntm.foundry.FoundryMaterial;
import com.hbm.ntm.item.FoundryScrapsItem;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Containers;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class CrucibleBlock extends BaseEntityBlock {
    public static final MapCodec<CrucibleBlock> CODEC = simpleCodec(CrucibleBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty X = IntegerProperty.create("part_x", 0, 2);
    public static final IntegerProperty Y = IntegerProperty.create("part_y", 0, 1);
    public static final IntegerProperty Z = IntegerProperty.create("part_z", 0, 2);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape BOTTOM = box(0, 0, 0, 16, 8, 16);

    public CrucibleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(X, 1).setValue(Y, 0).setValue(Z, 1));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos clicked = context.getClickedPos();
        BlockPos core = clicked.relative(facing.getOpposite());
        for (BlockPos part : partPositions(core)) {
            if (!part.equals(clicked) && !context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return stateForPart(clicked, core, facing);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        BlockPos core = corePosition(pos, state);
        Direction facing = state.getValue(FACING);
        for (BlockPos part : partPositions(core)) level.setBlock(part, stateForPart(part, core, facing), Block.UPDATE_ALL);
        if (stack.has(DataComponents.CUSTOM_NAME) && level.getBlockEntity(core) instanceof CrucibleBlockEntity crucible) {
            crucible.setCustomName(stack.getHoverName());
        }
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(Y) == 0) return BOTTOM;
        int x = state.getValue(X);
        int z = state.getValue(Z);
        VoxelShape shape = Shapes.empty();
        if (z == 0) shape = Shapes.or(shape, box(x == 0 ? 4 : 0, 0, 4, x == 2 ? 12 : 16, 16, 8));
        if (z == 2) shape = Shapes.or(shape, box(x == 0 ? 4 : 0, 0, 8, x == 2 ? 12 : 16, 16, 12));
        if (x == 0) shape = Shapes.or(shape, box(4, 0, z == 0 ? 4 : 0, 8, 16, z == 2 ? 12 : 16));
        if (x == 2) shape = Shapes.or(shape, box(8, 0, z == 0 ? 4 : 0, 12, 16, z == 2 ? 12 : 16));
        return shape;
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                        Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ItemTags.SHOVELS)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        BlockPos core = corePosition(pos, state);
        if (!level.isClientSide && level.getBlockEntity(core) instanceof CrucibleBlockEntity crucible) {
            for (FoundryMaterial material : FoundryMaterial.values()) {
                give(player, material, crucible.recipeAmount(material));
                give(player, material, crucible.wasteAmount(material));
            }
            crucible.clearMolten();
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                         Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.sidedSuccess(level.isClientSide);
        BlockPos core = corePosition(pos, state);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(core) instanceof CrucibleBlockEntity crucible) {
            serverPlayer.openMenu(crucible, buffer -> buffer.writeBlockPos(core));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void give(Player player, FoundryMaterial material, int amount) {
        if (amount <= 0) return;
        ItemStack scraps = FoundryScrapsItem.create(ModItems.SCRAPS.get(), material, amount);
        if (!player.getInventory().add(scraps)) player.drop(scraps, false);
        player.inventoryMenu.broadcastChanges();
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (state.is(next.getBlock())) { super.onRemove(state, level, pos, next, moved); return; }
        BlockPos core = corePosition(pos, state);
        if (!REMOVING.get()) {
            REMOVING.set(true);
            try {
                if (!level.isClientSide && level.getBlockEntity(core) instanceof CrucibleBlockEntity crucible) {
                    Containers.dropContents(level, core, crucible);
                    for (FoundryMaterial material : FoundryMaterial.values()) {
                        dropScraps(level, core, material, crucible.recipeAmount(material));
                        dropScraps(level, core, material, crucible.wasteAmount(material));
                    }
                    crucible.clearContent();
                    crucible.clearMolten();
                }
                for (BlockPos part : partPositions(core)) if (!part.equals(pos) && level.getBlockState(part).is(this)) {
                    level.setBlock(part, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                }
            } finally { REMOVING.set(false); }
        }
        super.onRemove(state, level, pos, next, moved);
    }

    private static void dropScraps(Level level, BlockPos pos, FoundryMaterial material, int amount) {
        if (amount > 0) Containers.dropItemStack(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                FoundryScrapsItem.create(ModItems.SCRAPS.get(), material, amount));
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isCore(state) ? new CrucibleBlockEntity(pos, state) : new CrucibleProxyBlockEntity(pos, state);
    }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                                      BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.MACHINE_CRUCIBLE.get(), CrucibleBlockEntity::tick) : null;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, X, Y, Z);
    }

    public static boolean isCore(BlockState state) {
        return state.getValue(X) == 1 && state.getValue(Y) == 0 && state.getValue(Z) == 1;
    }

    public static BlockPos corePosition(BlockPos pos, BlockState state) {
        return pos.offset(1 - state.getValue(X), -state.getValue(Y), 1 - state.getValue(Z));
    }

    public static List<BlockPos> partPositions(BlockPos core) {
        List<BlockPos> result = new ArrayList<>(18);
        for (int y = 0; y <= 1; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            result.add(core.offset(x, y, z));
        }
        return result;
    }

    private static BlockState stateForPart(BlockPos part, BlockPos core, Direction facing) {
        return com.hbm.ntm.registry.ModBlocks.MACHINE_CRUCIBLE.get().defaultBlockState().setValue(FACING, facing)
                .setValue(X, part.getX() - core.getX() + 1).setValue(Y, part.getY() - core.getY())
                .setValue(Z, part.getZ() - core.getZ() + 1);
    }
}
