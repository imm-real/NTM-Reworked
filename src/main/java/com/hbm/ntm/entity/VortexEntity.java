package com.hbm.ntm.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Temporary blue B93 vortex living for size / 0.0025 ticks. */
public final class VortexEntity extends BlackHoleEntity {
    public VortexEntity(EntityType<? extends VortexEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        setSize(size() - 0.0025F);
        if (size() <= 0.0F) {
            discard();
            return;
        }
        super.tick();
    }
}
