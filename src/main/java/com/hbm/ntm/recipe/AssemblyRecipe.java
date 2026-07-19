package com.hbm.ntm.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;
import java.util.Optional;

/** Datapack recipe with a permanent assembly-machine lane assignment. */
public record AssemblyRecipe(
        ResourceLocation id,
        String name,
        List<Input> inputs,
        Optional<FluidIo> fluidInput,
        ItemStack output,
        Optional<FluidIo> fluidOutput,
        int duration,
        long power,
        List<String> pools,
        Optional<String> autoswitch
) {
    public static final int MAX_INPUTS = 12;

    public static final Codec<Input> INPUT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(Input::ingredient),
            Codec.intRange(1, 64).fieldOf("count").forGetter(Input::count)
    ).apply(instance, Input::new));

    public static final Codec<FluidIo> FLUID_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("fluid").forGetter(FluidIo::fluid),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("amount").forGetter(FluidIo::amount)
    ).apply(instance, FluidIo::new));

    public static final Codec<AssemblyRecipe> CODEC = RecordCodecBuilder.<AssemblyRecipe>create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(AssemblyRecipe::id),
            Codec.STRING.fieldOf("name").forGetter(AssemblyRecipe::name),
            INPUT_CODEC.listOf().fieldOf("inputs").forGetter(AssemblyRecipe::inputs),
            FLUID_CODEC.optionalFieldOf("fluid_input").forGetter(AssemblyRecipe::fluidInput),
            ItemStack.CODEC.fieldOf("output").forGetter(AssemblyRecipe::output),
            FLUID_CODEC.optionalFieldOf("fluid_output").forGetter(AssemblyRecipe::fluidOutput),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("duration").forGetter(AssemblyRecipe::duration),
            Codec.LONG.fieldOf("power").forGetter(AssemblyRecipe::power),
            Codec.STRING.listOf().optionalFieldOf("pools", List.of()).forGetter(AssemblyRecipe::pools),
            Codec.STRING.optionalFieldOf("autoswitch").forGetter(AssemblyRecipe::autoswitch)
    ).apply(instance, AssemblyRecipe::new)).validate(recipe -> {
        if (recipe.inputs().size() > MAX_INPUTS) {
            return com.mojang.serialization.DataResult.error(
                    () -> recipe.id() + " has more than " + MAX_INPUTS + " item inputs");
        }
        if (recipe.power() < 0L) {
            return com.mojang.serialization.DataResult.error(() -> recipe.id() + " has negative HE/t");
        }
        return com.mojang.serialization.DataResult.success(recipe);
    });

    public ItemStack icon() {
        return output.copyWithCount(1);
    }

    public record Input(Ingredient ingredient, int count) {
        public boolean matches(ItemStack stack) {
            return ingredient.test(stack) && stack.getCount() >= count;
        }

        public ItemStack display() {
            ItemStack[] values = ingredient.getItems();
            return values.length == 0 ? ItemStack.EMPTY : values[0].copyWithCount(count);
        }
    }

    public record FluidIo(ResourceLocation fluid, int amount) { }
}
