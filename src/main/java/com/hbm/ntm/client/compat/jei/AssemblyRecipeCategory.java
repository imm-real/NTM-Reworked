package com.hbm.ntm.client.compat.jei;

import com.hbm.ntm.recipe.AssemblyRecipe;
import com.hbm.ntm.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.Arrays;
import java.util.List;

public final class AssemblyRecipeCategory implements IRecipeCategory<AssemblyRecipe> {
    private final IDrawable icon;
    private final IDrawable arrow;

    public AssemblyRecipeCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemLike(ModItems.MACHINE_ASSEMBLY_MACHINE_ITEM.get());
        arrow = gui.getRecipeArrow();
    }

    @Override
    public RecipeType<AssemblyRecipe> getRecipeType() {
        return HbmJeiPlugin.ASSEMBLY;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.hbm.assembly");
    }

    @Override
    public int getWidth() {
        return 176;
    }

    @Override
    public int getHeight() {
        return 92;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AssemblyRecipe recipe, IFocusGroup focuses) {
        for (int index = 0; index < recipe.inputs().size(); index++) {
            AssemblyRecipe.Input input = recipe.inputs().get(index);
            List<ItemStack> stacks = Arrays.stream(input.ingredient().getItems())
                    .map(stack -> stack.copyWithCount(input.count()))
                    .toList();
            builder.addInputSlot(3 + index % 4 * 18, 3 + index / 4 * 18)
                    .setStandardSlotBackground()
                    .addItemStacks(stacks);
        }

        recipe.fluidInput().ifPresent(input -> addFluid(builder, input, true, 78, 3));
        builder.addOutputSlot(147, 20)
                .setOutputSlotBackground()
                .addItemStack(recipe.output().copy());
        recipe.fluidOutput().ifPresent(output -> addFluid(builder, output, false, 147, 46));
    }

    private static void addFluid(IRecipeLayoutBuilder builder, AssemblyRecipe.FluidIo fluid,
                                 boolean input, int x, int y) {
        Fluid value = BuiltInRegistries.FLUID.get(fluid.fluid());
        if (value == null || value == Fluids.EMPTY) return;
        var slot = input ? builder.addInputSlot(x, y) : builder.addOutputSlot(x, y);
        slot.setStandardSlotBackground()
                .setFluidRenderer(Math.max(1_000L, fluid.amount()), false, 16, 16)
                .addFluidStack(value, fluid.amount());
    }

    @Override
    public void draw(AssemblyRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        arrow.draw(graphics, 108, 27);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable("jei.hbm.power", recipe.power()), 3, 73,
                0x404040, false);
        Component time = Component.translatable("jei.hbm.time", recipe.duration());
        graphics.drawString(font, time, 173 - font.width(time), 73, 0x404040, false);
        graphics.drawString(font, Component.literal(recipe.name()), 3, 84, 0x606060, false);
    }

    @Override
    public ResourceLocation getRegistryName(AssemblyRecipe recipe) {
        return recipe.id();
    }
}
