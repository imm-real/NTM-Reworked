package com.hbm.ntm.item;

import com.hbm.ntm.foundry.FoundryMaterial;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** Firearm parts sorted by material inside one item. */
public final class FoundryPartItem extends Item {
    private static final String MATERIAL = "material";
    private final PartType type;

    public FoundryPartItem(PartType type) {
        super(new Properties());
        this.type = type;
    }

    public PartType type() { return type; }

    public static ItemStack create(Item item, FoundryMaterial material, int count) {
        ItemStack stack = new ItemStack(item, count);
        CompoundTag tag = new CompoundTag();
        tag.putString(MATERIAL, material.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(material.legacyId()));
        return stack;
    }

    public static FoundryMaterial material(ItemStack stack) {
        if (!(stack.getItem() instanceof FoundryPartItem)) return null;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(MATERIAL)) return FoundryMaterial.byId(tag.getString(MATERIAL));
        CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return model == null ? null : FoundryMaterial.byLegacyId(model.value());
    }

    @Override public String getDescriptionId(ItemStack stack) {
        FoundryMaterial material = material(stack);
        return material == null ? super.getDescriptionId(stack)
                : "item.hbm." + type.id() + "." + material.id();
    }

    public enum PartType {
        LIGHT_BARREL("part_barrel_light", 216),
        HEAVY_BARREL("part_barrel_heavy", 432),
        LIGHT_RECEIVER("part_receiver_light", 288),
        HEAVY_RECEIVER("part_receiver_heavy", 648),
        MECHANISM("part_mechanism", 288),
        STOCK("part_stock", 288),
        GRIP("part_grip", 144);

        private final String id;
        private final int cost;

        PartType(String id, int cost) {
            this.id = id;
            this.cost = cost;
        }

        public String id() { return id; }
        public int cost() { return cost; }
    }
}
