package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.TurbofanBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/** One of the Turbofan's four combined fuel/output-fluid and HE ports. */
public final class TurbofanProxyBlockEntity extends BlockEntity implements HeProviderProxy {
    public TurbofanProxyBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_TURBOFAN_PROXY.get(), position, state);
    }

    @Override
    @Nullable
    public TurbofanBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof TurbofanBlock)) return null;
        BlockPos core = TurbofanBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof TurbofanBlockEntity turbofan ? turbofan : null;
    }

    @Nullable
    public IFluidHandler fluidHandler(@Nullable Direction side) {
        TurbofanBlockEntity target = target();
        return target != null && TurbofanBlock.canConnectAt(getBlockState(), side)
                ? target.portFluidHandler() : null;
    }

    @Override
    public boolean canConnect(Direction side) {
        return TurbofanBlock.canConnectAt(getBlockState(), side);
    }
}
