package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.DfcComponentBlock;
import com.hbm.ntm.dfc.DfcKind;
import com.hbm.ntm.dfc.DfcLaserable;
import com.hbm.ntm.dfc.DfcTank;
import com.hbm.ntm.energy.HeReceiver;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public final class DfcEmitterBlockEntity extends DfcBlockEntity implements HeReceiver, DfcLaserable {
    public static final long MAX_POWER = 1_000_000_000L;
    public static final int CRYOGEL_CAPACITY = 64_000;
    public static final int RANGE = 50;
    private final DfcTank cryogel = new DfcTank(ModFluids.CRYOGEL.get(), CRYOGEL_CAPACITY,
            fluid -> fluid.isSame(ModFluids.CRYOGEL.get()), this::setChanged);
    private long power;
    private int watts;
    private int beam;
    private long joules;
    private long previous;
    private boolean on;
    private long lastSync = Long.MIN_VALUE;

    public DfcEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DFC_EMITTER.get(), pos, state, DfcKind.EMITTER);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DfcEmitterBlockEntity emitter) {
        if (level instanceof ServerLevel server) emitter.serverTick(server, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        for (Direction direction : Direction.values()) trySubscribe(level, pos.relative(direction), direction);
        watts = Mth.clamp(watts, 1, 100);
        long demand = MAX_POWER * watts / 2_000L;
        beam = 0;
        if (joules > 0L || previous > 0L) {
            if (cryogel.amount() >= 20) cryogel.remove(20);
            else {
                level.setBlock(pos, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
                return;
            }
        }
        if (on) {
            if (power >= demand) {
                power -= demand;
                joules += watts * 100L;
            }
            previous = joules;
            if (joules > 0L) fire(level, pos, state.getValue(DfcComponentBlock.FACING), joules * 95L / 100L);
            joules = 0L;
        } else {
            joules = 0L;
            previous = 0L;
        }
        sync(level, pos, state);
        setChanged();
    }

    private void fire(ServerLevel level, BlockPos pos, Direction direction, long output) {
        for (int i = 1; i <= RANGE; i++) {
            beam = i;
            BlockPos target = pos.relative(direction, i);
            BlockState targetState = level.getBlockState(target);
            var blockEntity = level.getBlockEntity(target);
            if (blockEntity instanceof DfcLaserable laserable) {
                laserable.addDfcEnergy(level, output, direction);
                break;
            }
            if (blockEntity instanceof DfcCoreBlockEntity core) {
                output = core.burn(output);
                continue;
            }
            if (!targetState.getFluidState().isEmpty()) {
                level.playSound(null, target, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.removeBlock(target, false);
                break;
            }
            if (!targetState.isAir()) {
                if (targetState.getBlock().getExplosionResistance() < 6_000.0F && level.random.nextInt(20) == 0) {
                    level.destroyBlock(target, false);
                }
                break;
            }
        }
        double minX = Math.min(pos.getX(), pos.getX() + direction.getStepX() * beam) + 0.2D;
        double maxX = Math.max(pos.getX(), pos.getX() + direction.getStepX() * beam) + 0.8D;
        double minY = Math.min(pos.getY(), pos.getY() + direction.getStepY() * beam) + 0.2D;
        double maxY = Math.max(pos.getY(), pos.getY() + direction.getStepY() * beam) + 0.8D;
        double minZ = Math.min(pos.getZ(), pos.getZ() + direction.getStepZ() * beam) + 0.2D;
        double maxZ = Math.max(pos.getZ(), pos.getZ() + direction.getStepZ() * beam) + 0.8D;
        for (Entity entity : level.getEntities(null, new AABB(minX, minY, minZ, maxX, maxY, maxZ))) {
            entity.hurt(level.damageSources().source(ModDamageTypes.AMS_CORE), 50.0F);
            entity.igniteForSeconds(10.0F);
        }
    }

    @Override public void addDfcEnergy(ServerLevel level, long energy, Direction incomingDirection) {
        Direction facing = getBlockState().getValue(DfcComponentBlock.FACING);
        if (incomingDirection.getOpposite() != facing) {
            joules += energy;
            setChanged();
        }
    }

    public void setControl(int watts, boolean toggle) {
        this.watts = Mth.clamp(watts, 1, 100);
        if (toggle) on = !on;
        setChanged();
    }

    public DfcTank cryogel() { return cryogel; }
    public int watts() { return watts; }
    public int beam() { return beam; }
    public long previous() { return previous; }
    public boolean isOn() { return on; }
    public long demand() { return MAX_POWER * Mth.clamp(watts, 1, 100) / 2_000L; }

    private void sync(ServerLevel level, BlockPos pos, BlockState state) {
        long signature = power ^ previous ^ ((long) beam << 40) ^ ((long) cryogel.amount() << 17)
                ^ ((long) watts << 8) ^ (on ? 1L : 0L);
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
            case 6 -> on ? 1 : 0;
            case 7 -> (int) previous;
            case 8 -> (int) (previous >>> 32);
            case 9 -> cryogel.amount();
            case 10 -> FluidIdentifierItem.Selection.CRYOGEL.ordinal();
            default -> 0;
        };
    }

    @Override protected void saveDfcState(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.putLong("power", power);
        tag.putInt("watts", watts);
        tag.putLong("joules", joules);
        tag.putLong("previous", previous);
        tag.putBoolean("on", on);
        tag.putInt("beam", beam);
        tag.put("cryogel", cryogel.save(registries));
    }

    @Override protected void loadDfcState(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        power = Math.clamp(tag.getLong("power"), 0L, MAX_POWER);
        watts = tag.getInt("watts");
        joules = tag.getLong("joules");
        previous = tag.getLong("previous");
        on = tag.getBoolean("on");
        beam = tag.getInt("beam");
        if (tag.contains("cryogel")) cryogel.load(tag.getCompound("cryogel"), registries);
    }

    @Override public boolean canPlaceItem(int slot, net.minecraft.world.item.ItemStack stack) { return false; }
    @Override public long getPower() { return power; }
    @Override public void setPower(long power) { this.power = Math.clamp(power, 0L, MAX_POWER); setChanged(); }
    @Override public long getMaxPower() { return MAX_POWER; }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }
    public net.neoforged.neoforge.fluids.capability.IFluidHandler fluidHandler(@Nullable Direction side) { return cryogel; }
}
