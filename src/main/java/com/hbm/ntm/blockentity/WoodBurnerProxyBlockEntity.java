package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.WoodBurnerBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/** Inventory proxy; the lower rear row also forwards HE and fluid. */
public final class WoodBurnerProxyBlockEntity extends InventoryProxyBlockEntity<WoodBurnerBlockEntity>
        implements HeProviderProxy {
    public WoodBurnerProxyBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_WOOD_BURNER_PROXY.get(), position, state);
    }

    @Override
    @Nullable public WoodBurnerBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof WoodBurnerBlock)) return null;
        BlockPos core = WoodBurnerBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof WoodBurnerBlockEntity burner ? burner : null;
    }

    @Nullable public IFluidHandler fluidHandler(@Nullable Direction side) {
        WoodBurnerBlockEntity target = target();
        return target != null && WoodBurnerBlock.canConnectAt(getBlockState(), side)
                ? target.fluidHandler() : null;
    }

    @Override public boolean canConnect(Direction side) {
        return WoodBurnerBlock.canConnectAt(getBlockState(), side);
    }
}
