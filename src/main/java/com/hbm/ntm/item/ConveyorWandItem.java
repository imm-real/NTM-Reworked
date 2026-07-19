package com.hbm.ntm.item;

import com.hbm.ntm.block.AbstractConveyorBlock;
import com.hbm.ntm.block.ConveyorBlock;
import com.hbm.ntm.block.ConveyorChuteBlock;
import com.hbm.ntm.block.ConveyorLiftBlock;
import com.hbm.ntm.conveyor.ConveyorBelt;
import com.hbm.ntm.conveyor.ConveyorCurve;
import com.hbm.ntm.conveyor.ConveyorEnterable;
import com.hbm.ntm.conveyor.ConveyorType;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A wand where every spell costs one conveyor belt. */
public final class ConveyorWandItem extends Item {
    private static final String SELECTED = "ConveyorSelected";
    private static final String X = "ConveyorX";
    private static final String Y = "ConveyorY";
    private static final String Z = "ConveyorZ";
    private static final String SIDE = "ConveyorSide";
    private static final String COUNT = "ConveyorCount";
    private static final int MAX_CREATIVE_ROUTE = 256;
    private final ConveyorType type;

    public ConveyorWandItem(ConveyorType type) {
        super(new Properties());
        this.type = type;
    }

    public ConveyorType type() {
        return type;
    }

    public static ItemStack stackFor(ConveyorType type, int count) {
        Item item = switch (type) {
            case REGULAR -> ModItems.CONVEYOR_WAND.get();
            case EXPRESS -> ModItems.CONVEYOR_WAND_EXPRESS.get();
            case DOUBLE -> ModItems.CONVEYOR_WAND_DOUBLE.get();
            case TRIPLE -> ModItems.CONVEYOR_WAND_TRIPLE.get();
        };
        return new ItemStack(item, count);
    }

    public static Block conveyorBlock(ConveyorType type) {
        return switch (type) {
            case REGULAR -> ModBlocks.CONVEYOR.get();
            case EXPRESS -> ModBlocks.CONVEYOR_EXPRESS.get();
            case DOUBLE -> ModBlocks.CONVEYOR_DOUBLE.get();
            case TRIPLE -> ModBlocks.CONVEYOR_TRIPLE.get();
        };
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = context.getLevel();

        if (player.isShiftKeyDown() && !hasSelection(stack)) {
            if (!level.isClientSide) {
                placeSingle(context, player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        Endpoint endpoint = snapEndpoint(level, context.getClickedPos(), context.getClickedFace(),
                hasSelection(stack));
        if (!hasSelection(stack)) {
            int available = player.getAbilities().instabuild ? MAX_CREATIVE_ROUTE : countAvailable(player);
            setSelection(stack, endpoint.pos(), endpoint.side(), available);
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.hbm.conveyor.start"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        Selection selection = selection(stack);
        if (!level.isClientSide) {
            int available = player.getAbilities().instabuild ? MAX_CREATIVE_ROUTE
                    : Math.min(selection.count(), countAvailable(player));
            Route route = planRoute(level, type, player.getDirection(), selection.pos(), selection.side(),
                    endpoint.pos(), endpoint.side(), available);
            if (route.status() == RouteStatus.SUCCESS) {
                route.placements().forEach(placement -> level.setBlock(placement.pos(), placement.state(),
                        Block.UPDATE_ALL));
                route.placements().forEach(placement -> {
                    BlockState placed = level.getBlockState(placement.pos());
                    if (placed.getBlock() instanceof ConveyorChuteBlock chute) {
                        level.setBlock(placement.pos(), chute.recalculateState(placed, level,
                                placement.pos()), Block.UPDATE_ALL);
                    }
                });
                if (!player.getAbilities().instabuild) consume(player, route.placements().size());
                player.displayClientMessage(Component.translatable("message.hbm.conveyor.built",
                        route.placements().size()), false);
            } else if (route.status() == RouteStatus.INSUFFICIENT) {
                player.displayClientMessage(Component.translatable("message.hbm.conveyor.insufficient"), false);
            } else {
                player.displayClientMessage(Component.translatable("message.hbm.conveyor.obstructed"), false);
            }
        }
        clearSelection(stack);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Rehearse the route so the client can draw judgmental red ghosts. */
    @Nullable
    public Route previewRoute(Level level, ItemStack stack, Direction playerFacing, BlockHitResult hit) {
        if (!hasSelection(stack)) return null;
        Selection selected = selection(stack);
        Endpoint endpoint = snapEndpoint(level, hit.getBlockPos(), hit.getDirection(), true);
        return planRoute(level, type, playerFacing, selected.pos(), selected.side(),
                endpoint.pos(), endpoint.side(), selected.count());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (entity instanceof Player player && hasSelection(stack)
                && player.getMainHandItem() != stack && player.getOffhandItem() != stack) {
            clearSelection(stack);
        }
    }

    private void placeSingle(UseOnContext context, Player player) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        Direction side = context.getClickedFace();
        BlockState clickedState = level.getBlockState(clicked);

        if (type.supportsVerticalRouting() && clickedState.getBlock() == ModBlocks.CONVEYOR.get()
                && clickedState.getValue(ConveyorBlock.CURVE) == ConveyorCurve.STRAIGHT) {
            if (side == Direction.UP) {
                clickedState = ModBlocks.CONVEYOR_LIFT.get().defaultBlockState()
                        .setValue(AbstractConveyorBlock.FACING,
                                clickedState.getValue(AbstractConveyorBlock.FACING));
                level.setBlock(clicked, clickedState, Block.UPDATE_ALL);
            } else if (side == Direction.DOWN) {
                ConveyorChuteBlock chute = ModBlocks.CONVEYOR_CHUTE.get();
                clickedState = chute.defaultBlockState().setValue(AbstractConveyorBlock.FACING,
                        clickedState.getValue(AbstractConveyorBlock.FACING));
                clickedState = chute.recalculateState(clickedState, level, clicked);
                level.setBlock(clicked, clickedState, Block.UPDATE_ALL);
            }
        }

        Block toPlace = conveyorBlock(type);
        if (type.supportsVerticalRouting()) {
            if (clickedState.getBlock() instanceof ConveyorLiftBlock && side == Direction.UP) {
                toPlace = ModBlocks.CONVEYOR_LIFT.get();
            } else if (clickedState.getBlock() instanceof ConveyorChuteBlock && side == Direction.DOWN) {
                toPlace = ModBlocks.CONVEYOR_CHUTE.get();
            }
        }

        BlockPos target = clicked.relative(side);
        if (!level.getBlockState(target).canBeReplaced()) return;
        BlockState placed = toPlace.defaultBlockState().setValue(AbstractConveyorBlock.FACING,
                player.getDirection().getOpposite());
        if (toPlace instanceof ConveyorChuteBlock chute) {
            placed = chute.recalculateState(placed, level, target);
        }
        level.setBlock(target, placed, Block.UPDATE_ALL);
        if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
    }

    private Endpoint snapEndpoint(Level level, BlockPos clicked, Direction side, boolean ending) {
        BlockState state = level.getBlockState(clicked);
        if (state.getBlock() instanceof ConveyorBlock belt) {
            Direction snapped = ending ? belt.inputDirection(level, clicked, state)
                    : belt.outputDirection(level, clicked, state);
            if (level.getBlockState(clicked.relative(snapped)).canBeReplaced()) {
                return new Endpoint(clicked, snapped);
            }
        }
        return new Endpoint(clicked, side);
    }

    public static Route planRoute(Level level, ConveyorType type, Direction playerFacing,
                                  BlockPos start, Direction startSide, BlockPos end, Direction endSide,
                                  int maximum) {
        if (maximum <= 0) return new Route(RouteStatus.INSUFFICIENT, List.of());

        if (start.equals(end) && startSide == endSide && startSide.getAxis() == Direction.Axis.Y) {
            BlockPos position = start.relative(startSide);
            if (!level.getBlockState(position).canBeReplaced()) {
                return new Route(RouteStatus.OBSTRUCTED, List.of(), position);
            }
            BlockState state = conveyorBlock(type).defaultBlockState()
                    .setValue(AbstractConveyorBlock.FACING, playerFacing.getOpposite());
            return new Route(RouteStatus.SUCCESS, List.of(new Placement(position, state)));
        }

        boolean vertical = type.supportsVerticalRouting();
        BlockPos target = end.relative(endSide);
        BlockPos current = start.relative(startSide);
        Direction direction = startSide;
        if (direction.getAxis() == Direction.Axis.Y) {
            direction = targetDirection(current, end, target, null, false, vertical);
        }

        Block endBlock = level.getBlockState(end).getBlock();
        boolean turnToTarget = endSide.getAxis().isHorizontal() || endBlock instanceof ConveyorEnterable
                || endBlock instanceof ConveyorLiftBlock || endBlock instanceof ConveyorChuteBlock;
        Direction horizontalDirection = direction.getAxis().isHorizontal() ? direction : playerFacing;

        if (vertical && current.getY() > target.getY() && level.getBlockState(current.below()).canBeReplaced()) {
            direction = Direction.DOWN;
        }

        List<Placement> placements = new ArrayList<>();
        Set<BlockPos> occupied = new HashSet<>();
        for (int depth = 1; depth <= maximum; depth++) {
            if (!level.getBlockState(current).canBeReplaced() || !occupied.add(current)) {
                return new Route(RouteStatus.OBSTRUCTED,
                        withChuteTopology(level, placements), current);
            }

            Direction oldDirection = direction;
            Block block = blockForDirection(type, oldDirection);
            ConveyorCurve curve = ConveyorCurve.STRAIGHT;
            BlockPos next = current.relative(oldDirection);
            int fromDistance = taxiDistance(current, target);
            int toDistance = taxiDistance(next, target);
            int finalDistance = taxiDistance(next, end);
            boolean notAtTarget = (turnToTarget ? finalDistance : fromDistance) > 0;
            boolean willBeObstructed = notAtTarget && !level.getBlockState(next).canBeReplaced();
            boolean shouldTurn = (toDistance >= fromDistance && notAtTarget) || willBeObstructed;

            if (shouldTurn) {
                Direction nextDirection = targetDirection(current, turnToTarget ? end : target, target,
                        oldDirection, willBeObstructed, vertical);
                if (nextDirection == Direction.UP) {
                    block = ModBlocks.CONVEYOR_LIFT.get();
                } else if (nextDirection == Direction.DOWN) {
                    block = ModBlocks.CONVEYOR_CHUTE.get();
                } else if (block instanceof ConveyorBlock && oldDirection.getAxis().isHorizontal()) {
                    curve = nextDirection == oldDirection.getCounterClockWise()
                            ? ConveyorCurve.LEFT : ConveyorCurve.RIGHT;
                }
                direction = nextDirection;
            }

            Direction stateFacing;
            if (block instanceof ConveyorBlock) {
                stateFacing = oldDirection.getOpposite();
            } else {
                stateFacing = endSide.getAxis().isHorizontal() ? endSide : horizontalDirection.getOpposite();
            }
            BlockState state = block.defaultBlockState().setValue(AbstractConveyorBlock.FACING, stateFacing);
            if (block instanceof ConveyorBlock) state = state.setValue(ConveyorBlock.CURVE, curve);
            placements.add(new Placement(current, state));

            if (current.equals(target)) {
                return new Route(RouteStatus.SUCCESS, withChuteTopology(level, placements));
            }
            current = current.relative(direction);
            if (direction.getAxis().isHorizontal()) horizontalDirection = direction;
        }
        return new Route(RouteStatus.INSUFFICIENT, withChuteTopology(level, placements));
    }

    /** Let imaginary chutes inspect their imaginary neighbors. */
    private static List<Placement> withChuteTopology(Level level, List<Placement> placements) {
        Map<BlockPos, BlockState> planned = new HashMap<>();
        placements.forEach(placement -> planned.put(placement.pos(), placement.state()));

        List<Placement> resolved = new ArrayList<>(placements.size());
        for (Placement placement : placements) {
            BlockState state = placement.state();
            if (state.getBlock() instanceof ConveyorChuteBlock) {
                BlockPos pos = placement.pos();
                boolean bottom = !plannedConveyor(level, planned, pos.below())
                        && !AbstractConveyorBlock.isEntryTarget(level, pos.below(), Direction.UP);
                state = state.setValue(ConveyorChuteBlock.BOTTOM, bottom)
                        .setValue(ConveyorChuteBlock.NORTH,
                                plannedConveyor(level, planned, pos.north()))
                        .setValue(ConveyorChuteBlock.EAST,
                                plannedConveyor(level, planned, pos.east()))
                        .setValue(ConveyorChuteBlock.SOUTH,
                                plannedConveyor(level, planned, pos.south()))
                        .setValue(ConveyorChuteBlock.WEST,
                                plannedConveyor(level, planned, pos.west()));
            }
            resolved.add(new Placement(placement.pos(), state));
        }
        return List.copyOf(resolved);
    }

    private static boolean plannedConveyor(Level level, Map<BlockPos, BlockState> planned, BlockPos pos) {
        BlockState state = planned.get(pos);
        return state != null ? state.getBlock() instanceof ConveyorBelt
                : AbstractConveyorBlock.isConveyor(level, pos);
    }

    private static Block blockForDirection(ConveyorType type, Direction direction) {
        if (direction == Direction.UP) return ModBlocks.CONVEYOR_LIFT.get();
        if (direction == Direction.DOWN) return ModBlocks.CONVEYOR_CHUTE.get();
        return conveyorBlock(type);
    }

    private static Direction targetDirection(BlockPos current, BlockPos destination, BlockPos target,
                                             Direction heading, boolean obstructed, boolean vertical) {
        if (vertical && (current.getY() != destination.getY() || current.getY() != target.getY())
                && (obstructed || sameColumn(current, destination) || sameColumn(current, target))) {
            return current.getY() > destination.getY() ? Direction.DOWN : Direction.UP;
        }
        int dx = current.getX() - destination.getX();
        int dz = current.getZ() - destination.getZ();
        if (Math.abs(dx) > Math.abs(dz)) {
            if (heading == Direction.EAST || heading == Direction.WEST) {
                return dz > 0 ? Direction.NORTH : Direction.SOUTH;
            }
            return dx > 0 ? Direction.WEST : Direction.EAST;
        }
        if (heading == Direction.NORTH || heading == Direction.SOUTH) {
            return dx > 0 ? Direction.WEST : Direction.EAST;
        }
        return dz > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    private static boolean sameColumn(BlockPos first, BlockPos second) {
        return first.getX() == second.getX() && first.getZ() == second.getZ();
    }

    private static int taxiDistance(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX()) + Math.abs(first.getY() - second.getY())
                + Math.abs(first.getZ() - second.getZ());
    }

    private int countAvailable(Player player) {
        int count = 0;
        int limit = Math.min(36, player.getInventory().getContainerSize());
        for (int slot = 0; slot < limit; slot++) {
            ItemStack inventoryStack = player.getInventory().getItem(slot);
            if (inventoryStack.is(this)) count += inventoryStack.getCount();
        }
        return count;
    }

    private void consume(Player player, int amount) {
        int limit = Math.min(36, player.getInventory().getContainerSize());
        for (int slot = 0; slot < limit && amount > 0; slot++) {
            ItemStack inventoryStack = player.getInventory().getItem(slot);
            if (!inventoryStack.is(this)) continue;
            int removing = Math.min(amount, inventoryStack.getCount());
            inventoryStack.shrink(removing);
            amount -= removing;
        }
        player.inventoryMenu.broadcastChanges();
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level world, BlockPos pos, Player player) {
        if (!player.isShiftKeyDown() || !player.getAbilities().instabuild
                || !(player.level() instanceof ServerLevel level)
                || !(level.getBlockState(pos).getBlock() instanceof ConveyorBelt belt)) {
            return false;
        }
        Set<BlockPos> visited = new HashSet<>();
        visited.add(pos);
        breakConnected(level, pos.relative(belt.inputDirection(level, pos, state)), player, 32, visited);
        breakConnected(level, pos.relative(belt.outputDirection(level, pos, state)), player, 32, visited);
        return false;
    }

    private static void breakConnected(ServerLevel level, BlockPos pos, Player player, int depth,
                                       Set<BlockPos> visited) {
        if (depth <= 0 || !visited.add(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ConveyorBelt belt)) return;
        Direction input = belt.inputDirection(level, pos, state);
        Direction output = belt.outputDirection(level, pos, state);
        if (!level.destroyBlock(pos, false, player)) return;
        breakConnected(level, pos.relative(input), player, depth - 1, visited);
        breakConnected(level, pos.relative(output), player, depth - 1, visited);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hbm.conveyor_wand.desc.0")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("item.hbm.conveyor_wand.desc.1")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("item.hbm.conveyor_wand.desc.2")
                .withStyle(ChatFormatting.YELLOW));
        if (type.supportsVerticalRouting()) {
            tooltip.add(Component.translatable("item.hbm.conveyor_wand.vertical.desc")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    private static boolean hasSelection(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(SELECTED);
    }

    private static void setSelection(ItemStack stack, BlockPos pos, Direction side, int count) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(SELECTED, true);
        tag.putInt(X, pos.getX());
        tag.putInt(Y, pos.getY());
        tag.putInt(Z, pos.getZ());
        tag.putInt(SIDE, side.get3DDataValue());
        tag.putInt(COUNT, count);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static Selection selection(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return new Selection(new BlockPos(tag.getInt(X), tag.getInt(Y), tag.getInt(Z)),
                Direction.from3DDataValue(tag.getInt(SIDE)), tag.getInt(COUNT));
    }

    private static void clearSelection(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(SELECTED);
        tag.remove(X);
        tag.remove(Y);
        tag.remove(Z);
        tag.remove(SIDE);
        tag.remove(COUNT);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private record Endpoint(BlockPos pos, Direction side) {
    }

    private record Selection(BlockPos pos, Direction side, int count) {
    }

    public record Placement(BlockPos pos, BlockState state) {
    }

    public record Route(RouteStatus status, List<Placement> placements, BlockPos obstruction) {
        public Route(RouteStatus status, List<Placement> placements) {
            this(status, placements, null);
        }
    }

    public enum RouteStatus {
        SUCCESS,
        INSUFFICIENT,
        OBSTRUCTED
    }
}
