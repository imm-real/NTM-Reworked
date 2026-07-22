package com.hbm.ntm.client.compat.jei;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class FluidContainerRecipeCategory implements IRecipeCategory<FluidContainerJeiRecipe> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/nei/gui_nei_fluid.png");

    private final IDrawable background;
    private final IDrawable icon;

    public FluidContainerRecipeCategory(IGuiHelper gui) {
        background = gui.createDrawable(TEXTURE, 5, 11, 166, 65);
        icon = gui.createDrawableItemLike(ModItems.FLUID_TANK_EMPTY.get());
    }

    @Override
    public RecipeType<FluidContainerJeiRecipe> getRecipeType() {
        return HbmJeiPlugin.FLUID_CONTAINERS;
    }

    @Override
    public Component getTitle() {
        return Component.literal("Fluid Containers");
    }

    @Override
    public int getWidth() {
        return 166;
    }

    @Override
    public int getHeight() {
        return 65;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, FluidContainerJeiRecipe recipe,
                          IFocusGroup focuses) {
        var fluid = recipe.fluid();
        builder.addInputSlot(30, 24)
                .setFluidRenderer(fluid.getAmount(), false, 16, 16)
                .addFluidStack(fluid.getFluid(), fluid.getAmount(), fluid.getComponentsPatch());
        builder.addInputSlot(48, 24).addItemStack(recipe.empty());
        builder.addOutputSlot(120, 24).addItemStack(recipe.full());
    }

    @Override
    public void draw(FluidContainerJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        background.draw(graphics, 0, 0);
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }

    @Override
    public ResourceLocation getRegistryName(FluidContainerJeiRecipe recipe) {
        return recipe.id();
    }
}
