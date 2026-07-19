package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.FensuBlock;
import com.hbm.ntm.energy.HeConductor;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** One of the six bottom FEnSU power cells, delegating HE storage to the core. */
public final class FensuProxyBlockEntity extends BlockEntity implements HeConductor, HeReceiverProxy {
    public FensuProxyBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_BATTERY_REDD_PROXY.get(), position, state);
    }

    @Override
    @Nullable
    public FensuBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof FensuBlock)) return null;
        BlockPos core = FensuBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof FensuBlockEntity fensu ? fensu : null;
    }

    @Override public boolean canConnect(Direction side) { return FensuBlock.canConnectAt(getBlockState(), side); }
    @Override public void setPower(long value) { }
    @Override public boolean allowDirectProvision() { return false; }
}
