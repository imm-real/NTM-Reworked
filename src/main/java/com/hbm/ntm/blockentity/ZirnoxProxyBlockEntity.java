package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.ZirnoxBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class ZirnoxProxyBlockEntity extends BlockEntity {
    public ZirnoxProxyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REACTOR_ZIRNOX_PROXY.get(), pos, state);
    }

    @Nullable public ZirnoxBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof ZirnoxBlock)) return null;
        return level.getBlockEntity(ZirnoxBlock.corePosition(worldPosition, getBlockState()))
                instanceof ZirnoxBlockEntity reactor ? reactor : null;
    }

    @Nullable public IFluidHandler fluidHandler(@Nullable Direction side) {
        ZirnoxBlockEntity reactor = target();
        return reactor != null && ZirnoxBlock.canConnectAt(getBlockState(), side)
                ? reactor.fluidHandler() : null;
    }
}
