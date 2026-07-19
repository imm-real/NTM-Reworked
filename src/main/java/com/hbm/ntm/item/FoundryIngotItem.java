package com.hbm.ntm.item;

import com.hbm.ntm.foundry.FoundryMaterial;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** Component-backed {@code ingot_raw} material carrier. */
public final class FoundryIngotItem extends Item {
    public FoundryIngotItem() { super(new Properties()); }

    public static ItemStack create(Item item, FoundryMaterial material, int count) {
        ItemStack stack = new ItemStack(item, count);
        CompoundTag tag = new CompoundTag();
        tag.putString("material", material.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(material.legacyId()));
        return stack;
    }

    public static FoundryMaterial material(ItemStack stack) {
        if (!(stack.getItem() instanceof FoundryIngotItem)) return null;
        String id = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getString("material");
        for (FoundryMaterial material : FoundryMaterial.values()) {
            if (material.id().equals(id)) return material;
        }
        return null;
    }

    @Override public Component getName(ItemStack stack) {
        FoundryMaterial material = material(stack);
        return material == null ? Component.literal("Undefined Ingot")
                : Component.translatable("item.hbm.ingot_raw",
                        Component.translatable("hbmmat." + material.id()));
    }
}
