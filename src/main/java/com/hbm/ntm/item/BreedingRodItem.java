package com.hbm.ntm.item;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.hazard.HazardCarrier;
import com.hbm.ntm.hazard.HazardProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** Single, dual and quad breeding rods hiding in custom data. */
public final class BreedingRodItem extends Item implements HazardCarrier {
    private static final String TYPE_KEY = "hbmBreedingRodType";
    private final Form form;

    public BreedingRodItem(Properties properties, Form form) {
        super(properties);
        this.form = form;
    }

    public Form form() { return form; }

    @Override public String getDescriptionId(ItemStack stack) {
        return "item.hbm." + form.id() + "." + type(stack).id();
    }

    @Override public HazardProfile hbm$getHazards(ItemStack stack) {
        return HazardProfile.radiation(radiation(type(stack), form));
    }

    @Override public boolean hasCraftingRemainingItem(ItemStack stack) { return true; }

    @Override public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return BuiltInRegistries.ITEM.getOptional(form.emptyId())
                .map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    public static ItemStack stack(Item item, Type type, int count) {
        ItemStack stack = new ItemStack(item, count);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TYPE_KEY, type.id()));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(type.ordinal()));
        return stack;
    }

    public static Type type(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(TYPE_KEY)) return Type.byId(tag.getString(TYPE_KEY));
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return Type.byMetadata(data == null ? 0 : data.value());
    }

    public static float radiation(Type type, Form form) {
        float base = switch (type) {
            case TRITIUM -> 0.001F;
            case CO60, AC227 -> 30.0F;
            case RA226 -> 7.5F;
            case TH232 -> 0.1F;
            case THF -> 1.75F;
            case U235 -> 1.0F;
            case NP237 -> 2.5F;
            case U238 -> 0.25F;
            case PU238 -> 10.0F;
            case PU239 -> 5.0F;
            case RGP -> 6.25F;
            case WASTE -> 15.0F;
            case URANIUM -> 0.35F;
            default -> 0.0F;
        };
        return base * form.radiationMultiplier();
    }

    public enum Form {
        SINGLE("rod", "rod_empty", 1, 0.5F),
        DUAL("rod_dual", "rod_dual_empty", 2, 1.0F),
        QUAD("rod_quad", "rod_quad_empty", 3, 2.0F);

        private final String id;
        private final ResourceLocation emptyId;
        private final int breedingFluxMultiplier;
        private final float radiationMultiplier;

        Form(String id, String emptyId, int breedingFluxMultiplier, float radiationMultiplier) {
            this.id = id;
            this.emptyId = ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, emptyId);
            this.breedingFluxMultiplier = breedingFluxMultiplier;
            this.radiationMultiplier = radiationMultiplier;
        }

        public String id() { return id; }
        public ResourceLocation emptyId() { return emptyId; }
        public int breedingFluxMultiplier() { return breedingFluxMultiplier; }
        public float radiationMultiplier() { return radiationMultiplier; }
    }

    public enum Type {
        LITHIUM("lithium"), TRITIUM("tritium"), CO("co"), CO60("co60"),
        TH232("th232"), THF("thf"), U235("u235"), NP237("np237"),
        U238("u238"), PU238("pu238"), PU239("pu239"), RGP("rgp"),
        WASTE("waste"), LEAD("lead"), URANIUM("uranium"), RA226("ra226"), AC227("ac227");

        private final String id;
        Type(String id) { this.id = id; }
        public String id() { return id; }
        public static Type byId(String id) {
            for (Type type : values()) if (type.id.equals(id)) return type;
            return LITHIUM;
        }
        public static Type byMetadata(int metadata) {
            return metadata >= 0 && metadata < values().length ? values()[metadata] : LITHIUM;
        }
    }
}
