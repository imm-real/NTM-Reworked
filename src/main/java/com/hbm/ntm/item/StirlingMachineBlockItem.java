package com.hbm.ntm.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class StirlingMachineBlockItem extends DescriptionBlockItem {
    private static final String MISSING_COG = "hbm_missing_cog";

    public StirlingMachineBlockItem(Block block, Properties properties) {
        super(block, properties,
                "block.hbm.machine_stirling.desc.0",
                "block.hbm.machine_stirling.desc.1",
                "block.hbm.machine_stirling.desc.2",
                "block.hbm.machine_stirling.desc.3");
    }

    public static ItemStack withoutCog(BlockItem item) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MISSING_COG, true);
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        return stack;
    }

    public static boolean isMissingCog(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getBoolean(MISSING_COG);
    }
}
