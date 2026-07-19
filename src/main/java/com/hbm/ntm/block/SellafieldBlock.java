package com.hbm.ntm.block;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.item.SellafieldBlockItem;
import com.hbm.ntm.radiation.ChunkRadiationData;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;

/** Six stages of Sellafite getting worse in measurable ways. */
public final class SellafieldBlock extends Block {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 5);

    public SellafieldBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 0));
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int radiationLevel = state.getValue(LEVEL);
        if (HbmConfig.ENABLE_CHUNK_RADIATION.get()) {
            ChunkRadiationData.get(level).increment(pos, 0.5F * (radiationLevel + 1));
        }
        if (random.nextInt(radiationLevel == 0 ? 25 : 15) != 0) return;
        level.setBlock(pos, radiationLevel > 0
                ? state.setValue(LEVEL, radiationLevel - 1)
                : ModBlocks.SELLAFIELD_SLAKED.get().defaultBlockState(), Block.UPDATE_ALL);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && entity instanceof LivingEntity living) {
            int radiationLevel = state.getValue(LEVEL);
            LegacyRadiationBlockEffects.refresh(living,
                    radiationLevel < 5 ? radiationLevel : radiationLevel * 2);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        Float radius = params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS);
        if (radius != null && radius > 0.0F
                && params.getLevel().getRandom().nextFloat() > 1.0F / radius) return List.of();
        return List.of(SellafieldBlockItem.create(
                ModItems.SELLAFIELD_ITEM.get(), state.getValue(LEVEL), 1));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return SellafieldBlockItem.create(ModItems.SELLAFIELD_ITEM.get(), state.getValue(LEVEL), 1);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }
}
