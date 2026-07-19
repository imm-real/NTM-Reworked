package com.hbm.ntm.block;

import com.hbm.ntm.explosion.DetonationResult;
import com.hbm.ntm.explosion.ExplosionChaos;
import com.hbm.ntm.explosion.RemoteDetonatable;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Levitation bomb. Remote and redstone detonation work; the EMP sibling still needs
 * its missing EMP blast entity. No block entity, inventory or GUI lurks inside.
 */
public final class LevitationBombBlock extends Block implements RemoteDetonatable {

    public LevitationBombBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                   BlockPos neighborPos, boolean movedByPiston) {
        // Source onNeighborBlockChange: explode when the block is indirectly powered. Detonation is
        // Server presses the button; clients receive the bad news.
        if (!level.isClientSide && level.hasNeighborSignal(pos)) {
            detonate((ServerLevel) level, pos);
        }
    }

    @Override
    public DetonationResult detonateRemotely(ServerLevel level, BlockPos position) {
        // Levitation bomb has no concept of failure.
        detonate(level, position);
        return DetonationResult.DETONATED;
    }

    /** Make noise, remove bomb, relocate the local zip code fifty blocks upward. */
    private void detonate(ServerLevel level, BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        level.playSound(null, x, y, z, ModSounds.GUN_B92_FIRE.get(), SoundSource.BLOCKS,
                5.0F, level.random.nextFloat() * 0.2F + 0.9F);

        level.removeBlock(pos, false);
        ExplosionChaos.floater(level, x, y, z, 15, 50);
        ExplosionChaos.move(level, x, y, z, 15, 0, 50, 0);
    }
}
