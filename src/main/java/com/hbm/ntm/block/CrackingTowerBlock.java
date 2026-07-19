package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.CrackingTowerBlockEntity;
import com.hbm.ntm.blockentity.CrackingTowerProxyBlockEntity;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Catalytic cracking tower spread across 368 inconvenient cells. */
public final class CrackingTowerBlock extends BaseEntityBlock {
    public static final MapCodec<CrackingTowerBlock> CODEC = simpleCodec(CrackingTowerBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final List<PartOffset> PARTS = buildParts();
    private static final Map<PartOffset, Integer> PART_INDICES = buildPartIndices();
    private static final PartOffset CORE = new PartOffset(0, 0, 0);
    private static final Set<PartOffset> PORTS = Set.of(
            new PartOffset(1, 0, 3), new PartOffset(-2, 0, 3),
            new PartOffset(1, 0, -3), new PartOffset(-2, 0, -3),
            new PartOffset(2, 0, 2), new PartOffset(-3, 0, 2),
            new PartOffset(2, 0, -2), new PartOffset(-3, 0, -2));
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, PARTS.size() - 1);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FULL = box(0D, 0D, 0D, 16D, 16D, 16D);

    public CrackingTowerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(PART, PART_INDICES.get(CORE)));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos clicked = context.getClickedPos();
        BlockPos core = clicked.relative(facing.getOpposite(), 3);
        for (BlockPos part : partPositions(core, facing)) {
            if (!part.equals(clicked) && !context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return stateForPart(clicked, core, facing);
    }

    @Override public void setPlacedBy(Level level, BlockPos position, BlockState state,
                                      LivingEntity placer, ItemStack stack) {
        BlockPos core = corePosition(position, state);
        Direction facing = state.getValue(FACING);
        for (BlockPos part : partPositions(core, facing)) {
            level.setBlock(part, stateForPart(part, core, facing), Block.UPDATE_ALL);
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
        if (!(level.getBlockEntity(core) instanceof CrackingTowerBlockEntity tower)) {
            return ItemInteractionResult.FAIL;
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
        Direction facing = state.getValue(FACING);
        if (!REMOVING.get()) {
            REMOVING.set(true);
            try {
                for (BlockPos part : partPositions(core, facing)) {
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
        if (isCore(state)) return new CrackingTowerBlockEntity(position, state);
        return isProxy(state) ? new CrackingTowerProxyBlockEntity(position, state) : null;
    }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return isCore(state) ? createTickerHelper(type, ModBlockEntities.MACHINE_CATALYTIC_CRACKER.get(),
                CrackingTowerBlockEntity::tick) : null;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    public static boolean isCore(BlockState state) { return offset(state).equals(CORE); }
    public static boolean isProxy(BlockState state) { return PORTS.contains(offset(state)); }
    public static int partCount() { return PARTS.size(); }

    public static BlockPos corePosition(BlockPos position, BlockState state) {
        PartOffset offset = offset(state);
        Direction facing = state.getValue(FACING);
        Direction side = facing.getClockWise();
        return position.relative(side, -offset.x()).relative(facing, -offset.z()).below(offset.y());
    }

    public static List<BlockPos> partPositions(BlockPos core, Direction facing) {
        Direction side = facing.getClockWise();
        List<BlockPos> positions = new ArrayList<>(PARTS.size());
        for (PartOffset offset : PARTS) {
            positions.add(core.relative(side, offset.x()).above(offset.y()).relative(facing, offset.z()));
        }
        return positions;
    }

    public static List<Connection> connections(BlockPos core, Direction facing) {
        Direction side = facing.getClockWise();
        List<Connection> connections = new ArrayList<>(8);
        for (PartOffset port : PORTS) {
            Direction outward = port.z() == 3 ? facing : port.z() == -3 ? facing.getOpposite()
                    : port.x() == 2 ? side : side.getOpposite();
            BlockPos position = core.relative(side, port.x()).relative(facing, port.z());
            connections.add(new Connection(position, outward));
        }
        return connections;
    }

    public static boolean canConnectAt(BlockState state, @Nullable Direction side) {
        if (side == null || !side.getAxis().isHorizontal()) return false;
        PartOffset port = offset(state);
        if (!PORTS.contains(port)) return false;
        Direction facing = state.getValue(FACING);
        Direction cross = facing.getClockWise();
        return port.z() == 3 && side == facing || port.z() == -3 && side == facing.getOpposite()
                || port.x() == 2 && side == cross || port.x() == -3 && side == cross.getOpposite();
    }

    private BlockState stateForPart(BlockPos part, BlockPos core, Direction facing) {
        Direction side = facing.getClockWise();
        BlockPos delta = part.subtract(core);
        PartOffset offset = new PartOffset(delta.getX() * side.getStepX() + delta.getZ() * side.getStepZ(),
                delta.getY(), delta.getX() * facing.getStepX() + delta.getZ() * facing.getStepZ());
        Integer index = PART_INDICES.get(offset);
        if (index == null) throw new IllegalArgumentException("Position is outside Catalytic Cracker volume: " + offset);
        return defaultBlockState().setValue(FACING, facing).setValue(PART, index);
    }

    private static PartOffset offset(BlockState state) { return PARTS.get(state.getValue(PART)); }

    private static List<PartOffset> buildParts() {
        LinkedHashSet<PartOffset> parts = new LinkedHashSet<>();
        addBox(parts, -3, 2, 0, 0, -3, 3);
        addBox(parts, 0, 2, 1, 8, -3, -1);
        addBox(parts, -1, 2, 0, 13, 0, 3);
        addBox(parts, 0, 1, 13, 14, 1, 2);
        addBox(parts, -3, -1, 1, 3, -2, 3);
        return List.copyOf(parts);
    }

    private static void addBox(Set<PartOffset> parts, int minX, int maxX, int minY, int maxY,
                               int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) parts.add(new PartOffset(x, y, z));
            }
        }
    }

    private static Map<PartOffset, Integer> buildPartIndices() {
        Map<PartOffset, Integer> indices = new LinkedHashMap<>();
        for (int index = 0; index < PARTS.size(); index++) indices.put(PARTS.get(index), index);
        return Map.copyOf(indices);
    }

    public record PartOffset(int x, int y, int z) { }
    public record Connection(BlockPos port, Direction outward) { }
}
