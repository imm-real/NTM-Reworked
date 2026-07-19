package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.HeatExchangerBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/** One of four heat-exchanger corners pretending to be a fluid port. */
public final class HeatExchangerProxyBlockEntity extends BlockEntity {
    public HeatExchangerProxyBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.HEATER_HEATEX_PROXY.get(), position, state);
    }

    @Nullable public HeatExchangerBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof HeatExchangerBlock)) return null;
        BlockPos core = HeatExchangerBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof HeatExchangerBlockEntity exchanger ? exchanger : null;
    }

    @Nullable public IFluidHandler fluidHandler(@Nullable Direction side) {
        HeatExchangerBlockEntity target = target();
        return target != null && HeatExchangerBlock.canConnectAt(getBlockState(), side)
                ? target.fluidHandler() : null;
    }
}
