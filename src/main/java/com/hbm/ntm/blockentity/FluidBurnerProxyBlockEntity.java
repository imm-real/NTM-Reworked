package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.FluidBurnerBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.thermal.HeatSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class FluidBurnerProxyBlockEntity extends BlockEntity implements HeatSource {
    public FluidBurnerProxyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HEATER_OILBURNER_PROXY.get(), pos, state);
    }

    @Nullable public FluidBurnerBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof FluidBurnerBlock)) return null;
        BlockPos core = FluidBurnerBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof FluidBurnerBlockEntity burner ? burner : null;
    }

    @Nullable public IFluidHandler fluidHandler() {
        FluidBurnerBlockEntity burner = target();
        return FluidBurnerBlock.isFluidPort(getBlockState()) && burner != null ? burner.fluidHandler() : null;
    }

    @Override public int getHeatStored() {
        FluidBurnerBlockEntity burner = target();
        return FluidBurnerBlock.isHeatPort(getBlockState()) && burner != null ? burner.getHeatStored() : 0;
    }

    @Override public void useUpHeat(int heat) {
        FluidBurnerBlockEntity burner = target();
        if (FluidBurnerBlock.isHeatPort(getBlockState()) && burner != null) burner.useUpHeat(heat);
    }
}
