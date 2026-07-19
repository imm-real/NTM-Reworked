package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.CrackingTowerBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/** One of eight cracking-tower holes authorized to handle fluids. */
public final class CrackingTowerProxyBlockEntity extends BlockEntity {
    public CrackingTowerProxyBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_CATALYTIC_CRACKER_PROXY.get(), position, state);
    }

    @Nullable public CrackingTowerBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof CrackingTowerBlock)) return null;
        BlockPos core = CrackingTowerBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof CrackingTowerBlockEntity tower ? tower : null;
    }

    @Nullable public IFluidHandler fluidHandler(@Nullable Direction side) {
        CrackingTowerBlockEntity target = target();
        return target != null && CrackingTowerBlock.canConnectAt(getBlockState(), side)
                ? target.fluidHandler() : null;
    }
}
