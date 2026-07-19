package com.hbm.ntm.mixin.compat.sable;

import com.hbm.ntm.block.BombMultiBlock;
import com.hbm.ntm.block.BombThermoBlock;
import com.hbm.ntm.block.LargeNukeBlock;
import com.hbm.ntm.block.LevitationBombBlock;
import com.hbm.ntm.block.NukeBalefireBlock;
import com.hbm.ntm.block.NukeCustomBlock;
import com.hbm.ntm.block.NukeFleijaBlock;
import com.hbm.ntm.block.NukeManBlock;
import com.hbm.ntm.block.NukeN2Block;
import com.hbm.ntm.block.NukePrototypeBlock;
import com.hbm.ntm.block.NukeSoliniumBlock;
import com.hbm.ntm.compat.SableBombImpactFuse;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** Gives remotely detonatable HBM bombs Sable's native high-speed collision fuse. */
@Mixin({
        BombMultiBlock.class,
        BombThermoBlock.class,
        LargeNukeBlock.class,
        LevitationBombBlock.class,
        NukeBalefireBlock.class,
        NukeCustomBlock.class,
        NukeFleijaBlock.class,
        NukeManBlock.class,
        NukeN2Block.class,
        NukePrototypeBlock.class,
        NukeSoliniumBlock.class
})
public abstract class BombImpactFuseSableMixin
        implements BlockWithSubLevelCollisionCallback, BlockSubLevelAssemblyListener {
    @Override
    public BlockSubLevelCollisionCallback sable$getCallback() {
        return SableBombImpactFuse.INSTANCE;
    }

    @Override
    public void beforeMove(ServerLevel sourceLevel, ServerLevel targetLevel, BlockState state,
                           BlockPos sourcePosition, BlockPos targetPosition) {
        SableBombImpactFuse.beforeMove(sourceLevel, sourcePosition, targetPosition);
    }

    @Override
    public void afterMove(ServerLevel sourceLevel, ServerLevel targetLevel, BlockState state,
                          BlockPos sourcePosition, BlockPos targetPosition) {
        SableBombImpactFuse.afterMove(targetLevel, targetPosition);
    }
}
