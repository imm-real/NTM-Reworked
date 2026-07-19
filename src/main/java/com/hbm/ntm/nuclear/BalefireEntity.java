package com.hbm.ntm.nuclear;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.entity.B92BeamEntity;
import com.hbm.ntm.entity.B93BeamEntity;
import com.hbm.ntm.entity.BlackHoleEntity;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Balefire spiral. No fallout rain, just the aggressively green apocalypse. */
public final class BalefireEntity extends Entity {
    private static final TicketType<UUID> CHUNK_TICKET = TicketType.create(
            "hbm_balefire", Comparator.<UUID>naturalOrder());

    private int age = 0;
    private int destructionRange = 0;
    private int speed = 1;
    private boolean did = false;
    private BalefireExplosion exp;
    private ChunkPos ticketedChunk;

    public BalefireEntity(EntityType<? extends BalefireEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static BalefireEntity create(ServerLevel level, double x, double y, double z, int range) {
        BalefireEntity entity = new BalefireEntity(ModEntities.BALEFIRE.get(), level);
        entity.destructionRange = range;
        entity.setPos(x, y, z);
        return entity;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) { }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || destructionRange <= 0) return;
        ServerLevel server = (ServerLevel) level();
        ensureChunkTicket(server);

        if (!did) {
            exp = new BalefireExplosion((int) getX(), (int) getY(), (int) getZ(), server, destructionRange);
            did = true;
        }

        speed += 1; // increase speed to keep up with expansion

        boolean flag = false;
        for (int i = 0; i < this.speed; i++) {
            flag = exp.update();

            if (flag) {
                clearChunkTicket();
                discard();
            }
        }

        if (!flag) {
            // Terrain spiral still moving? Continue hurting everything nearby.
            dealDamage(server, destructionRange * 2.0D);
        }

        age++;
    }

    private void ensureChunkTicket(ServerLevel level) {
        if (!HbmConfig.ENABLE_EXPLOSION_CHUNK_LOADING.get() || ticketedChunk != null) return;
        ticketedChunk = new ChunkPos(blockPosition());
        level.getChunkSource().addRegionTicket(CHUNK_TICKET, ticketedChunk, 0, getUUID());
    }

    private void clearChunkTicket() {
        if (ticketedChunk == null || !(level() instanceof ServerLevel server)) return;
        server.getChunkSource().removeRegionTicket(CHUNK_TICKET, ticketedChunk, 0, getUUID());
        ticketedChunk = null;
    }

    @Override
    public void remove(RemovalReason reason) {
        clearChunkTicket();
        super.remove(reason);
    }

    // 250 max damage. Ocelots, projectiles and creative players have diplomatic immunity.
    private void dealDamage(ServerLevel level, double range) {
        List<Entity> entities = level.getEntities(this, new AABB(position(), position()).inflate(range));
        Vec3 origin = position();
        for (Entity entity : entities) {
            if (!entity.isAlive() || entity instanceof Ocelot || entity instanceof B92BeamEntity
                    || entity instanceof B93BeamEntity || entity instanceof BlackHoleEntity
                    || entity instanceof BulletEntity
                    || entity instanceof Player player && player.isCreative()) continue;
            double distance = entity.distanceTo(this);
            if (distance > range) continue;
            Vec3 target = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ());
            if (level.clip(new ClipContext(origin, target, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, entity)).getType() != HitResult.Type.MISS) continue;

            float damage = (float) (250.0D * (range - distance) / range);
            boolean doKnockback = true;
            if (entity instanceof LivingEntity living) {
                living.invulnerableTime = 0;
                doKnockback = living.hurt(
                        level.damageSources().source(ModDamageTypes.NUCLEAR_BLAST, this), damage);
            } else {
                entity.hurt(level.damageSources().source(ModDamageTypes.NUCLEAR_BLAST, this), damage);
            }
            entity.igniteForSeconds(5.0F);
            if (doKnockback) {
                entity.setDeltaMovement(entity.getDeltaMovement()
                        .add(target.subtract(origin).normalize().scale(0.2D)));
            }
        }
    }

    public int destructionRange() { return destructionRange; }
    public int speed() { return speed; }
    public int spiralIndex() { return exp != null ? exp.spiralIndex() : 0; }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("age", age);
        tag.putInt("destructionRange", destructionRange);
        tag.putInt("speed", speed);
        tag.putBoolean("did", did);
        if (exp != null) exp.saveToNbt(tag, "exp_");
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("age");
        destructionRange = tag.getInt("destructionRange");
        speed = tag.getInt("speed");
        did = tag.getBoolean("did");

        exp = new BalefireExplosion((int) getX(), (int) getY(), (int) getZ(), (ServerLevel) level(), destructionRange);
        exp.readFromNbt(tag, "exp_");
        did = true;
    }
}
