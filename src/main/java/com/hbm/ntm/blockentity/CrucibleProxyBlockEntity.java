package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.CrucibleBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class CrucibleProxyBlockEntity extends InventoryProxyBlockEntity<CrucibleBlockEntity> {
    public CrucibleProxyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_CRUCIBLE_PROXY.get(), pos, state);
    }

    @Override
    @Nullable public CrucibleBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof CrucibleBlock)) return null;
        BlockPos core = CrucibleBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof CrucibleBlockEntity crucible ? crucible : null;
    }
}
