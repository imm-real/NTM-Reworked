package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.FractionTowerBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/** One of four tower cells where fluid may leave but never reconsider. */
public final class FractionTowerProxyBlockEntity extends BlockEntity {
    public FractionTowerProxyBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_FRACTION_TOWER_PROXY.get(), position, state);
    }

    @Nullable public FractionTowerBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof FractionTowerBlock)) return null;
        BlockPos core = FractionTowerBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof FractionTowerBlockEntity tower ? tower : null;
    }

    @Nullable public IFluidHandler fluidHandler(@Nullable Direction side) {
        FractionTowerBlockEntity target = target();
        return target != null && FractionTowerBlock.canConnectAt(getBlockState(), side)
                ? target.fluidHandler() : null;
    }
}
