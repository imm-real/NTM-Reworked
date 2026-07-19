package com.hbm.ntm.item;

import com.hbm.ntm.foundry.FoundryMaterial;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** Dense wire variants sorted by material in component data. */
public final class DenseWireItem extends Item {
    private static final String MATERIAL = "material";

    public DenseWireItem() { super(new Properties()); }

    public static ItemStack create(Item item, FoundryMaterial material, int count) {
        ItemStack stack = new ItemStack(item, count);
        CompoundTag tag = new CompoundTag();
        tag.putString(MATERIAL, material.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(material.legacyId()));
        return stack;
    }

    public static FoundryMaterial material(ItemStack stack) {
        if (!(stack.getItem() instanceof DenseWireItem)) return null;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(MATERIAL)) return FoundryMaterial.byId(tag.getString(MATERIAL));
        CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return model == null ? null : FoundryMaterial.byLegacyId(model.value());
    }

    @Override public String getDescriptionId(ItemStack stack) {
        FoundryMaterial material = material(stack);
        return material == null ? super.getDescriptionId(stack) : "item.hbm.wire_dense." + material.id();
    }
}
