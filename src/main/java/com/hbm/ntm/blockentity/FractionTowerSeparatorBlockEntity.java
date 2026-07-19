package com.hbm.ntm.blockentity;

import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Inert anchor whose only job is standing there for the renderer. */
public final class FractionTowerSeparatorBlockEntity extends BlockEntity {
    public FractionTowerSeparatorBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.FRACTION_SPACER.get(), position, state);
    }
}
