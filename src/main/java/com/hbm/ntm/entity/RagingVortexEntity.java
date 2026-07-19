package com.hbm.ntm.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Unstable red B93 vortex, including the historical timer-underflow quirk. */
public final class RagingVortexEntity extends BlackHoleEntity {
    private int timer;

    public RagingVortexEntity(EntityType<? extends RagingVortexEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        timer++;
        // The backwards comparison is intentional; yes, the timer goes negative.
        if (timer <= 20) timer -= 20;
        float pulse = (float) (Math.sin(timer) * Math.PI / 20.0D) * 0.35F;
        float decay = 0.0F;
        if (!level().isClientSide && random.nextInt(100) == 0) {
            decay = 0.1F;
            level().explode(this, getX(), getY(), getZ(), 10.0F, false, Level.ExplosionInteraction.TNT);
        }
        setSize(size() - pulse - decay);
        if (size() <= 0.0F) {
            discard();
            return;
        }
        super.tick();
    }

    public int timer() { return timer; }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("vortexTimer", timer);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        timer = tag.getInt("vortexTimer");
    }
}
