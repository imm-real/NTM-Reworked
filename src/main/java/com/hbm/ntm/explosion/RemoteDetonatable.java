package com.hbm.ntm.explosion;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Something a remote detonator is allowed to make everybody regret. */
public interface RemoteDetonatable {
    DetonationResult detonateRemotely(ServerLevel level, BlockPos position);
}
