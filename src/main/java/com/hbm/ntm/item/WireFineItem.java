package com.hbm.ntm.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** Component-backed carrier for {@code hbm:wire_fine}. */
public final class WireFineItem extends Item {
    private static final String MATERIAL = "material";

    public WireFineItem() {
        super(new Properties());
    }

    public static ItemStack create(Item item, WireMaterial material, int count) {
        ItemStack stack = new ItemStack(item, count);
        CompoundTag tag = new CompoundTag();
        tag.putString(MATERIAL, material.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(material.legacyMetadata()));
        return stack;
    }

    public static WireMaterial material(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(MATERIAL)) {
            String id = tag.getString(MATERIAL);
            for (WireMaterial material : WireMaterial.values()) {
                if (material.id().equals(id)) return material;
            }
        }
        CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (model != null) {
            for (WireMaterial material : WireMaterial.values()) {
                if (material.legacyMetadata() == model.value()) return material;
            }
        }
        return WireMaterial.STEEL;
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return "item.hbm.wire_fine." + material(stack).id();
    }

    public enum WireMaterial {
        CARBON("carbon", 699, "hbm:ingot_graphite", "c:ingots/graphite", true),
        GOLD("gold", 7900, "minecraft:gold_ingot", "c:ingots/gold", true),
        COPPER("copper", 2900, "hbm:ingot_copper", "c:ingots/copper", true),
        TUNGSTEN("tungsten", 7400, "hbm:ingot_tungsten", "c:ingots/tungsten", true),
        ALUMINIUM("aluminium", 1300, "hbm:ingot_aluminium", "c:ingots/aluminum", true),
        LEAD("lead", 8200, "hbm:ingot_lead", "c:ingots/lead", false),
        ZIRCONIUM("zirconium", 4000, "hbm:ingot_zirconium", "c:ingots/zirconium", false),
        STEEL("steel", 30, "hbm:ingot_steel", "c:ingots/steel", false),
        RED_COPPER("red_copper", 31, "hbm:ingot_red_copper", "c:ingots/red_copper", true);

        private final String id;
        private final int legacyMetadata;
        private final String ingotItem;
        private final String ingotTag;
        private final boolean craftingRecompression;

        WireMaterial(String id, int legacyMetadata, String ingotItem, String ingotTag,
                     boolean craftingRecompression) {
            this.id = id;
            this.legacyMetadata = legacyMetadata;
            this.ingotItem = ingotItem;
            this.ingotTag = ingotTag;
            this.craftingRecompression = craftingRecompression;
        }

        public String id() { return id; }
        public int legacyMetadata() { return legacyMetadata; }
        public String ingotItem() { return ingotItem; }
        public String ingotTag() { return ingotTag; }
        public boolean craftingRecompression() { return craftingRecompression; }
    }
}
