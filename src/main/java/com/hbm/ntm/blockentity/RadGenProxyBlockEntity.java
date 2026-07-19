package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.RadGenBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Source {@code TileEntityProxyCombo(true, true, false)} at the RadGen's three extra cells. */
public final class RadGenProxyBlockEntity extends InventoryProxyBlockEntity<RadGenBlockEntity>
        implements HeProviderProxy {
    public RadGenProxyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_RADGEN_PROXY.get(), pos, state);
    }

    @Override
    @Nullable
    public RadGenBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof RadGenBlock)) return null;
        BlockPos core = RadGenBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof RadGenBlockEntity radGen ? radGen : null;
    }

    @Override public boolean canConnect(Direction side) {
        return RadGenBlock.canConnectPower(getBlockState(), side);
    }
}
