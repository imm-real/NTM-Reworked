package com.hbm.ntm.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** Component-backed carrier for the material-metadata {@code hbm:plate_welded}. */
public final class WeldedPlateItem extends Item {
    private static final String MATERIAL = "material";

    public WeldedPlateItem() {
        super(new Properties());
    }

    public static ItemStack steel(Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        CompoundTag tag = new CompoundTag();
        tag.putString(MATERIAL, "steel");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(30));
        return stack;
    }

    public static boolean isSteel(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(MATERIAL)) return "steel".equals(tag.getString(MATERIAL));
        CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return model == null || model.value() == 30;
    }

    @Override public String getDescriptionId(ItemStack stack) {
        return isSteel(stack) ? "item.hbm.plate_welded.steel" : super.getDescriptionId(stack);
    }
}
