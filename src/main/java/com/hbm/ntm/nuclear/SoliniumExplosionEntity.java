package com.hbm.ntm.nuclear;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.radiation.RadiationSystem;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** The blue rinse: removes organics, irradiates everything else. */
public final class SoliniumExplosionEntity extends Entity {
    private static final TicketType<UUID> CHUNK_TICKET = TicketType.create(
            "hbm_solinium", Comparator.<UUID>naturalOrder());

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

    public SoliniumExplosionEntity(EntityType<? extends SoliniumExplosionEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static SoliniumExplosionEntity create(ServerLevel level, double x, double y, double z, int radius) {
        SoliniumExplosionEntity explosion = new SoliniumExplosionEntity(ModEntities.SOLINIUM_EXPLOSION.get(), level);
        explosion.radius = radius;
        // Same speed knob as FLEIJA. Do not give this thing its own personality.
        explosion.speed = HbmConfig.FLEIJA_BLAST_SPEED.get();
        // Yes, casts. Negative coordinates get the authentic off-by-one experience.
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
                // Dead, but not done. MK3 finishes the current batch anyway.
                discard();
            }
        }

        // makeSol never sets the finish flag. One last thunder-and-radiation encore.
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.WEATHER, 10_000.0F, 0.8F + random.nextFloat() * 0.2F);
        doRadiation(server, 15_000.0F, 250_000.0F, radius);
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
        // Square spiral. Changing this turns the blue rinse into modern art.
        int shell2 = Math.max(shell * 2, 1);
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
        // No floor guard. Bedrock survives because even this bomb knows it is not a salad.
        for (int y = distance; y > -distance; y--) {
            cleanse(level, new BlockPos(centerX + x, centerY + y, centerZ + z));
        }
    }

    /** Dirt stays dirt-ish, plants stop existing. */
    private static void cleanse(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (block == Blocks.GRASS_BLOCK || block == Blocks.MYCELIUM
                || block == ModBlocks.WASTE_EARTH.get() || block == ModBlocks.WASTE_MYCELIUM.get()) {
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
            return;
        }
        if (isCleansableMaterial(state)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /** 1.21 removed Material, so welcome to tag soup. */
    private static boolean isCleansableMaterial(BlockState state) {
        if (state.is(BlockTags.LEAVES)) return true;                              // Material.leaves
        // Wood and gourds, plus whatever else brought an axe to the tag party.
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) return true;
        if (state.is(BlockTags.CORALS) || state.is(BlockTags.CORAL_BLOCKS)
                || state.is(BlockTags.WALL_CORALS)) return true;                  // Material.coral
        Block block = state.getBlock();
        if (block instanceof BushBlock) return true;                             // Material.plants
        if (block instanceof CactusBlock) return true;                           // Material.cactus
        if (block instanceof SugarCaneBlock) return true;                        // Material.plants (reeds)
        if (block instanceof VineBlock || block instanceof GrowingPlantBlock) return true; // Material.vine
        return state.is(Blocks.SPONGE) || state.is(Blocks.WET_SPONGE);           // Material.sponge
    }

    /** Linear dose, creative contamination. Hazmat politely ignored. */
    private void doRadiation(ServerLevel level, float outer, float inner, double range) {
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(getX(), getY(), getZ(), getX(), getY(), getZ()).inflate(range));
        for (LivingEntity entity : entities) {
            Vec3 vector = new Vec3(getX() - entity.getX(), getY() - entity.getY(), getZ() - entity.getZ());
            double distance = vector.length();
            if (distance > range) continue;
            double interpolation = 1.0D - distance / range;
            float rad = (float) (outer + (inner - outer) * interpolation);
            RadiationSystem.contaminate(entity, rad, true);
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
