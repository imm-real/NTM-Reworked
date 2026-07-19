package com.hbm.ntm.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** One generated pipe item with its material printed on the component tag. */
public final class PipeItem extends Item {
    private static final String MATERIAL = "material";

    public PipeItem() { super(new Properties()); }

    public static ItemStack create(Item item, PipeMaterial material, int count) {
        ItemStack stack = new ItemStack(item, count);
        CompoundTag tag = new CompoundTag();
        tag.putString(MATERIAL, material.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(material.legacyMetadata()));
        return stack;
    }

    public static ItemStack copper(Item item, int count) { return create(item, PipeMaterial.COPPER, count); }
    public static ItemStack steel(Item item, int count) { return create(item, PipeMaterial.STEEL, count); }
    public static ItemStack duraSteel(Item item, int count) { return create(item, PipeMaterial.DURA_STEEL, count); }
    public static ItemStack lead(Item item, int count) { return create(item, PipeMaterial.LEAD, count); }

    public static PipeMaterial material(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(MATERIAL)) {
            PipeMaterial material = PipeMaterial.byId(tag.getString(MATERIAL));
            if (material != null) return material;
        }
        CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (model != null) {
            for (PipeMaterial material : PipeMaterial.values()) {
                if (material.legacyMetadata() == model.value()) return material;
            }
        }
        return PipeMaterial.COPPER;
    }

    public static boolean isCopper(ItemStack stack) { return material(stack) == PipeMaterial.COPPER; }
    public static boolean isSteel(ItemStack stack) { return material(stack) == PipeMaterial.STEEL; }
    public static boolean isDuraSteel(ItemStack stack) { return material(stack) == PipeMaterial.DURA_STEEL; }
    public static boolean isLead(ItemStack stack) { return material(stack) == PipeMaterial.LEAD; }

    @Override public String getDescriptionId(ItemStack stack) {
        return "item.hbm.pipe." + material(stack).id();
    }

    public enum PipeMaterial {
        STEEL("steel", 30),
        DURA_STEEL("dura_steel", 33),
        COPPER("copper", 2900),
        LEAD("lead", 8200);

        private final String id;
        private final int legacyMetadata;

        PipeMaterial(String id, int legacyMetadata) {
            this.id = id;
            this.legacyMetadata = legacyMetadata;
        }

        public String id() { return id; }
        public int legacyMetadata() { return legacyMetadata; }

        public static PipeMaterial byId(String id) {
            for (PipeMaterial material : values()) if (material.id.equals(id)) return material;
            return null;
        }
    }
}
