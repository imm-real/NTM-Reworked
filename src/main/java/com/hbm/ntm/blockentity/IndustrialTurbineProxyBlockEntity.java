package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.IndustrialTurbineBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/** Turbine fluid port or rear HE output. */
public final class IndustrialTurbineProxyBlockEntity extends BlockEntity implements HeProviderProxy {
    public IndustrialTurbineProxyBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_INDUSTRIAL_TURBINE_PROXY.get(), position, state);
    }

    @Override
    @Nullable
    public IndustrialTurbineBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof IndustrialTurbineBlock)) return null;
        BlockPos core = IndustrialTurbineBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof IndustrialTurbineBlockEntity turbine ? turbine : null;
    }

    @Nullable
    public IFluidHandler fluidHandler(@Nullable Direction side) {
        IndustrialTurbineBlockEntity target = target();
        return target != null && IndustrialTurbineBlock.canFluidConnectAt(getBlockState(), side)
                ? target.portFluidHandler() : null;
    }

    @Override public boolean canConnect(Direction side) {
        return IndustrialTurbineBlock.canPowerConnectAt(getBlockState(), side);
    }
}
