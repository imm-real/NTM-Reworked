package com.hbm.ntm.block;

import com.hbm.ntm.entity.PrimedExplosiveEntity;
import net.minecraft.server.level.ServerLevel;

public interface PrimedExplosiveBlock {
    void detonatePrimed(ServerLevel level, double x, double y, double z, PrimedExplosiveEntity entity);
}
