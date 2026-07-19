package com.hbm.ntm.nuclear;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.radiation.ChunkRadiationData;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.Queue;

public final class FalloutRainEntity extends Entity {
    private static final EntityDataAccessor<Integer> SCALE = SynchedEntityData.defineId(
            FalloutRainEntity.class, EntityDataSerializers.INT);
    private final Queue<Long> chunks = new ArrayDeque<>();
    private boolean initialized;
    private int delay;

    public FalloutRainEntity(EntityType<? extends FalloutRainEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        delay = HbmConfig.FALLOUT_DELAY.get();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SCALE, 0);
    }

    public void setScale(int scale) { entityData.set(SCALE, Math.max(0, scale)); }
    public int scale() { return entityData.get(SCALE); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            spawnRain();
            return;
        }
        ServerLevel server = (ServerLevel) level();
        if (!initialized) initializeQueue();
        if (chunks.isEmpty()) {
            discard();
            return;
        }
        if (delay == 0) {
            delay = HbmConfig.FALLOUT_DELAY.get();
            long deadline = System.nanoTime() + HbmConfig.MK5_BLAST_TIME.get() * 1_000_000L;
            while (!chunks.isEmpty() && System.nanoTime() < deadline) {
                processChunk(server, chunks.remove());
            }
        }
        delay--;
    }

    private void initializeQueue() {
        initialized = true;
        int chunkRadius = Mth.ceil(scale() / 16.0D);
        int centerX = Mth.floor(getX()) >> 4;
        int centerZ = Mth.floor(getZ()) >> 4;
        for (int x = centerX - chunkRadius; x <= centerX + chunkRadius; x++) {
            for (int z = centerZ - chunkRadius; z <= centerZ + chunkRadius; z++) {
                double dx = (x * 16 + 8) - getX();
                double dz = (z * 16 + 8) - getZ();
                if (dx * dx + dz * dz <= (double) scale() * scale()) chunks.add(ChunkPos.asLong(x, z));
            }
        }
    }

    private void processChunk(ServerLevel server, long packedChunk) {
        int chunkX = ChunkPos.getX(packedChunk);
        int chunkZ = ChunkPos.getZ(packedChunk);
        if (!server.hasChunk(chunkX, chunkZ)) return;

        double centerX = chunkX * 16.0D + 8.0D;
        double centerZ = chunkZ * 16.0D + 8.0D;
        double normalized = Math.sqrt((centerX - getX()) * (centerX - getX())
                + (centerZ - getZ()) * (centerZ - getZ())) / Math.max(1.0D, scale());
        float ambient = normalized < 0.65D ? 2.5F : 0.5F;
        ChunkRadiationData radiation = ChunkRadiationData.get(server);
        radiation.set(packedChunk, Math.max(radiation.get(packedChunk), ambient));

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = chunkX * 16 + localX;
                int z = chunkZ * 16 + localZ;
                double dx = x + 0.5D - getX();
                double dz = z + 0.5D - getZ();
                if (dx * dx + dz * dz > (double) scale() * scale()) continue;
                double columnNormalized = Math.sqrt(dx * dx + dz * dz) / Math.max(1.0D, scale());
                double chance = Math.max(0.0D, 0.1D - Math.pow(columnNormalized - 0.7D, 2.0D));
                int y = server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                mutable.set(x, y, z);
                BlockState ground = server.getBlockState(mutable.below());
                if (server.random.nextDouble() < chance && server.getBlockState(mutable).canBeReplaced()
                        && ModBlocks.FALLOUT.get().defaultBlockState().canSurvive(server, mutable)) {
                    server.setBlock(mutable, ModBlocks.FALLOUT.get().defaultBlockState(), Block.UPDATE_ALL);
                }
                if (columnNormalized < 0.65D && server.random.nextInt(5) == 0
                        && ground.ignitedByLava() && server.getBlockState(mutable).isAir()) {
                    server.setBlock(mutable, BaseFireBlock.getState(server, mutable), Block.UPDATE_ALL);
                }
                if (columnNormalized <= 0.5D) {
                    convertColumnToSlakedSellafite(server, mutable.below());
                }
            }
        }
    }

    /**
     * Source {@code EntityFalloutRain.stomp}: the inner half of the fallout field processes the
     * first three solid terrain layers. Its material-rock/iron/sand/ground entries all resolve to
     * Slaked Sellafite; block tags stand in for the removed material enum.
     */
    static int convertColumnToSlakedSellafite(ServerLevel level, BlockPos surface) {
        BlockPos.MutableBlockPos cursor = surface.mutable();
        int depth = 0;
        int converted = 0;
        int minimumY = level.getMinBuildHeight();
        for (int scanned = 0; cursor.getY() >= minimumY && depth < 3 && scanned < 64;
             scanned++, cursor.move(0, -1, 0)) {
            BlockState state = level.getBlockState(cursor);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;

            if (convertsToSlakedSellafite(state)) {
                level.setBlock(cursor, ModBlocks.SELLAFIELD_SLAKED.get().defaultBlockState(), Block.UPDATE_ALL);
                depth++;
                converted++;
            } else if (state.canOcclude() && state.isCollisionShapeFullBlock(level, cursor)) {
                depth++;
            }
        }
        return converted;
    }

    static boolean convertsToSlakedSellafite(BlockState state) {
        if (state.is(ModBlocks.SELLAFIELD.get()) || state.is(ModBlocks.SELLAFIELD_SLAKED.get())
                || state.is(Blocks.BEDROCK)) return false;
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                || state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SOUL_SAND)
                || state.is(Blocks.SOUL_SOIL)
                || state.is(Blocks.MUD)
                || state.is(Blocks.PACKED_MUD);
    }

    private void spawnRain() {
        if (level().players().isEmpty() || scale() <= 0) return;
        var player = level().players().getFirst();
        if (player.distanceToSqr(getX(), player.getY(), getZ()) > (double) scale() * scale()) return;
        int radius = level().getLevelData().isRaining() ? 10 : 5;
        for (int i = 0; i < 8; i++) {
            double x = player.getX() + (random.nextDouble() * 2.0D - 1.0D) * radius;
            double y = player.getY() + 6.0D + random.nextDouble() * 4.0D;
            double z = player.getZ() + (random.nextDouble() * 2.0D - 1.0D) * radius;
            level().addParticle(ParticleTypes.WHITE_ASH, x, y, z, 0.0D, -0.03D, 0.0D);
        }
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) { setScale(tag.getInt("scale")); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putInt("scale", scale()); }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return true; }
}
