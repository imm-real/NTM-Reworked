package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.SteamEngineBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/** One of the engine's three power and fluid ports. */
public final class SteamEngineProxyBlockEntity extends BlockEntity implements HeProviderProxy {
    public SteamEngineProxyBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_STEAM_ENGINE_PROXY.get(), position, state);
    }

    @Override
    @Nullable
    public SteamEngineBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof SteamEngineBlock)) return null;
        BlockPos core = SteamEngineBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof SteamEngineBlockEntity engine ? engine : null;
    }

    @Nullable
    public IFluidHandler fluidHandler(@Nullable Direction side) {
        SteamEngineBlockEntity target = target();
        return target != null && SteamEngineBlock.canConnectAt(getBlockState(), side)
                ? target.portFluidHandler() : null;
    }

    @Override public boolean canConnect(Direction side) {
        return SteamEngineBlock.canConnectAt(getBlockState(), side);
    }
}
