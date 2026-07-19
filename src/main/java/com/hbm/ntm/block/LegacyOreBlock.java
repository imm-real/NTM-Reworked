package com.hbm.ntm.block;

import com.hbm.ntm.item.OreChunkItem;
import com.hbm.ntm.item.DepthRockTool;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;

/** Drop rules for the 1.7.10 ore, cluster, Gneiss and depth families. */
public class LegacyOreBlock extends Block {
    private final Drop drop;
    private final boolean depthRock;

    public LegacyOreBlock(Properties properties, Drop drop) {
        this(properties, drop, false);
    }

    public LegacyOreBlock(Properties properties, Drop drop, boolean depthRock) {
        super(properties);
        this.drop = drop;
        this.depthRock = depthRock;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos position) {
        if (!depthRock) return super.getDestroyProgress(state, player, level, position);
        return player.getMainHandItem().getItem() instanceof DepthRockTool ? 1.0F / 50.0F : 0.0F;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack tool = params.getOptionalParameter(LootContextParams.TOOL);
        int fortune = 0;
        if (tool != null && !tool.isEmpty()) {
            var enchantments = params.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            if (EnchantmentHelper.getItemEnchantmentLevel(
                    enchantments.getOrThrow(Enchantments.SILK_TOUCH), tool) > 0) {
                return survivesExplosion(params) ? List.of(new ItemStack(this)) : List.of();
            }
            fortune = EnchantmentHelper.getItemEnchantmentLevel(
                    enchantments.getOrThrow(Enchantments.FORTUNE), tool);
        }
        if (!survivesExplosion(params)) return List.of();

        int count = drop.minimum + params.getLevel().getRandom().nextInt(drop.maximum - drop.minimum + 1);
        if (fortune > 0 && drop.fortune) {
            count *= Math.max(params.getLevel().getRandom().nextInt(fortune + 2) - 1, 0) + 1;
        }
        if (drop.alexandriteCap) count = Math.min(count, 2);

        if (drop.chunk != null) {
            return List.of(OreChunkItem.create(ModItems.CHUNK_ORE.get(), drop.chunk, count));
        }
        String itemId = drop.itemId;
        if ("#nether_fire".equals(itemId)) {
            itemId = netherFireDrop(params.getLevel().getRandom().nextInt(10));
        }
        Item item = itemId == null ? BuiltInRegistries.ITEM.get(BuiltInRegistries.BLOCK.getKey(this))
                : BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        if (item == Items.AIR) return List.of();
        return List.of(new ItemStack(item, count));
    }

    static String netherFireDrop(int roll) {
        return roll == 0 ? "hbm:ingot_phosphorus" : "hbm:powder_fire";
    }

    private static boolean survivesExplosion(LootParams.Builder params) {
        Float radius = params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS);
        return radius == null || radius <= 0.0F || params.getLevel().getRandom().nextFloat() <= 1.0F / radius;
    }

    public record Drop(String itemId, int minimum, int maximum, boolean fortune,
                       OreChunkItem.ChunkType chunk, boolean alexandriteCap) {
        public static Drop self() { return new Drop(null, 1, 1, false, null, false); }
        public static Drop item(String id) { return item(id, 1, 1, true); }
        public static Drop item(String id, int min, int max, boolean fortune) {
            return new Drop("hbm:" + id, min, max, fortune, null, false);
        }
        public static Drop vanilla(String id, int min, int max, boolean fortune) {
            return new Drop("minecraft:" + id, min, max, fortune, null, false);
        }
        public static Drop chunk(OreChunkItem.ChunkType type) {
            return new Drop(null, 1, 1, true, type, false);
        }
        public static Drop alexandrite() {
            return new Drop("hbm:gem_alexandrite", 1, 1, true, null, true);
        }
        public static Drop netherFire() { return new Drop("#nether_fire", 1, 1, true, null, false); }
    }
}
