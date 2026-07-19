package com.hbm.ntm.block;

import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Source BlockOutgas behavior for ordinary, Gneiss, and Nether Uranium Ore. */
public final class UraniumOutgassingOreBlock extends LegacyOreBlock {
    public UraniumOutgassingOreBlock(Properties properties, Drop drop) {
        super(properties, drop);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos target = pos.relative(Direction.getRandom(random));
        if (level.getBlockState(target).isAir()) {
            level.setBlock(target, ModBlocks.legacy("gas_radon").get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (!level.isClientSide && level.getBlockState(pos).isAir()) {
            level.setBlock(pos, ModBlocks.legacy("gas_radon").get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
