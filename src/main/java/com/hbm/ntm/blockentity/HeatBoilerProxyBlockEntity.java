package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.HeatBoilerBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class HeatBoilerProxyBlockEntity extends BlockEntity {
    public HeatBoilerProxyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_BOILER_PROXY.get(), pos, state);
    }

    @Nullable public HeatBoilerBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof HeatBoilerBlock)) return null;
        BlockPos core = HeatBoilerBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof HeatBoilerBlockEntity boiler ? boiler : null;
    }

    @Nullable public IFluidHandler fluidHandler(@Nullable Direction side) {
        Direction outward = HeatBoilerBlock.portSide(getBlockState());
        HeatBoilerBlockEntity boiler = target();
        return boiler != null && (side == null || side == outward) ? boiler.fluidHandler(side) : null;
    }
}
