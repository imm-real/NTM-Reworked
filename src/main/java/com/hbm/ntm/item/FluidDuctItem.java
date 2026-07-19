package com.hbm.ntm.item;

import com.hbm.ntm.block.FluidDuctBlock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;

/** Inventory duct with its preferred fluid already tattooed on it. */
public final class FluidDuctItem extends BlockItem {
    private static final String FLUID = "fluid";

    public FluidDuctItem(Block block) {
        super(block, new Item.Properties());
    }

    public static ItemStack create(Item item, FluidIdentifierItem.Selection selection, int count) {
        ItemStack stack = new ItemStack(item, count);
        CompoundTag tag = new CompoundTag();
        tag.putString(FLUID, selection.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static FluidIdentifierItem.Selection selection(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(FLUID) ? FluidIdentifierItem.Selection.byId(tag.getString(FLUID))
                : FluidIdentifierItem.Selection.NONE;
    }

    @Override public Component getName(ItemStack stack) {
        return Component.translatable("item.hbm.fluid_duct")
                .append(" ").append(Component.translatable(selection(stack).translationKey()));
    }
}
