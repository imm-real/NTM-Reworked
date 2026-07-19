package com.hbm.ntm.entity;

import com.hbm.ntm.weapon.WeaponStatusEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Runtime area left by the 40 mm incendiary and phosphorus grenades. */
public final class LingeringFireEntity extends Entity {
    private int duration = 200;
    private double width = 5.0D;
    private double height = 2.0D;
    private Kind kind = Kind.FIRE;

    public LingeringFireEntity(EntityType<? extends LingeringFireEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public LingeringFireEntity(EntityType<? extends LingeringFireEntity> type, ServerLevel level,
                               double x, double y, double z, int duration, boolean phosphorus) {
        this(type, level);
        setPos(x, y, z);
        this.duration = duration;
        this.kind = phosphorus ? Kind.PHOSPHORUS : Kind.FIRE;
    }

    public LingeringFireEntity(EntityType<? extends LingeringFireEntity> type, ServerLevel level,
                               double x, double y, double z, int duration,
                               double width, double height, Kind kind) {
        this(type, level);
        setPos(x, y, z);
        this.duration = duration;
        this.width = width;
        this.height = height;
        this.kind = kind;
    }

    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) { }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (tickCount > duration) {
            discard();
            return;
        }

        double radius = width * 0.5D;
        AABB area = new AABB(getX() - radius, getY(), getZ() - radius,
                getX() + radius, getY() + height, getZ() + radius);
        for (Entity entity : level.getEntities(this, area, Entity::isAlive)) {
            if (entity instanceof LivingEntity living) {
                if (kind == Kind.PHOSPHORUS) WeaponStatusEvents.applyPhosphorus(living, 300);
                else if (kind == Kind.BALEFIRE) WeaponStatusEvents.applyBalefire(living, 100);
                else WeaponStatusEvents.applyFire(living, 60);
            } else {
                entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), 80));
            }
        }

        for (int i = 0; i < 2; i++) {
            double particleRadius = Math.sqrt(random.nextDouble()) * radius;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double px = getX() + Math.cos(angle) * particleRadius;
            double py = getY() + random.nextDouble() * height;
            double pz = getZ() + Math.sin(angle) * particleRadius;
            level.sendParticles(kind == Kind.BALEFIRE
                            ? com.hbm.ntm.registry.ModParticles.FLAMETHROWER_BALEFIRE.get()
                            : kind == Kind.FIRE
                            ? com.hbm.ntm.registry.ModParticles.FLAMETHROWER_FIRE.get()
                            : ParticleTypes.FLAME,
                    px, py, pz, 1, 0.04D, 0.05D, 0.04D, 0.01D);
        }
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        duration = Math.max(1, tag.getInt("Duration"));
        width = tag.contains("Width") ? Math.max(0.1D, tag.getDouble("Width")) : 5.0D;
        height = tag.contains("Height") ? Math.max(0.1D, tag.getDouble("Height")) : 2.0D;
        if (tag.contains("Kind")) {
            int ordinal = tag.getInt("Kind");
            kind = ordinal >= 0 && ordinal < Kind.values().length ? Kind.values()[ordinal] : Kind.FIRE;
        } else kind = tag.getBoolean("Phosphorus") ? Kind.PHOSPHORUS : Kind.FIRE;
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Duration", Math.max(duration - tickCount, 1));
        tag.putDouble("Width", width);
        tag.putDouble("Height", height);
        tag.putInt("Kind", kind.ordinal());
    }

    public enum Kind { FIRE, PHOSPHORUS, BALEFIRE }
}
