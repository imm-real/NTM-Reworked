package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.FluidUtilityBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.ror.RorFunctionException;
import com.hbm.ntm.ror.RorInteractive;
import com.hbm.ntm.ror.RorValueProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class FluidUtilityBlockEntity extends FluidDuctBlockEntity
        implements RorValueProvider, RorInteractive {
    private long counter;
    private long deltaTick;
    private long deltaSecond;
    private long deltaLastSecond;
    private long currentTick;
    private long sampleTime = Long.MIN_VALUE;

    public FluidUtilityBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.FLUID_UTILITY.get(), position, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FluidUtilityBlockEntity utility) {
        utility.advance(level.getGameTime());
        if (level.getGameTime() % 25L == 0L) {
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public FluidUtilityBlock.Kind kind() {
        return ((FluidUtilityBlock) getBlockState().getBlock()).kind();
    }

    @Override public boolean routesFluid() {
        return kind() == FluidUtilityBlock.Kind.GAUGE || getBlockState().getValue(FluidUtilityBlock.OPEN);
    }

    @Override protected void recordTransfer(int amount) {
        if (level == null) return;
        advance(level.getGameTime());
        if (kind() == FluidUtilityBlock.Kind.COUNTER) counter += amount;
        if (kind() == FluidUtilityBlock.Kind.GAUGE) currentTick += amount;
        setChanged();
    }

    private void advance(long time) {
        if (sampleTime == Long.MIN_VALUE) {
            sampleTime = time;
            return;
        }
        while (sampleTime < time) {
            sampleTime++;
            deltaTick = currentTick;
            currentTick = 0;
            if (sampleTime % 20L == 0L) {
                deltaLastSecond = deltaSecond;
                deltaSecond = 0;
            }
            deltaSecond += deltaTick;
        }
    }

    public long counter() { return counter; }
    public long deltaTick() { return deltaTick; }
    public long deltaSecond() { return deltaLastSecond; }

    @Override public String[] rorInfo() {
        return switch (kind()) {
            case COUNTER -> new String[]{VALUE_PREFIX + "value", VALUE_PREFIX + "state",
                    FUNCTION_PREFIX + "reset", FUNCTION_PREFIX + "setstate!state"};
            case GAUGE -> new String[]{VALUE_PREFIX + "deltatick", VALUE_PREFIX + "deltasecond"};
            default -> new String[0];
        };
    }

    @Override public String provideRorValue(String name) {
        if (kind() == FluidUtilityBlock.Kind.COUNTER) {
            if ((VALUE_PREFIX + "value").equals(name)) return Long.toString(counter);
            if ((VALUE_PREFIX + "state").equals(name)) return routesFluid() ? "1" : "0";
        } else if (kind() == FluidUtilityBlock.Kind.GAUGE) {
            if ((VALUE_PREFIX + "deltatick").equals(name)) return Long.toString(deltaTick);
            if ((VALUE_PREFIX + "deltasecond").equals(name)) return Long.toString(deltaSecond);
        }
        return null;
    }

    @Override public void runRorFunction(String name, String[] parameters) throws RorFunctionException {
        if (kind() != FluidUtilityBlock.Kind.COUNTER || level == null) return;
        if ((FUNCTION_PREFIX + "reset").equals(name)) {
            counter = 0;
            setChanged();
        } else if ((FUNCTION_PREFIX + "setstate").equals(name) && parameters.length > 0) {
            boolean open = RorInteractive.integer(parameters[0], 0, 1) == 1;
            FluidUtilityBlock.setOpen(level, worldPosition, getBlockState(), open, false);
            setChanged();
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("counter", counter);
        tag.putLong("deltaTick", deltaTick);
        tag.putLong("deltaSecond", deltaSecond);
        tag.putLong("deltaLastSecond", deltaLastSecond);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        counter = tag.getLong("counter");
        deltaTick = tag.getLong("deltaTick");
        deltaSecond = tag.getLong("deltaSecond");
        deltaLastSecond = tag.getLong("deltaLastSecond");
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
