package com.hbm.ntm.entity;

import com.hbm.ntm.conveyor.ConveyorEnterable;
import com.hbm.ntm.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class MovingConveyorPackageEntity extends MovingConveyorObjectEntity {
    private final List<ItemStack> contents = new ArrayList<>();

    public MovingConveyorPackageEntity(EntityType<? extends MovingConveyorPackageEntity> type, Level level) {
        super(type, level);
    }

    public static MovingConveyorPackageEntity create(Level level, List<ItemStack> stacks) {
        MovingConveyorPackageEntity entity = new MovingConveyorPackageEntity(
                ModEntities.MOVING_CONVEYOR_PACKAGE.get(), level);
        entity.setItemStacks(stacks);
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    public void setItemStacks(List<ItemStack> stacks) {
        contents.clear();
        stacks.stream().map(stack -> stack == null ? ItemStack.EMPTY : stack.copy()).forEach(contents::add);
    }

    public List<ItemStack> getItemStacks() {
        return contents.stream().map(ItemStack::copy).toList();
    }

    @Override
    protected boolean tryEnter(BlockPos pos, Direction incomingSide) {
        var state = level().getBlockState(pos);
        if (state.getBlock() instanceof ConveyorEnterable enterable
                && enterable.canConveyorPackageEnter(level(), pos, incomingSide, this)) {
            enterable.onConveyorPackageEnter(level(), pos, incomingSide, this);
            discard();
            return true;
        }
        return false;
    }

    @Override
    protected void leaveConveyor() {
        dropContents(true);
    }

    private void dropContents(boolean carryMotion) {
        if (!isAlive()) return;
        for (ItemStack stack : contents) {
            if (stack.isEmpty()) continue;
            ItemEntity dropped = new ItemEntity(level(), getX() + getDeltaMovement().x * 2.0D,
                    getY() + 0.125D, getZ() + getDeltaMovement().z * 2.0D, stack.copy());
            if (carryMotion) {
                dropped.setDeltaMovement(getDeltaMovement().x * 2.0D, 0.1D,
                        getDeltaMovement().z * 2.0D);
            }
            level().addFreshEntity(dropped);
        }
        discard();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        for (ItemStack stack : contents) {
            if (stack.isEmpty()) continue;
            ItemStack remainder = stack.copy();
            player.getInventory().add(remainder);
            if (!remainder.isEmpty()) player.drop(remainder, false);
        }
        player.inventoryMenu.broadcastChanges();
        discard();
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide) dropContents(false);
        return true;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ListTag list = new ListTag();
        for (int slot = 0; slot < contents.size(); slot++) {
            if (contents.get(slot).isEmpty()) continue;
            CompoundTag entry = (CompoundTag) contents.get(slot).save(level().registryAccess());
            entry.putInt("Slot", slot);
            list.add(entry);
        }
        tag.put("Contents", list);
        tag.putInt("Count", contents.size());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        contents.clear();
        ListTag list = tag.getList("Contents", Tag.TAG_COMPOUND);
        int count = Math.max(tag.getInt("Count"), list.size());
        for (int slot = 0; slot < count; slot++) contents.add(ItemStack.EMPTY);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            ItemStack stack = ItemStack.parseOptional(level().registryAccess(), entry);
            int slot = entry.contains("Slot") ? entry.getInt("Slot") : index;
            while (contents.size() <= slot) contents.add(ItemStack.EMPTY);
            if (!stack.isEmpty()) contents.set(slot, stack);
        }
    }
}
