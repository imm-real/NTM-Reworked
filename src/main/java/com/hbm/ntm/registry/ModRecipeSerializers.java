package com.hbm.ntm.registry;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.recipe.FluidDuctTypingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, HbmNtm.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<FluidDuctTypingRecipe>>
            FLUID_DUCT_TYPING = SERIALIZERS.register("fluid_duct_typing",
            () -> new SimpleCraftingRecipeSerializer<>(FluidDuctTypingRecipe::new));

    private ModRecipeSerializers() { }

    public static void register(IEventBus bus) { SERIALIZERS.register(bus); }
}
