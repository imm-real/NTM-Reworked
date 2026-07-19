package com.hbm.ntm.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

public final class AshItem extends Item {
    public AshItem() {
        super(new Item.Properties());
    }

    public static ItemStack create(Item item, AshType type) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(type.ordinal()));
        return stack;
    }

    public static AshType type(ItemStack stack) {
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        int index = data == null ? 0 : data.value();
        AshType[] values = AshType.values();
        return index >= 0 && index < values.length ? values[index] : AshType.WOOD;
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return "item.hbm.powder_ash." + type(stack).serializedName;
    }

    public enum AshType {
        WOOD("wood", 100),
        COAL("coal", 200),
        MISC("misc", 100),
        FLY("fly", 200),
        SOOT("soot", 100),
        FULLERENE("fullerene", 0);

        private final String serializedName;
        private final int burnTime;

        AshType(String serializedName, int burnTime) {
            this.serializedName = serializedName;
            this.burnTime = burnTime;
        }

        public String serializedName() {
            return serializedName;
        }

        public int burnTime() {
            return burnTime;
        }
    }
}
