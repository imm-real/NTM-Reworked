package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.BatterySocketBlock;
import com.hbm.ntm.energy.HeConnector;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class BatterySocketProxyBlockEntity extends InventoryProxyBlockEntity<BatterySocketBlockEntity>
        implements HeConnector {
    private static final int[] SLOT = {0};

    public BatterySocketProxyBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.BATTERY_SOCKET_PROXY.get(), position, state);
    }

    @Override
    @Nullable
    protected BatterySocketBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof BatterySocketBlock)) {
            return null;
        }
        BlockPos core = BatterySocketBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof BatterySocketBlockEntity socket ? socket : null;
    }

    @Override
    public boolean canConnect(Direction side) {
        return side != null && BatterySocketBlock.canConnectAt(getBlockState(), side);
    }

    @Override public int getContainerSize() { return target() == null ? 0 : 1; }
    @Override public int[] getSlotsForFace(Direction direction) { return target() == null ? NO_SLOTS : SLOT; }
}
