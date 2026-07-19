package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.SolderingStationBlock;
import com.hbm.ntm.energy.HeConnector;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class SolderingStationProxyBlockEntity extends InventoryProxyBlockEntity<SolderingStationBlockEntity>
        implements HeConnector {
    public SolderingStationProxyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_SOLDERING_STATION_PROXY.get(), pos, state);
    }

    @Override
    @Nullable public SolderingStationBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof SolderingStationBlock)) return null;
        BlockPos core = SolderingStationBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof SolderingStationBlockEntity station ? station : null;
    }
    public IFluidHandler fluidHandler() { SolderingStationBlockEntity t = target(); return t == null ? null : t.fluidHandler(); }
    @Override public boolean canConnect(Direction side) { return SolderingStationBlock.canConnectAt(getBlockState(), side); }
}
