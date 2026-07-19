package com.hbm.ntm.nuclear;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.entity.B92BeamEntity;
import com.hbm.ntm.entity.B93BeamEntity;
import com.hbm.ntm.entity.BlackHoleEntity;
import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.radiation.RadiationSystem;
import com.hbm.ntm.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class NuclearExplosionEntity extends Entity {
    private int strength;
    private int speed;
    private int length;
    // Fallout on by default. Custom nukes may request the diet version.
    private boolean fallout = true;
    private int falloutAdd = 0;
    private NuclearRayExplosion explosion;

    public NuclearExplosionEntity(EntityType<? extends NuclearExplosionEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static NuclearExplosionEntity create(ServerLevel level, int radius, double x, double y, double z) {
        if (radius == 0) radius = 25;
        int strength = radius * 2;
        NuclearExplosionEntity entity = new NuclearExplosionEntity(ModEntities.NUCLEAR_EXPLOSION.get(), level);
        entity.strength = strength;
        entity.speed = Mth.ceil(100_000.0D / strength);
        entity.length = strength / 2;
        entity.setPos(x, y, z);
        return entity;
    }

    /** Terrain damage without the complimentary radiation package. */
    public static NuclearExplosionEntity createNoRad(ServerLevel level, int radius, double x, double y, double z) {
        NuclearExplosionEntity entity = create(level, radius, x, y, z);
        entity.fallout = false;
        return entity;
    }

    /** Add more weather nobody asked for. */
    public NuclearExplosionEntity moreFallout(int extraFallout) {
        this.falloutAdd = extraFallout;
        return this;
    }

    public static void spawnFatMan(ServerLevel level, double x, double y, double z) {
        spawnLargeNuke(level, x, y, z, HbmConfig.FAT_MAN_RADIUS.get(), false);
    }

    /** N-squared: sound, clean-ish MK5 blast, mushroom cloud, still very fatal. */
    public static void spawnN2Mine(ServerLevel level, double x, double y, double z, int radius) {
        level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS, 1.0F, level.random.nextFloat() * 0.1F + 0.9F);
        NuclearExplosionEntity explosion = createNoRad(level, radius, x, y, z);
        level.addFreshEntity(explosion);
        MushroomCloudEntity cloud = new MushroomCloudEntity(ModEntities.MUSHROOM_CLOUD.get(), level);
        cloud.setPos(x, y, z);
        cloud.configure(radius);
        level.addFreshEntity(cloud);
    }

    public static void spawnLargeNuke(ServerLevel level, double x, double y, double z,
                                      int radius, boolean littleBoyCloud) {
        level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS, 1.0F, level.random.nextFloat() * 0.1F + 0.9F);
        NuclearExplosionEntity explosion = create(level, radius, x, y, z);
        level.addFreshEntity(explosion);
        MushroomCloudEntity cloud = new MushroomCloudEntity(ModEntities.MUSHROOM_CLOUD.get(), level);
        cloud.setPos(x, littleBoyCloud ? y + 0.5D : y, z);
        if (littleBoyCloud) cloud.configureScale(1.5F);
        else cloud.configure(radius);
        level.addFreshEntity(cloud);
    }

    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) { }

    @Override
    public void tick() {
        if (strength == 0) {
            discard();
            return;
        }
        if (level().isClientSide) return;
        ServerLevel server = (ServerLevel) level();
        server.getChunkAt(blockPosition());

        // MK5 never ages here, so flash radiation repeats until the terrain work clocks out.
        if (fallout && strength >= 75) radiate(server, 2_500_000.0F, length * 2.0D);
        dealDamage(server, length * 2.0D);

        if (explosion == null) {
            explosion = new NuclearRayExplosion(server, Mth.floor(getX()), Mth.floor(getY()), Mth.floor(getZ()),
                    strength, speed, length);
        }
        if (!explosion.isComplete()) {
            explosion.cacheTick(HbmConfig.MK5_BLAST_TIME.get());
            explosion.destructionTick(HbmConfig.MK5_BLAST_TIME.get());
            return;
        }

        if (fallout) {
            FalloutRainEntity falloutRain = new FalloutRainEntity(ModEntities.FALLOUT_RAIN.get(), server);
            falloutRain.setPos(getX(), getY(), getZ());
            falloutRain.setScale((int) (length * 2.5D + falloutAdd) * HbmConfig.FALLOUT_RANGE.get() / 100);
            server.addFreshEntity(falloutRain);
        }
        discard();
    }

    private void dealDamage(ServerLevel level, double radius) {
        List<Entity> entities = level.getEntities(this, new AABB(getX(), getY(), getZ(), getX(), getY(), getZ())
                .inflate(radius));
        Vec3 origin = position();
        for (Entity entity : entities) {
            if (!entity.isAlive() || isNuclearProcessEntity(entity)
                    || entity instanceof Player player && player.isCreative()) continue;
            double distance = entity.distanceTo(this);
            if (distance > radius) continue;
            Vec3 target = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ());
            if (level.clip(new ClipContext(origin, target, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, entity)).getType() != HitResult.Type.MISS) continue;

            float damage = (float) (250.0D * (radius - distance) / radius);
            if (entity instanceof LivingEntity living) living.invulnerableTime = 0;
            boolean hurt = entity.hurt(level.damageSources().source(ModDamageTypes.NUCLEAR_BLAST, this), damage);
            entity.igniteForSeconds(5.0F);
            if (hurt) {
                Vec3 knock = target.subtract(origin).normalize().scale(0.2D);
                entity.setDeltaMovement(entity.getDeltaMovement().add(knock));
            }
        }
    }

    public static boolean isNuclearProcessEntity(Entity entity) {
        return entity instanceof NuclearExplosionEntity
                || entity instanceof FleijaExplosionEntity
                || entity instanceof FleijaRainbowCloudEntity
                || entity instanceof MushroomCloudEntity
                || entity instanceof FalloutRainEntity
                || entity instanceof B92BeamEntity
                || entity instanceof B93BeamEntity
                || entity instanceof BlackHoleEntity;
    }

    private void radiate(ServerLevel level, float rads, double range) {
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(getX(), getY(), getZ(), getX(), getY(), getZ()).inflate(range));
        Vec3 origin = position();
        for (LivingEntity entity : entities) {
            Vec3 vector = new Vec3(entity.getX() - getX(), entity.getEyeY() - getY(), entity.getZ() - getZ());
            double distance = vector.length();
            if (distance <= 0.0D || distance > range) continue;
            Vec3 direction = vector.normalize();
            float resistance = 0.0F;
            for (int i = 1; i < distance; i++) {
                BlockPos pos = BlockPos.containing(origin.add(direction.scale(i)));
                resistance += level.getBlockState(pos).getBlock().getExplosionResistance();
            }
            resistance = Math.max(1.0F, resistance);
            float dose = rads / resistance / (float) (distance * distance);
            RadiationSystem.contaminate(entity, dose, true);
        }
    }

    public int strength() { return strength; }
    public int speed() { return speed; }
    public int length() { return length; }
    /** Whether the cloud sends radioactive thank-you notes afterward. */
    public boolean fallout() { return fallout; }
    public NuclearRayExplosion rayExplosion() { return explosion; }

    @Override
    public void remove(RemovalReason reason) {
        if (explosion != null && reason != RemovalReason.DISCARDED) explosion.cancel();
        super.remove(reason);
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) { strength = 0; }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putInt("ticksExisted", tickCount); }
}
