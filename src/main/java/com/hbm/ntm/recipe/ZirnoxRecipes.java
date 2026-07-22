package com.hbm.ntm.recipe;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.item.ZirnoxRodItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ZirnoxRecipes {
    private ZirnoxRecipes() {
    }

    public static ItemStack burnResult(ZirnoxRodItem.Type type) {
        return new ItemStack(switch (type) {
            case NATURAL_URANIUM_FUEL -> ModItems.ROD_ZIRNOX_NATURAL_URANIUM_FUEL_DEPLETED.get();
            case URANIUM_FUEL -> ModItems.ROD_ZIRNOX_URANIUM_FUEL_DEPLETED.get();
            case TH232 -> ModItems.ROD_ZIRNOX_THORIUM_FUEL.get();
            case THORIUM_FUEL -> ModItems.ROD_ZIRNOX_THORIUM_FUEL_DEPLETED.get();
            case MOX_FUEL -> ModItems.ROD_ZIRNOX_MOX_FUEL_DEPLETED.get();
            case PLUTONIUM_FUEL -> ModItems.ROD_ZIRNOX_PLUTONIUM_FUEL_DEPLETED.get();
            case U233_FUEL -> ModItems.ROD_ZIRNOX_U233_FUEL_DEPLETED.get();
            case U235_FUEL -> ModItems.ROD_ZIRNOX_U235_FUEL_DEPLETED.get();
            case LES_FUEL -> ModItems.ROD_ZIRNOX_LES_FUEL_DEPLETED.get();
            case LITHIUM -> ModItems.ROD_ZIRNOX_TRITIUM.get();
            case ZFB_MOX -> ModItems.ROD_ZIRNOX_ZFB_MOX_DEPLETED.get();
        });
    }

    public static List<Recipe> all() {
        Map<ZirnoxRodItem.Type, Item> inputs = new EnumMap<>(ZirnoxRodItem.Type.class);
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof ZirnoxRodItem rod) inputs.put(rod.type(), item);
        }
        List<Recipe> recipes = new ArrayList<>();
        for (ZirnoxRodItem.Type type : ZirnoxRodItem.Type.values()) {
            Item inputItem = inputs.get(type);
            if (inputItem == null) continue;
            ItemStack input = new ItemStack(inputItem);
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(inputItem);
            recipes.add(new Recipe(ResourceLocation.fromNamespaceAndPath(
                    HbmNtm.MOD_ID, "zirnox/" + itemId.getPath()), input, burnResult(type)));
        }
        return List.copyOf(recipes);
    }

    public record Recipe(ResourceLocation id, ItemStack input, ItemStack output) {
    }
}
