package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.AirIntakeBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/** Power and fluid proxy used by the intake's three dummy cells. */
public final class AirIntakeProxyBlockEntity extends BlockEntity implements HeReceiverProxy {
    public AirIntakeProxyBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_INTAKE_PROXY.get(), position, state);
    }

    @Override
    @Nullable public AirIntakeBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof AirIntakeBlock)) return null;
        BlockPos core = AirIntakeBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof AirIntakeBlockEntity intake ? intake : null;
    }

    @Nullable public IFluidHandler fluidHandler(@Nullable Direction side) {
        AirIntakeBlockEntity intake = target();
        return intake != null && side != null && side.getAxis().isHorizontal()
                ? intake.outputHandler() : null;
    }

    @Override public boolean canConnect(Direction side) {
        return side != null && side.getAxis().isHorizontal() && target() != null;
    }
}
