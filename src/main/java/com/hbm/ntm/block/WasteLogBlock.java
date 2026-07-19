package com.hbm.ntm.block;

import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;

/** Charred logs hide bark and charcoal. Frozen logs hide snowballs and common sense. */
public final class WasteLogBlock extends Block {
    private final boolean frozen;

    public WasteLogBlock(Properties properties, boolean frozen) {
        super(properties);
        this.frozen = frozen;
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        return !frozen;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        return frozen ? 0 : 20;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        return frozen ? 0 : 5;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack tool = params.getOptionalParameter(LootContextParams.TOOL);
        if (tool != null && !tool.isEmpty()) {
            var enchantments = params.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            if (EnchantmentHelper.getItemEnchantmentLevel(
                    enchantments.getOrThrow(Enchantments.SILK_TOUCH), tool) > 0) {
                return survivesExplosion(params) ? List.of(new ItemStack(this)) : List.of();
            }
        }
        if (!survivesExplosion(params)) return List.of();
        // Roll secret bark before charcoal or the ancient RNG sequence becomes angry.
        if (frozen) {
            return List.of(new ItemStack(Items.SNOWBALL, 2 + params.getLevel().getRandom().nextInt(3)));
        }
        if (params.getLevel().getRandom().nextInt(1000) == 0) {
            return List.of(new ItemStack(ModItems.BURNT_BARK.get()));
        }
        return List.of(new ItemStack(Items.CHARCOAL, 2 + params.getLevel().getRandom().nextInt(3)));
    }

    private static boolean survivesExplosion(LootParams.Builder params) {
        Float radius = params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS);
        return radius == null || radius <= 0.0F
                || params.getLevel().getRandom().nextFloat() <= 1.0F / radius;
    }
}
