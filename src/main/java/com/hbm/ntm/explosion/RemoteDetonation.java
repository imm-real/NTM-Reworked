package com.hbm.ntm.explosion;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Server-authoritative, non-chunk-loading entry point shared by all detonator variants. */
public final class RemoteDetonation {
    private RemoteDetonation() {
    }

    public static Attempt trigger(ServerLevel level, BlockPos position) {
        if (!level.hasChunkAt(position)) return Attempt.NO_BOMB;
        if (!(level.getBlockState(position).getBlock() instanceof RemoteDetonatable bomb)) {
            return Attempt.NO_BOMB;
        }
        return new Attempt(true, bomb.detonateRemotely(level, position));
    }

    public record Attempt(boolean compatible, DetonationResult result) {
        private static final Attempt NO_BOMB = new Attempt(false, DetonationResult.ERROR_NO_BOMB);
    }
}
