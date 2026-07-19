package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.ElectricHeaterBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** HE proxy living in the front cell where the item was placed. */
public final class ElectricHeaterProxyBlockEntity extends BlockEntity implements HeReceiverProxy {
    public ElectricHeaterProxyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HEATER_ELECTRIC_PROXY.get(), pos, state);
    }

    @Override
    @Nullable public ElectricHeaterBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof ElectricHeaterBlock)) return null;
        BlockPos core = ElectricHeaterBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof ElectricHeaterBlockEntity heater ? heater : null;
    }

    @Override public boolean canConnect(Direction side) { return side != null && target() != null; }
}
