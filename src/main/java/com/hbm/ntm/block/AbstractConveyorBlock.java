package com.hbm.ntm.block;

import com.hbm.ntm.conveyor.ConveyorBelt;
import com.hbm.ntm.conveyor.ConveyorEnterable;
import com.hbm.ntm.conveyor.ConveyorType;
import com.hbm.ntm.entity.MovingConveyorItemEntity;
import com.hbm.ntm.item.ConveyorWandItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;

public abstract class AbstractConveyorBlock extends Block implements ConveyorBelt {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final double LIVING_ENTITY_SPEED = 0.1D;
    private final ConveyorType inventoryType;

    protected AbstractConveyorBlock(Properties properties, ConveyorType inventoryType) {
        super(properties);
        this.inventoryType = inventoryType;
    }

    public final ConveyorType inventoryType() {
        return inventoryType;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof ItemEntity dropped && dropped.tickCount > 10
                && dropped.isAlive() && !dropped.getItem().isEmpty()) {
            MovingConveyorItemEntity moving = MovingConveyorItemEntity.create(level, dropped.getItem().copy());
            var snap = closestSnappingPosition(level, pos, state, dropped.position());
            moving.setPos(snap.x, snap.y, snap.z);
            moving.setDeltaMovement(dropped.getDeltaMovement());
            level.addFreshEntity(moving);
            dropped.discard();
        }
        super.entityInside(state, level, pos, entity);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (entity instanceof LivingEntity living && !living.isSpectator()) {
            Direction travel = travelDirection(level, pos, state, living.position());
            if (travel.getAxis().isHorizontal()) {
                double speed = LIVING_ENTITY_SPEED * speedMultiplier(level, pos, state, living.position());
                double beltX = -travel.getStepX();
                double beltZ = -travel.getStepZ();
                var movement = living.getDeltaMovement();
                double forwardSpeed = movement.x * beltX + movement.z * beltZ;
                if (forwardSpeed < speed) {
                    double correction = speed - forwardSpeed;
                    living.push(beltX * correction, 0.0D, beltZ * correction);
                }
            }
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.SCREWDRIVER.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            onScrewdriver(level, pos, state, player.isShiftKeyDown());
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    protected abstract void onScrewdriver(Level level, BlockPos pos, BlockState state, boolean sneaking);

    protected static Direction rotate(Direction facing) {
        return facing.getClockWise();
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return ConveyorWandItem.stackFor(inventoryType, 1);
    }

    public static boolean isConveyor(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof ConveyorBelt;
    }

    public static boolean isEntryTarget(LevelAccessor accessor, BlockPos pos, Direction incomingSide) {
        BlockState state = accessor.getBlockState(pos);
        if (state.getBlock() instanceof ConveyorEnterable) {
            return true;
        }
        return accessor instanceof Level level && (level.getCapability(
                Capabilities.ItemHandler.BLOCK, pos, incomingSide) != null
                || level.getBlockEntity(pos) instanceof Container);
    }
}
