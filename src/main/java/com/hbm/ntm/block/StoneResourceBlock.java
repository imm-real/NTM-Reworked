package com.hbm.ntm.block;

import com.hbm.ntm.item.OreChunkItem;
import com.hbm.ntm.item.StoneResourceBlockItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Six stone-resource variants sharing one block and no labels. */
public final class StoneResourceBlock extends Block {
    public static final EnumProperty<Type> TYPE = EnumProperty.create("type", Type.class);

    public StoneResourceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TYPE, Type.HEMATITE));
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        Type type = state.getValue(TYPE);
        if (type != Type.MALACHITE) {
            return List.of(StoneResourceBlockItem.create(ModItems.STONE_RESOURCE_ITEM.get(), type, 1));
        }

        ItemStack tool = params.getOptionalParameter(LootContextParams.TOOL);
        int fortune = 0;
        if (tool != null && !tool.isEmpty()) {
            var enchantments = params.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var silkTouch = enchantments.getOrThrow(Enchantments.SILK_TOUCH);
            if (EnchantmentHelper.getItemEnchantmentLevel(silkTouch, tool) > 0) {
                return List.of(StoneResourceBlockItem.create(ModItems.STONE_RESOURCE_ITEM.get(), type, 1));
            }
            var enchantment = enchantments
                    .getOrThrow(Enchantments.FORTUNE);
            fortune = EnchantmentHelper.getItemEnchantmentLevel(enchantment, tool);
        }
        Float explosionRadius = params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS);
        if (explosionRadius != null && explosionRadius > 0.0F
                && params.getLevel().getRandom().nextFloat() > 1.0F / explosionRadius) {
            return List.of();
        }
        int count = 3 + fortune + params.getLevel().getRandom().nextInt(fortune + 2);
        return List.of(OreChunkItem.create(ModItems.CHUNK_ORE.get(), OreChunkItem.ChunkType.MALACHITE, count));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return StoneResourceBlockItem.create(ModItems.STONE_RESOURCE_ITEM.get(), state.getValue(TYPE), 1);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (!level.isClientSide && state.getValue(TYPE) == Type.ASBESTOS && level.getBlockState(pos).isAir()) {
            level.setBlock(pos, com.hbm.ntm.registry.ModBlocks.legacy("gas_asbestos").get().defaultBlockState(),
                    Block.UPDATE_ALL);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TYPE);
    }

    public enum Type implements StringRepresentable {
        SULFUR("sulfur", 0),
        ASBESTOS("asbestos", 1),
        HEMATITE("hematite", 2),
        MALACHITE("malachite", 3),
        LIMESTONE("limestone", 4),
        BAUXITE("bauxite", 5);

        private final String id;
        private final int legacyMetadata;

        Type(String id, int legacyMetadata) {
            this.id = id;
            this.legacyMetadata = legacyMetadata;
        }

        @Override public String getSerializedName() { return id; }
        public int legacyMetadata() { return legacyMetadata; }

        public static Type byLegacyMetadata(int metadata) {
            for (Type type : values()) if (type.legacyMetadata == metadata) return type;
            return HEMATITE;
        }
    }
}
