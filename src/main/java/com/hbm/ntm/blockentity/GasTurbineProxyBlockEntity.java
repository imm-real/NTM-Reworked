package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.GasTurbineBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/** One of the six useful holes in an otherwise extremely solid turbine. */
public final class GasTurbineProxyBlockEntity extends BlockEntity implements HeProviderProxy {
    public GasTurbineProxyBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_TURBINE_GAS_PROXY.get(), position, state);
    }

    @Override
    @Nullable
    public GasTurbineBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof GasTurbineBlock)) return null;
        BlockPos core = GasTurbineBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof GasTurbineBlockEntity turbine ? turbine : null;
    }

    @Nullable
    public IFluidHandler fluidHandler(@Nullable Direction side) {
        GasTurbineBlockEntity target = target();
        return target != null && GasTurbineBlock.canFluidConnectAt(getBlockState(), side)
                ? target.fluidHandler(GasTurbineBlock.port(getBlockState())) : null;
    }

    @Override public boolean canConnect(Direction side) {
        return GasTurbineBlock.canPowerConnectAt(getBlockState(), side);
    }
}
