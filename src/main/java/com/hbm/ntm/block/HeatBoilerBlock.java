package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.HeatBoilerBlockEntity;
import com.hbm.ntm.blockentity.HeatBoilerProxyBlockEntity;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.UniversalFluidTankItem;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Source 3x4x3 heat boiler with a bottom-center core and three fluid ports. */
public final class HeatBoilerBlock extends BaseEntityBlock {
    public static final MapCodec<HeatBoilerBlock> CODEC = simpleCodec(HeatBoilerBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty X = IntegerProperty.create("part_x", 0, 2);
    public static final IntegerProperty Y = IntegerProperty.create("part_y", 0, 3);
    public static final IntegerProperty Z = IntegerProperty.create("part_z", 0, 2);
    public static final BooleanProperty EXPLODED = BooleanProperty.create("exploded");
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FULL = Shapes.block();

    public HeatBoilerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(X, 1).setValue(Y, 0).setValue(Z, 1).setValue(EXPLODED, false));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos clicked = context.getClickedPos();
        BlockPos core = clicked.relative(facing.getOpposite());
        for (BlockPos part : partPositions(core)) {
            if (!part.equals(clicked) && !context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return stateForPart(clicked, core, facing, false);
    }

    @Override public void setPlacedBy(Level level, BlockPos position, BlockState state,
                                      LivingEntity placer, ItemStack stack) {
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        for (BlockPos part : partPositions(core)) {
            level.setBlock(part, stateForPart(part, core, facing, false), Block.UPDATE_ALL);
        }
        if (stack.has(DataComponents.CUSTOM_NAME)
                && level.getBlockEntity(core) instanceof HeatBoilerBlockEntity boiler) {
            boiler.setCustomName(stack.getHoverName());
        }
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                             CollisionContext context) { return FULL; }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                         BlockPos position, Player player, InteractionHand hand,
                                                         BlockHitResult hit) {
        BlockPos core = corePosition(position, state);
        if (!(level.getBlockEntity(core) instanceof HeatBoilerBlockEntity boiler) || boiler.hasExploded()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!player.isShiftKeyDown() && stack.getItem() instanceof FluidIdentifierItem) {
            FluidIdentifierItem.Selection selection = FluidIdentifierItem.primary(stack);
            if (!level.isClientSide && boiler.configureInput(selection)) {
                player.displayClientMessage(Component.literal("Changed type to ")
                        .withStyle(ChatFormatting.YELLOW)
                        .append(Component.translatable(selection.translationKey()))
                        .append("!"), false);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        boolean waterContainer = stack.is(Items.WATER_BUCKET)
                || stack.getItem() instanceof UniversalFluidTankItem
                && UniversalFluidTankItem.fluid(stack) == UniversalFluidTankItem.ContainedFluid.WATER;
        if (!waterContainer || boiler.inputTank().fill(new FluidStack(Fluids.WATER, 1_000),
                IFluidHandler.FluidAction.SIMULATE) != 1_000) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            boiler.fillWater(1_000);
            if (!player.getAbilities().instabuild) {
                if (stack.is(Items.WATER_BUCKET)) {
                    player.setItemInHand(hand, new ItemStack(Items.BUCKET));
                } else {
                    stack.shrink(1);
                    ItemStack empty = new ItemStack(ModItems.FLUID_TANK_EMPTY.get());
                    if (!player.getInventory().add(empty)) player.drop(empty, false);
                }
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position,
                                                          Player player, BlockHitResult hit) {
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos position,
                                      BlockState newState, boolean moved) {
        if (state.is(newState.getBlock()) || REMOVING.get()) {
            super.onRemove(state, level, position, newState, moved);
            return;
        }
        BlockPos core = corePosition(position, state);
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
        super.onRemove(state, level, position, newState, moved);
    }

    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (state.getValue(EXPLODED)) {
            return List.of(new ItemStack(ModItems.get("ingot_steel").get(), 4),
                    new ItemStack(ModItems.get("plate_copper").get(), 8));
        }
        return super.getDrops(state, params);
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (isCore(state)) return new HeatBoilerBlockEntity(pos, state);
        return isPort(state) ? new HeatBoilerProxyBlockEntity(pos, state) : null;
    }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.MACHINE_BOILER.get(),
                HeatBoilerBlockEntity::tick) : null;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, X, Y, Z, EXPLODED);
    }

    public void burst(Level level, BlockPos core) {
        BlockState coreState = level.getBlockState(core);
        if (!coreState.is(this)) return;
        Direction facing = coreState.getValue(FACING);
        REMOVING.set(true);
        try {
            for (BlockPos part : partPositions(core)) {
                int dy = part.getY() - core.getY();
                boolean remove = dy >= 2 || dy == 1 && part.getX() == core.getX() && part.getZ() == core.getZ();
                if (remove) {
                    if (level.getBlockState(part).is(this)) level.setBlock(part,
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                } else if (level.getBlockState(part).is(this)) {
                    level.setBlock(part, stateForPart(part, core, facing, true), Block.UPDATE_CLIENTS);
                }
            }
        } finally {
            REMOVING.set(false);
        }
    }

    public static boolean isCore(BlockState state) {
        return state.getValue(X) == 1 && state.getValue(Y) == 0 && state.getValue(Z) == 1;
    }

    public static boolean isPort(BlockState state) {
        int x = state.getValue(X), y = state.getValue(Y), z = state.getValue(Z);
        if (y == 3 && x == 1 && z == 1) return true;
        if (y != 0) return false;
        Direction facing = state.getValue(FACING);
        return facing.getAxis() == Direction.Axis.Z ? z == 1 && (x == 0 || x == 2)
                : x == 1 && (z == 0 || z == 2);
    }

    @Nullable public static Direction portSide(BlockState state) {
        if (!isPort(state)) return null;
        if (state.getValue(Y) == 3) return Direction.UP;
        if (state.getValue(X) == 0) return Direction.WEST;
        if (state.getValue(X) == 2) return Direction.EAST;
        if (state.getValue(Z) == 0) return Direction.NORTH;
        return Direction.SOUTH;
    }

    public static BlockPos corePosition(BlockPos position, BlockState state) {
        return position.offset(1 - state.getValue(X), -state.getValue(Y), 1 - state.getValue(Z));
    }

    public static List<BlockPos> partPositions(BlockPos core) {
        List<BlockPos> positions = new ArrayList<>(36);
        for (int y = 0; y <= 3; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            positions.add(core.offset(x, y, z));
        }
        return positions;
    }

    public BlockState stateForPart(BlockPos part, BlockPos core, Direction facing, boolean exploded) {
        return defaultBlockState().setValue(FACING, facing)
                .setValue(X, part.getX() - core.getX() + 1)
                .setValue(Y, part.getY() - core.getY())
                .setValue(Z, part.getZ() - core.getZ() + 1)
                .setValue(EXPLODED, exploded);
    }
}
