package com.hbm.ntm.block;

import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Source BlockOutgas behavior shared by standalone and Gneiss Asbestos Ore. */
public final class AsbestosOreBlock extends LegacyOreBlock {
    public AsbestosOreBlock(Properties properties, Drop drop) {
        super(properties, drop);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos target = pos.relative(Direction.getRandom(random));
        if (level.getBlockState(target).isAir()) {
            level.setBlock(target, ModBlocks.legacy("gas_asbestos").get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (!level.isClientSide && level.getBlockState(pos).isAir()) {
            level.setBlock(pos, ModBlocks.legacy("gas_asbestos").get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        BlockPos above = pos.above();
        if (!level.getBlockState(above).isAir()) return;
        if (!level.isClientSide && level.random.nextInt(10) == 0) {
            level.setBlock(above, ModBlocks.legacy("gas_asbestos").get().defaultBlockState(), Block.UPDATE_ALL);
        }
        if (level.isClientSide) {
            for (int i = 0; i < 5; i++) {
                level.addParticle(ParticleTypes.HAPPY_VILLAGER,
                        pos.getX() + level.random.nextFloat(), pos.getY() + 1.1D,
                        pos.getZ() + level.random.nextFloat(), 0.0D, 0.0D, 0.0D);
            }
        }
    }
}
