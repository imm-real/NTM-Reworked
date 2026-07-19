package com.hbm.ntm.nuclear;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.entity.B92BeamEntity;
import com.hbm.ntm.entity.B93BeamEntity;
import com.hbm.ntm.entity.BlackHoleEntity;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** MK3 FLEIJA terrain-deletion mode used by the B92. */
public final class FleijaExplosionEntity extends Entity {
    private static final TicketType<UUID> CHUNK_TICKET = TicketType.create(
            "hbm_fleija", Comparator.<UUID>naturalOrder());

    private int radius;
    private int speed;
    private int n = 1;
    private int lastX;
    private int lastZ;
    private int centerX;
    private int centerY;
    private int centerZ;
    private int age;
    private boolean expiredOnLoad;
    private ChunkPos ticketedChunk;

    public FleijaExplosionEntity(EntityType<? extends FleijaExplosionEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static FleijaExplosionEntity create(ServerLevel level, double x, double y, double z, int radius) {
        FleijaExplosionEntity explosion = new FleijaExplosionEntity(ModEntities.FLEIJA_EXPLOSION.get(), level);
        explosion.radius = radius;
        explosion.speed = HbmConfig.FLEIJA_BLAST_SPEED.get();
        // The old cast mangles negative coordinates differently. Keep the vintage damage.
        explosion.centerX = (int) x;
        explosion.centerY = (int) y;
        explosion.centerZ = (int) z;
        explosion.setPos(x, y, z);
        return explosion;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) { }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || radius <= 0) return;
        ServerLevel server = (ServerLevel) level();
        if (expiredOnLoad) {
            discard();
            return;
        }
        ensureChunkTicket(server);

        speed++;
        for (int i = 0; i < speed; i++) {
            if (updateExplosion(server)) {
                // Dead, but MK3 insists on finishing the batch.
                discard();
            }
        }

        // FLEIJA forgot its finish flag, so the last tick still hurts. Classic.
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.WEATHER, 10_000.0F, 0.8F + random.nextFloat() * 0.2F);
        dealDamage(server, radius * 2.0D);
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

    private boolean updateExplosion(ServerLevel level) {
        breakColumn(level, lastX, lastZ);
        int shell = (int) Math.floor((Math.sqrt(n) + 1.0D) / 2.0D);
        int shell2 = shell * 2;
        if (shell2 == 0) return true;
        int leg = (int) Math.floor((double) (n - (shell2 - 1) * (shell2 - 1)) / shell2);
        int element = (n - (shell2 - 1) * (shell2 - 1)) - shell2 * leg - shell + 1;
        lastX = leg == 0 ? shell : leg == 1 ? -element : leg == 2 ? -shell : element;
        lastZ = leg == 0 ? element : leg == 1 ? shell : leg == 2 ? -element : -shell;
        n++;
        return n > radius * radius * 4;
    }

    private void breakColumn(ServerLevel level, int x, int z) {
        int distance = radius * radius - (x * x + z * z);
        if (distance <= 0) return;
        distance = (int) Math.sqrt(distance);
        for (int y = distance; y > -distance; y--) {
            BlockPos pos = new BlockPos(centerX + x, centerY + y, centerZ + z);
            // Leave the bottom layer alone. Somebody has to hold the void back.
            if (pos.getY() > level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

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

    public int radius() { return radius; }
    public int speed() { return speed; }
    public int spiralIndex() { return n; }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("age", age);
        tag.putInt("destructionRange", radius);
        tag.putInt("speed", speed);
        tag.putLong("milliTime", System.currentTimeMillis());
        tag.putInt("n", n);
        tag.putInt("lastposX", lastX);
        tag.putInt("lastposZ", lastZ);
        tag.putInt("centerX", centerX);
        tag.putInt("centerY", centerY);
        tag.putInt("centerZ", centerZ);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("age");
        radius = tag.getInt("destructionRange");
        speed = tag.getInt("speed");
        int lifespan = HbmConfig.EXPLOSION_LIFESPAN_SECONDS.get();
        expiredOnLoad = lifespan > 0 && tag.contains("milliTime")
                && System.currentTimeMillis() - tag.getLong("milliTime") > lifespan * 1000L;
        n = tag.getInt("n");
        lastX = tag.getInt("lastposX");
        lastZ = tag.getInt("lastposZ");
        centerX = tag.contains("centerX") ? tag.getInt("centerX") : (int) getX();
        centerY = tag.contains("centerY") ? tag.getInt("centerY") : (int) getY();
        centerZ = tag.contains("centerZ") ? tag.getInt("centerZ") : (int) getZ();
    }
}
