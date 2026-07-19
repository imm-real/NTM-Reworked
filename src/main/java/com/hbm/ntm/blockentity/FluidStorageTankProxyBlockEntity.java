package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.FluidStorageTankBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class FluidStorageTankProxyBlockEntity extends BlockEntity {
    public FluidStorageTankProxyBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_FLUIDTANK_PROXY.get(), position, state);
    }

    @Nullable public FluidStorageTankBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof FluidStorageTankBlock)) return null;
        BlockPos core = FluidStorageTankBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof FluidStorageTankBlockEntity tank ? tank : null;
    }

    @Nullable public IFluidHandler fluidHandler(@Nullable Direction side) {
        FluidStorageTankBlockEntity tank = target();
        return tank != null && FluidStorageTankBlock.canConnectAt(getBlockState(), side)
                ? tank.fluidHandler() : null;
    }
}
