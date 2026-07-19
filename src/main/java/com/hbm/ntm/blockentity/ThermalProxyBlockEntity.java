package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.ThermalMultiblockBlock;
import com.hbm.ntm.energy.HeConnector;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class ThermalProxyBlockEntity extends InventoryProxyBlockEntity<WorldlyContainer>
        implements HeConnector {
    public ThermalProxyBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.THERMAL_PROXY.get(), position, state);
    }

    @Override
    @Nullable
    protected WorldlyContainer target() {
        if (level == null || !(getBlockState().getBlock() instanceof ThermalMultiblockBlock)) {
            return null;
        }
        BlockPos core = ThermalMultiblockBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof WorldlyContainer container ? container : null;
    }

    @Override
    public boolean canConnect(Direction side) {
        BlockState state = getBlockState();
        if (side == null || !ThermalMultiblockBlock.isStirlingPowerPort(state)) {
            return false;
        }
        BlockPos core = ThermalMultiblockBlock.corePosition(worldPosition, state);
        return side.getStepX() == worldPosition.getX() - core.getX()
                && side.getStepY() == 0
                && side.getStepZ() == worldPosition.getZ() - core.getZ();
    }

    @Nullable
    public IFluidHandler smokeHandler(@Nullable Direction side) {
        if (level == null || !(getBlockState().getBlock() instanceof ThermalMultiblockBlock block)
                || (block.kind() != ThermalMultiblockBlock.Kind.FIREBOX
                && block.kind() != ThermalMultiblockBlock.Kind.HEATING_OVEN)) {
            return null;
        }
        BlockPos core = ThermalMultiblockBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof FireboxBlockEntity firebox
                ? firebox.smokeHandler(side) : null;
    }

}
