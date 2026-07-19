package com.hbm.ntm.entity;

import com.hbm.ntm.conveyor.ConveyorEnterable;
import com.hbm.ntm.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

public final class MovingConveyorItemEntity extends MovingConveyorObjectEntity {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(
            MovingConveyorItemEntity.class, EntityDataSerializers.ITEM_STACK);

    public MovingConveyorItemEntity(EntityType<? extends MovingConveyorItemEntity> type, Level level) {
        super(type, level);
    }

    public static MovingConveyorItemEntity create(Level level, ItemStack stack) {
        MovingConveyorItemEntity entity = new MovingConveyorItemEntity(ModEntities.MOVING_CONVEYOR_ITEM.get(), level);
        entity.setItemStack(stack);
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ITEM, ItemStack.EMPTY);
    }

    public ItemStack getItemStack() {
        return entityData.get(ITEM);
    }

    public void setItemStack(ItemStack stack) {
        entityData.set(ITEM, stack.copy());
    }

    @Override
    public void tick() {
        if (!level().isClientSide && getItemStack().isEmpty()) {
            discard();
            return;
        }
        super.tick();
    }

    @Override
    protected boolean tryEnter(BlockPos pos, Direction incomingSide) {
        if (!isAlive()) return true;
        var state = level().getBlockState(pos);
        if (state.getBlock() instanceof ConveyorEnterable enterable
                && enterable.canConveyorItemEnter(level(), pos, incomingSide, this)) {
            enterable.onConveyorItemEnter(level(), pos, incomingSide, this);
            discard();
            return true;
        }

        var handler = level().getCapability(Capabilities.ItemHandler.BLOCK, pos, incomingSide);
        if (handler == null && level().getBlockEntity(pos) instanceof Container container) {
            handler = new InvWrapper(container);
        }
        if (handler == null) {
            return false;
        }
        ItemStack before = getItemStack();
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler, before.copy(), false);
        if (remainder.getCount() == before.getCount()) {
            return false;
        }
        if (remainder.isEmpty()) {
            discard();
        } else {
            setItemStack(remainder);
        }
        return true;
    }

    @Override
    protected void leaveConveyor() {
        if (!isAlive()) return;
        ItemStack stack = getItemStack().copy();
        Vec3Helper.spawnItem(level(), getX() + getDeltaMovement().x * 2.0D,
                getY() + getDeltaMovement().y * 2.0D,
                getZ() + getDeltaMovement().z * 2.0D,
                stack, getDeltaMovement().x * 2.0D, 0.1D, getDeltaMovement().z * 2.0D);
        discard();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack remainder = getItemStack().copy();
        int before = remainder.getCount();
        player.getInventory().add(remainder);
        if (remainder.getCount() < before) {
            if (remainder.isEmpty()) discard(); else setItemStack(remainder);
            player.inventoryMenu.broadcastChanges();
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && isAlive()) {
            Vec3Helper.spawnItem(level(), getX(), getY(), getZ(), getItemStack().copy(), 0, 0, 0);
            discard();
        }
        return true;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (!getItemStack().isEmpty()) {
            tag.put("Item", getItemStack().save(level().registryAccess()));
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setItemStack(tag.contains("Item")
                ? ItemStack.parseOptional(level().registryAccess(), tag.getCompound("Item")) : ItemStack.EMPTY);
    }

    private static final class Vec3Helper {
        private Vec3Helper() {
        }

        private static void spawnItem(Level level, double x, double y, double z, ItemStack stack,
                                      double motionX, double motionY, double motionZ) {
            if (stack.isEmpty()) return;
            ItemEntity dropped = new ItemEntity(level, x, y, z, stack);
            dropped.lifespan = 60 * 20;
            dropped.setDeltaMovement(motionX, motionY, motionZ);
            level.addFreshEntity(dropped);
        }
    }
}
