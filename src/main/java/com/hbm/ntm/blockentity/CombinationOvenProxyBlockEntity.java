package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.CombinationOvenBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/** Source ProxyCombo inventory and output-fluid delegation for all dummy cells. */
public final class CombinationOvenProxyBlockEntity extends InventoryProxyBlockEntity<CombinationOvenBlockEntity> {
    public CombinationOvenProxyBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.FURNACE_COMBINATION_PROXY.get(), position, state);
    }

    @Override
    @Nullable public CombinationOvenBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof CombinationOvenBlock)) return null;
        BlockPos core = CombinationOvenBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof CombinationOvenBlockEntity oven ? oven : null;
    }

    @Nullable public IFluidHandler fluidHandler() {
        CombinationOvenBlockEntity oven = target();
        return oven == null ? null : oven.fluidHandler();
    }
}
