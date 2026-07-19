package com.hbm.ntm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Digamma ash. Tracks a storm nobody can currently see. */
public final class DigammaAshBlock extends ColoredFallingBlock {
    private static final int UNPROTECTED_LIMIT = 244;

    private static int clientAshes;
    private static int clientExposureLimit = UNPROTECTED_LIMIT;

    public DigammaAshBlock(ColorRGBA dustColor, Properties properties) {
        super(dustColor, properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (random.nextInt(25) == 0 && clientAshes < clientExposureLimit) {
            clientAshes++;
        }
    }

    /** Increase doom, then let it decay. */
    public static void clientTick(int exposureLimit) {
        clientExposureLimit = exposureLimit;
        if (clientAshes > 256) clientAshes = 256;
        if (clientAshes > 0) clientAshes -= 2;
        if (clientAshes < 0) clientAshes = 0;
    }

    public static int clientAshes() {
        return clientAshes;
    }
}
