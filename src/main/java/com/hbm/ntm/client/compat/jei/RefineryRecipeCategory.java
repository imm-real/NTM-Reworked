package com.hbm.ntm.client.compat.jei;

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
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

public final class RefineryRecipeCategory implements IRecipeCategory<RefineryJeiRecipe> {
    private static final int[] OUTPUT_X = {60, 78, 60, 78};
    private static final int[] OUTPUT_Y = {4, 4, 22, 22};

    private final IDrawable icon;
    private final IDrawable arrow;

    public RefineryRecipeCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemLike(ModItems.MACHINE_REFINERY_ITEM.get());
        arrow = gui.getRecipeArrow();
    }

    @Override
    public RecipeType<RefineryJeiRecipe> getRecipeType() {
        return HbmJeiPlugin.REFINERY;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.hbm.refinery");
    }

    @Override
    public int getWidth() {
        return 150;
    }

    @Override
    public int getHeight() {
        return 54;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RefineryJeiRecipe recipe, IFocusGroup focuses) {
        JeiFluidHelper.fluidSlot(builder, recipe.input().getFluid(), recipe.input().getAmount(),
                true, 8, 19);
        List<FluidStack> outputs = recipe.outputs();
        for (int i = 0; i < outputs.size() && i < OUTPUT_X.length; i++) {
            JeiFluidHelper.fluidSlot(builder, outputs.get(i).getFluid(), outputs.get(i).getAmount(),
                    false, OUTPUT_X[i], OUTPUT_Y[i]);
        }
        builder.addOutputSlot(112, 13).setOutputSlotBackground().addItemStack(recipe.byproduct().copy());
    }

    @Override
    public void draw(RefineryJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        arrow.draw(graphics, 32, 19);
        var font = Minecraft.getInstance().font;
        Component chance = Component.translatable("jei.hbm.chance", recipe.byproductChance());
        graphics.drawString(font, chance, 130 - font.width(chance) / 2, 34, 0x404040, false);
    }
}
