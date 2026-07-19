package com.hbm.ntm.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** One circuit item, many tiny expensive personalities. */
public final class CircuitItem extends Item {
    private static final String TYPE = "type";

    public CircuitItem() { super(new Properties()); }

    public static ItemStack create(Item item, CircuitType type, int count) {
        ItemStack stack = new ItemStack(item, count);
        CompoundTag tag = new CompoundTag();
        tag.putString(TYPE, type.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(type.legacyMetadata()));
        return stack;
    }

    public static CircuitType type(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(TYPE)) {
            String id = tag.getString(TYPE);
            for (CircuitType type : CircuitType.values()) if (type.id().equals(id)) return type;
        }
        CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (model != null) for (CircuitType type : CircuitType.values()) if (type.legacyMetadata() == model.value()) return type;
        return CircuitType.VACUUM_TUBE;
    }

    @Override public String getDescriptionId(ItemStack stack) {
        return "item.hbm.circuit." + type(stack).id();
    }

    /** Creative-tab order. Do not alphabetize the electronics drawer. */
    public enum CircuitType {
        VACUUM_TUBE("vacuum_tube", 0),
        NUMITRON("numitron", 19),
        CAPACITOR("capacitor", 1),
        CAPACITOR_TANTALIUM("capacitor_tantalium", 2),
        PCB("pcb", 3),
        SILICON("silicon", 4),
        CHIP("chip", 5),
        ANALOG("analog", 7),
        BASIC("basic", 8),
        ADVANCED("advanced", 9),
        CAPACITOR_BOARD("capacitor_board", 10),
        CONTROLLER_CHASSIS("controller_chassis", 12);

        private final String id;
        private final int legacyMetadata;
        CircuitType(String id, int legacyMetadata) { this.id = id; this.legacyMetadata = legacyMetadata; }
        public String id() { return id; }
        public int legacyMetadata() { return legacyMetadata; }
    }
}
