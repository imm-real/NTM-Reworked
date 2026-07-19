package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.LandmineBlockEntity;
import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.explosion.MineExplosion;
import com.hbm.ntm.network.MineBlastPayload;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Five exciting new reasons to watch where you step. */
public final class LandmineBlock extends Block implements EntityBlock {
    /** Stops a mine from becoming offended by its own removal. */
    public static boolean safeMode = false;

    public enum Type {
        AP(1.5D, 1.0D, Block.box(5, 0, 5, 11, 1, 11)),
        HE(2.0D, 5.0D, Block.box(4, 0, 4, 12, 2, 12)),
        SHRAP(1.5D, 1.0D, Block.box(5, 0, 5, 11, 1, 11)),
        FAT(2.5D, 1.0D, Block.box(5, 0, 4, 11, 6, 12)),
        // Naval mine forgot to shrink. Nobody volunteered to tell it.
        NAVAL(2.5D, 1.0D, Shapes.block());

        public final double range;
        public final double height;
        public final VoxelShape shape;

        Type(double range, double height, VoxelShape shape) {
            this.range = range;
            this.height = height;
            this.shape = shape;
        }
    }

    private final Type type;

    public LandmineBlock(Properties properties, Type type) {
        super(properties);
        this.type = type;
    }

    public Type type() {
        return type;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LandmineBlockEntity(pos, state);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> beType) {
        if (level.isClientSide()) {
            return null;
        }
        return beType == ModBlockEntities.LANDMINE.get()
                ? (BlockEntityTicker<T>) (BlockEntityTicker<LandmineBlockEntity>) LandmineBlockEntity::serverTick
                : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return type.shape;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return type.shape;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        return belowState.isFaceSturdy(level, below, Direction.UP) || belowState.is(BlockTags.FENCES);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) {
            return;
        }
        net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) level;
        if (level.hasNeighborSignal(pos)) {
            explode(serverLevel, pos);
            return;
        }
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        boolean supported = belowState.isFaceSturdy(level, below, Direction.UP)
                || belowState.is(BlockTags.FENCES);
        if (!supported) {
            if (!safeMode) {
                explode(serverLevel, pos);
            } else {
                level.removeBlock(pos, false);
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !safeMode && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            explode(serverLevel, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** The rare landmine interaction that ends with an item drop. */
    public void defuse(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        safeMode = true;
        level.removeBlock(pos, false);

        ItemStack stack = new ItemStack(this);
        RandomSource rand = level.random;
        float f = rand.nextFloat() * 0.6F + 0.2F;
        float f1 = rand.nextFloat() * 0.2F;
        float f2 = rand.nextFloat() * 0.6F + 0.2F;
        ItemEntity item = new ItemEntity(level, pos.getX() + f, pos.getY() + f1 + 1, pos.getZ() + f2, stack);
        float f3 = 0.05F;
        item.setDeltaMovement(rand.nextGaussian() * f3, rand.nextGaussian() * f3 + 0.2F, rand.nextGaussian() * f3);
        level.addFreshEntity(item);

        safeMode = false;
    }

    /** Remove first, ask which flavor of death second. */
    public void explode(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        double cx = x + 0.5D;
        double cy = y + 0.5D;
        double cz = z + 0.5D;

        safeMode = true;
        level.removeBlock(pos, false);
        safeMode = false;

        switch (type) {
            case AP -> {
                float dmg = HbmConfig.MINE_AP_DAMAGE.get().floatValue();
                MineExplosion.blastEntities(level, cx, cy, cz, 3.0F, dmg, 0.5D, 5.0F, 0.2F, 1.0F);
                sendCloud(level, cx, cy, cz, 5, 0.5F);
            }
            case HE -> {
                float dmg = HbmConfig.MINE_HE_DAMAGE.get().floatValue();
                MineExplosion.blastEntities(level, cx, cy, cz, 4.0F, dmg, 1.0D, 15.0F, 0.2F, 1.0F);
                MineExplosion.blastBlocks(level, cx, cy, cz, 4.0F, 16, false);
                sendCloud(level, cx, cy, cz, 15, 1.25F);
            }
            case SHRAP -> {
                float dmg = HbmConfig.MINE_SHRAP_DAMAGE.get().floatValue();
                MineExplosion.blastEntities(level, cx, cy, cz, 3.0F, dmg, 0.5D, 0.0F, 0.0F, 1.0F);
                sendCloud(level, cx, cy, cz, 5, 0.5F);
                MineExplosion.spawnShrapnelShower(level, cx, cy, cz, 0.0D, 1.0D, 0.0D, 45, 0.2D);
                MineExplosion.spawnShrapnels(level, cx, cy, cz, 5);
            }
            case NAVAL -> {
                float dmg = HbmConfig.MINE_NAVAL_DAMAGE.get().floatValue();
                // Naval blast is five blocks off-center. Naval navigation, apparently.
                double ox = x + 5.0D;
                double oy = y + 5.0D;
                double oz = z + 5.0D;
                MineExplosion.blastEntities(level, ox, oy, oz, 25.0F, dmg, 0.5D, 5.0F, 0.2F, 1.0F);
                MineExplosion.blastBlocks(level, ox, oy, oz, 25.0F, 32, true);
                sendCloud(level, ox, oy, oz, 10, 0.5F);
                // TODO radial smoke, preferably the expensive kind
                MineExplosion.spawnRubble(level, cx, cy, cz, 5);
                // TODO foam splash when the sea comes back
            }
            case FAT -> {
                float dmg = HbmConfig.MINE_NUKE_DAMAGE.get().floatValue();
                MineExplosion.blastEntities(level, cx, cy, cz, 10.0F, dmg, 2.0D, 0.0F, 0.0F, 1.5F);
                MineExplosion.blastBlocks(level, cx, cy, cz, 10.0F, 64, false);
                MineExplosion.incrementRad(level, x, y, z, 1.5F);
                // TODO tiny nuclear flash, normal sentence
                level.playSound(null, cx, cy, cz, ModSounds.MUKE_EXPLOSION.get(), SoundSource.BLOCKS,
                        25.0F, 0.9F);
            }
        }
    }

    private static void sendCloud(net.minecraft.server.level.ServerLevel level, double x, double y, double z,
                                  int cloudCount, float cloudSpeed) {
        PacketDistributor.sendToPlayersNear(level, null, x, y, z, 200.0D,
                new MineBlastPayload(x, y, z, cloudCount, cloudSpeed));
    }
}
