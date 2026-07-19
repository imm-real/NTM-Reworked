package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.ChemicalPlantBlock;
import com.hbm.ntm.energy.HeConnector;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class ChemicalPlantProxyBlockEntity extends InventoryProxyBlockEntity<ChemicalPlantBlockEntity>
        implements HeConnector {
    public ChemicalPlantProxyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_CHEMICAL_PLANT_PROXY.get(), pos, state);
    }
    @Override
    @Nullable public ChemicalPlantBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof ChemicalPlantBlock)) return null;
        BlockPos core = ChemicalPlantBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof ChemicalPlantBlockEntity plant ? plant : null;
    }
    public IFluidHandler fluidHandler() { ChemicalPlantBlockEntity t = target(); return t == null ? null : t.fluidHandler(); }
    @Override public boolean canConnect(Direction side) { return ChemicalPlantBlock.canConnectAt(getBlockState(), side); }
}
