package com.hbm.ntm.registry;

import com.hbm.ntm.HbmNtm;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, HbmNtm.MOD_ID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> HAZMAT = MATERIALS.register(
            "hazmat",
            () -> new ArmorMaterial(
                    defense(1, 4, 5, 2),
                    5,
                    SoundEvents.ARMOR_EQUIP_LEATHER,
                    () -> Ingredient.of(ModItems.HAZMAT_CLOTH.get()),
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "hazmat"))),
                    0.0F,
                    0.0F
            )
    );

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> ENVSUIT = MATERIALS.register(
            "envsuit",
            () -> new ArmorMaterial(
                    defense(3, 6, 8, 3),
                    10,
                    SoundEvents.ARMOR_EQUIP_IRON,
                    () -> Ingredient.EMPTY,
                    List.of(
                            layer("envsuit_helmet"),
                            layer("envsuit_chest"),
                            layer("envsuit_arm"),
                            layer("envsuit_leg"),
                            layer("envsuit_lamp")
                    ),
                    0.0F,
                    0.0F
            )
    );

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> DNT_NANO = MATERIALS.register(
            "dnt_nano",
            () -> new ArmorMaterial(
                    defense(3, 6, 8, 3),
                    0,
                    SoundEvents.ARMOR_EQUIP_IRON,
                    () -> Ingredient.EMPTY,
                    List.of(layer("dnt_helmet"), layer("dnt_chest"),
                            layer("dnt_arm"), layer("dnt_leg")),
                    0.0F,
                    0.0F
            )
    );

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> GOGGLES = ironHeadMaterial("goggles");
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> GAS_MASK = ironHeadMaterial("gas_mask");
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> GAS_MASK_M65 = ironHeadMaterial("gas_mask_m65");
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> GAS_MASK_MONO = ironHeadMaterial("gas_mask_mono");
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> GAS_MASK_OLDE = ironHeadMaterial("gas_mask_olde");
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MASK_RAG = ragHeadMaterial("mask_rag");
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MASK_PISS = ragHeadMaterial("mask_piss");

    private ModArmorMaterials() {
    }

    private static ArmorMaterial.Layer layer(String id) {
        return new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id));
    }

    private static DeferredHolder<ArmorMaterial, ArmorMaterial> ironHeadMaterial(String id) {
        return MATERIALS.register(id, () -> new ArmorMaterial(
                defense(0, 0, 0, 2),
                9,
                SoundEvents.ARMOR_EQUIP_IRON,
                () -> Ingredient.of(Items.IRON_INGOT),
                List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id))),
                0.0F,
                0.0F
        ));
    }

    private static DeferredHolder<ArmorMaterial, ArmorMaterial> ragHeadMaterial(String id) {
        return MATERIALS.register(id, () -> new ArmorMaterial(
                defense(0, 0, 0, 1),
                0,
                SoundEvents.ARMOR_EQUIP_LEATHER,
                () -> Ingredient.of(ModItems.RAG.get()),
                List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id))),
                0.0F,
                0.0F
        ));
    }

    private static EnumMap<ArmorItem.Type, Integer> defense(int boots, int legs, int chest, int helmet) {
        EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.BOOTS, boots);
        defense.put(ArmorItem.Type.LEGGINGS, legs);
        defense.put(ArmorItem.Type.CHESTPLATE, chest);
        defense.put(ArmorItem.Type.HELMET, helmet);
        defense.put(ArmorItem.Type.BODY, chest);
        return defense;
    }

    public static void register(IEventBus modEventBus) {
        MATERIALS.register(modEventBus);
    }
}
