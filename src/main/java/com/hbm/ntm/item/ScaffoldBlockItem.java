package com.hbm.ntm.item;

import com.hbm.ntm.block.ScaffoldBlock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.Block;

/** Carries scaffold orientation in two tiny component bits. */
public final class ScaffoldBlockItem extends BlockItem {
    public ScaffoldBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack create(Item item, ScaffoldBlock.Variant variant, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY.with(ScaffoldBlock.VARIANT, variant));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(variant.legacyMetadata()));
        return stack;
    }

    public static ScaffoldBlock.Variant variant(ItemStack stack) {
        ScaffoldBlock.Variant variant = stack.getOrDefault(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY).get(ScaffoldBlock.VARIANT);
        return variant == null ? ScaffoldBlock.Variant.STEEL : variant;
    }
}
