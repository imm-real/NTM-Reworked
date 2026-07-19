package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.DfcComponentBlock;
import com.hbm.ntm.dfc.DfcKind;
import com.hbm.ntm.energy.HeReceiver;
import com.hbm.ntm.item.DfcLensItem;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class DfcStabilizerBlockEntity extends DfcBlockEntity implements HeReceiver {
    public static final long MAX_POWER = 2_500_000_000L;
    public static final int RANGE = 15;
    private long power;
    private int watts;
    private int beam;
    private long lastSync = Long.MIN_VALUE;

    public DfcStabilizerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DFC_STABILIZER.get(), pos, state, DfcKind.STABILIZER);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DfcStabilizerBlockEntity stabilizer) {
        if (level instanceof ServerLevel server) stabilizer.serverTick(server, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        for (Direction direction : Direction.values()) trySubscribe(level, pos.relative(direction), direction);
        watts = Mth.clamp(watts, 1, 100);
        int demand = demand(watts);
        beam = 0;
        ItemStack lens = items.get(0);
        if (power >= demand && lens.is(ModItems.AMS_LENS.get()) && DfcLensItem.damage(lens) < DfcLensItem.MAX_DAMAGE) {
            Direction direction = state.getValue(DfcComponentBlock.FACING);
            for (int i = 1; i <= RANGE; i++) {
                BlockPos target = pos.relative(direction, i);
                if (level.getBlockEntity(target) instanceof DfcCoreBlockEntity core) {
                    core.reinforceField(watts);
                    power -= demand;
                    beam = i;
                    long damage = DfcLensItem.damage(lens) + watts;
                    if (damage >= DfcLensItem.MAX_DAMAGE) items.set(0, ItemStack.EMPTY);
                    else DfcLensItem.setDamage(lens, damage);
                    break;
                }
                if (!level.getBlockState(target).isAir()) break;
            }
        }
        sync(level, pos, state);
        setChanged();
    }

    public static int demand(int watts) {
        int clamped = Mth.clamp(watts, 1, 100);
        return clamped * clamped * clamped * clamped;
    }

    public void setControl(int watts) { this.watts = Mth.clamp(watts, 1, 100); setChanged(); }
    public int watts() { return watts; }
    public int beam() { return beam; }

    private void sync(ServerLevel level, BlockPos pos, BlockState state) {
        long signature = power ^ ((long) watts << 32) ^ ((long) beam << 48)
                ^ (items.get(0).isEmpty() ? 0L : DfcLensItem.damage(items.get(0)));
        if (signature != lastSync || level.getGameTime() % 20L == 0L) {
            lastSync = signature;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override protected int menuValue(int index) {
        return switch (index) {
            case 0 -> (int) power;
            case 1 -> (int) (power >>> 32);
            case 2 -> (int) MAX_POWER;
            case 3 -> (int) (MAX_POWER >>> 32);
            case 4 -> watts;
            case 5 -> beam;
            default -> 0;
        };
    }

    @Override protected void saveDfcState(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.putLong("power", power);
        tag.putInt("watts", watts);
        tag.putInt("beam", beam);
    }

    @Override protected void loadDfcState(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        power = Math.clamp(tag.getLong("power"), 0L, MAX_POWER);
        watts = tag.getInt("watts");
        beam = tag.getInt("beam");
    }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot == 0 && stack.is(ModItems.AMS_LENS.get()); }
    @Override public long getPower() { return power; }
    @Override public void setPower(long power) { this.power = Math.clamp(power, 0L, MAX_POWER); setChanged(); }
    @Override public long getMaxPower() { return MAX_POWER; }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }
}
