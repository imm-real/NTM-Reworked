package com.hbm.ntm.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** One shell item with its caliber written in component ink. */
public final class ShellItem extends Item {
    private static final String MATERIAL = "material";

    public ShellItem() {
        super(new Properties());
    }

    public static ItemStack create(Item item, ShellMaterial material, int count) {
        ItemStack stack = new ItemStack(item, count);
        CompoundTag tag = new CompoundTag();
        tag.putString(MATERIAL, material.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(material.legacyMetadata()));
        return stack;
    }

    public static ItemStack steel(Item item, int count) {
        return create(item, ShellMaterial.STEEL, count);
    }

    public static ItemStack titanium(Item item, int count) {
        return create(item, ShellMaterial.TITANIUM, count);
    }

    public static ShellMaterial material(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(MATERIAL)) {
            ShellMaterial material = ShellMaterial.byId(tag.getString(MATERIAL));
            if (material != null) return material;
        }
        CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (model != null) {
            for (ShellMaterial material : ShellMaterial.values()) {
                if (material.legacyMetadata() == model.value()) return material;
            }
        }
        return ShellMaterial.STEEL;
    }

    public static boolean isSteel(ItemStack stack) {
        return stack.getItem() instanceof ShellItem && material(stack) == ShellMaterial.STEEL;
    }

    public static boolean isTitanium(ItemStack stack) {
        return stack.getItem() instanceof ShellItem && material(stack) == ShellMaterial.TITANIUM;
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return "item.hbm.shell." + material(stack).id();
    }

    public enum ShellMaterial {
        STEEL("steel", 30),
        TITANIUM("titanium", 2200);

        private final String id;
        private final int legacyMetadata;

        ShellMaterial(String id, int legacyMetadata) {
            this.id = id;
            this.legacyMetadata = legacyMetadata;
        }

        public String id() { return id; }
        public int legacyMetadata() { return legacyMetadata; }

        public static ShellMaterial byId(String id) {
            for (ShellMaterial material : values()) if (material.id.equals(id)) return material;
            return null;
        }
    }
}
