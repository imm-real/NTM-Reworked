package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.DfcComponentBlock;
import com.hbm.ntm.dfc.DfcKind;
import com.hbm.ntm.dfc.DfcLaserable;
import com.hbm.ntm.dfc.DfcTank;
import com.hbm.ntm.energy.HeProvider;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class DfcReceiverBlockEntity extends DfcBlockEntity implements HeProvider, DfcLaserable {
    public static final int CRYOGEL_CAPACITY = 64_000;
    private final DfcTank cryogel = new DfcTank(ModFluids.CRYOGEL.get(), CRYOGEL_CAPACITY,
            fluid -> fluid.isSame(ModFluids.CRYOGEL.get()), this::setChanged);
    private long power;
    private long joules;
    private long displayJoules;
    private long lastSync = Long.MIN_VALUE;

    public DfcReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DFC_RECEIVER.get(), pos, state, DfcKind.RECEIVER);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DfcReceiverBlockEntity receiver) {
        if (level instanceof ServerLevel server) receiver.serverTick(server, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        displayJoules = joules;
        power = joules * 5_000L;
        for (Direction direction : Direction.values()) tryProvide(level, pos.relative(direction), direction);
        if (joules > 0L) {
            if (cryogel.amount() >= 20) cryogel.remove(20);
            else {
                level.setBlock(pos, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
                return;
            }
        }
        sync(level, pos, state);
        joules = 0L;
        setChanged();
    }

    @Override public void addDfcEnergy(ServerLevel level, long energy, Direction incomingDirection) {
        Direction facing = getBlockState().getValue(DfcComponentBlock.FACING);
        if (incomingDirection.getOpposite() == facing) {
            joules += energy;
            setChanged();
            return;
        }
        BlockPos pos = getBlockPos();
        level.destroyBlock(pos, false);
        level.explode(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                2.5F, true, Level.ExplosionInteraction.TNT);
    }

    public DfcTank cryogel() { return cryogel; }
    public long joules() { return displayJoules; }

    private void sync(ServerLevel level, BlockPos pos, BlockState state) {
        long signature = power ^ displayJoules ^ ((long) cryogel.amount() << 17);
        if (signature != lastSync || level.getGameTime() % 20L == 0L) {
            lastSync = signature;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override protected int menuValue(int index) {
        return switch (index) {
            case 0 -> (int) power;
            case 1 -> (int) (power >>> 32);
            case 2 -> (int) power;
            case 3 -> (int) (power >>> 32);
            case 7 -> (int) displayJoules;
            case 8 -> (int) (displayJoules >>> 32);
            case 9 -> cryogel.amount();
            case 10 -> FluidIdentifierItem.Selection.CRYOGEL.ordinal();
            default -> 0;
        };
    }

    @Override protected void saveDfcState(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.putLong("power", power);
        tag.putLong("joules", joules);
        tag.putLong("displayJoules", displayJoules);
        tag.put("cryogel", cryogel.save(registries));
    }

    @Override protected void loadDfcState(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        power = tag.getLong("power");
        joules = tag.getLong("joules");
        displayJoules = tag.contains("displayJoules") ? tag.getLong("displayJoules") : joules;
        if (tag.contains("cryogel")) cryogel.load(tag.getCompound("cryogel"), registries);
    }

    @Override public boolean canPlaceItem(int slot, net.minecraft.world.item.ItemStack stack) { return false; }
    @Override public long getPower() { return power; }
    @Override public void setPower(long power) { this.power = power; setChanged(); }
    @Override public long getMaxPower() { return power; }
    @Override public long getProviderSpeed() { return power; }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }
    public net.neoforged.neoforge.fluids.capability.IFluidHandler fluidHandler(@Nullable Direction side) { return cryogel; }
}
