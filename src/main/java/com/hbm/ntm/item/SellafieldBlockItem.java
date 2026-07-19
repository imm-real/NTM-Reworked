package com.hbm.ntm.item;

import com.hbm.ntm.block.SellafieldBlock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.Block;

/** Carries all Sellafite stages without multiplying registry entries. */
public final class SellafieldBlockItem extends BlockItem {
    public SellafieldBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack create(Item item, int radiationLevel, int count) {
        int level = Mth.clamp(radiationLevel, 0, 5);
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY.with(SellafieldBlock.LEVEL, level));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(level));
        return stack;
    }

    public static int level(ItemStack stack) {
        Integer level = stack.getOrDefault(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY).get(SellafieldBlock.LEVEL);
        if (level != null) return Mth.clamp(level, 0, 5);
        CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return model == null ? 0 : Mth.clamp(model.value(), 0, 5);
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return "block.hbm.sellafield." + level(stack);
    }
}
