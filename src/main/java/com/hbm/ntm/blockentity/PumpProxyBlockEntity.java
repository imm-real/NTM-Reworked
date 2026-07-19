package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.PumpBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/** One of four outward-facing ports shared by both pumps. */
public final class PumpProxyBlockEntity extends BlockEntity implements HeReceiverProxy {
    public PumpProxyBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.PUMP_PROXY.get(), position, state);
    }

    @Override
    @Nullable
    public PumpBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof PumpBlock)) return null;
        BlockPos core = PumpBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof PumpBlockEntity pump ? pump : null;
    }

    @Nullable
    public IFluidHandler fluidHandler(@Nullable Direction side) {
        PumpBlockEntity pump = target();
        return pump != null && PumpBlock.canConnectAt(getBlockState(), side) ? pump.portFluidHandler() : null;
    }

    @Override public boolean canConnect(Direction side) {
        PumpBlockEntity pump = target();
        return pump != null && pump.electric() && PumpBlock.canConnectAt(getBlockState(), side);
    }
    @Override public long transferPower(long power) {
        PumpBlockEntity pump = target();
        return pump == null || !canConnect(PumpBlock.outward(getBlockState())) ? power : pump.transferPower(power);
    }
}
