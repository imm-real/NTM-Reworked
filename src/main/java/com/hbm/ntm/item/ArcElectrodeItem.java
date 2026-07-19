package com.hbm.ntm.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** Arc electrodes labeled by component data instead of registry clutter. */
public final class ArcElectrodeItem extends Item {
    private static final String TYPE = "type";
    private static final String DURABILITY = "durability";

    public ArcElectrodeItem() {
        super(new Properties().stacksTo(1));
    }

    public static ItemStack create(Item item, ElectrodeType type, int count) {
        ItemStack stack = new ItemStack(item, count);
        CompoundTag tag = new CompoundTag();
        tag.putString(TYPE, type.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(type.legacyMetadata()));
        return stack;
    }

    public static ElectrodeType type(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(TYPE)) {
            String id = tag.getString(TYPE);
            for (ElectrodeType type : ElectrodeType.values()) if (type.id().equals(id)) return type;
        }
        CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (model != null) {
            for (ElectrodeType type : ElectrodeType.values()) {
                if (type.legacyMetadata() == model.value()) return type;
            }
        }
        return ElectrodeType.GRAPHITE;
    }

    public static int durability(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(DURABILITY);
    }

    public static boolean damage(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int durability = tag.getInt(DURABILITY) + 1;
        tag.putInt(DURABILITY, durability);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return durability >= type(stack).maxDurability();
    }

    public static ItemStack burnt(Item item, ItemStack electrode) {
        ElectrodeType type = type(electrode);
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putString(TYPE, type.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(type.legacyMetadata()));
        return stack;
    }

    @Override public boolean isBarVisible(ItemStack stack) { return durability(stack) > 0; }
    @Override public int getBarWidth(ItemStack stack) {
        return Math.round(13F * (1F - Math.min(1F, (float) durability(stack) / type(stack).maxDurability())));
    }
    @Override public String getDescriptionId(ItemStack stack) {
        return "item.hbm.arc_electrode." + type(stack).id();
    }

    /** Original ordinal order and durability values. */
    public enum ElectrodeType {
        GRAPHITE("graphite", 0, 10),
        LANTHANIUM("lanthanium", 1, 100),
        DESH("desh", 2, 500),
        SATURNITE("saturnite", 3, 1_500);

        private final String id;
        private final int legacyMetadata;
        private final int maxDurability;

        ElectrodeType(String id, int legacyMetadata, int maxDurability) {
            this.id = id;
            this.legacyMetadata = legacyMetadata;
            this.maxDurability = maxDurability;
        }

        public String id() { return id; }
        public int legacyMetadata() { return legacyMetadata; }
        public int maxDurability() { return maxDurability; }
    }
}
