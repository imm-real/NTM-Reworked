package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.AssemblyMachineBlock;
import com.hbm.ntm.energy.HeConnector;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class AssemblyMachineProxyBlockEntity extends InventoryProxyBlockEntity<AssemblyMachineBlockEntity>
        implements HeConnector {
    public AssemblyMachineProxyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_ASSEMBLY_MACHINE_PROXY.get(), pos, state);
    }

    @Override
    @Nullable
    public AssemblyMachineBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof AssemblyMachineBlock)) return null;
        BlockPos core = AssemblyMachineBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof AssemblyMachineBlockEntity assembler ? assembler : null;
    }

    public IFluidHandler fluidHandler() {
        AssemblyMachineBlockEntity target = target();
        return target == null ? null : target.fluidHandler();
    }

    @Override public boolean canConnect(Direction side) { return AssemblyMachineBlock.canConnectAt(getBlockState(), side); }
}
