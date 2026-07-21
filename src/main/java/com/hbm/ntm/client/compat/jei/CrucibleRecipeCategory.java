package com.hbm.ntm.client.compat.jei;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.foundry.FoundryMaterial;
import com.hbm.ntm.recipe.CrucibleRecipes.Recipe;
import com.hbm.ntm.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class CrucibleRecipeCategory implements IRecipeCategory<Recipe> {
    // Three-column input grid on the left, output grid on the right, matching the 1.7.10 NEI handler.
    private static final int[] INPUT_X = {6, 24, 42, 6};
    private static final int[] INPUT_Y = {4, 4, 4, 22};
    private static final int[] OUTPUT_X = {100, 118};
    private static final int[] OUTPUT_Y = {4, 4};

    private final IDrawable icon;
    private final IDrawable arrow;

    public CrucibleRecipeCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemLike(ModItems.MACHINE_CRUCIBLE_ITEM.get());
        arrow = gui.getRecipeArrow();
    }

    @Override
    public RecipeType<Recipe> getRecipeType() {
        return HbmJeiPlugin.CRUCIBLE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.hbm.crucible");
    }

    @Override
    public int getWidth() {
        return 180;
    }

    @Override
    public int getHeight() {
        return 58;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        place(builder, recipe.inputs(), true, INPUT_X, INPUT_Y);
        place(builder, recipe.outputs(), false, OUTPUT_X, OUTPUT_Y);
    }

    private static void place(IRecipeLayoutBuilder builder, List<FoundryMaterial.MaterialAmount> amounts,
                              boolean input, int[] xs, int[] ys) {
        for (int i = 0; i < amounts.size() && i < xs.length; i++) {
            ItemStack stack = JeiMaterials.crucibleStack(amounts.get(i));
            if (stack.isEmpty()) continue;
            String amount = JeiMaterials.friendlyAmount(amounts.get(i).amount());
            IRecipeSlotBuilder slot = input ? builder.addInputSlot(xs[i], ys[i])
                    : builder.addOutputSlot(xs[i], ys[i]);
            if (input) {
                slot.setStandardSlotBackground();
            } else {
                slot.setOutputSlotBackground();
            }
            slot.addItemStack(stack);
            slot.addRichTooltipCallback((view, tooltip) ->
                    tooltip.add(Component.literal(amount).withStyle(ChatFormatting.GRAY)));
        }
    }

    @Override
    public void draw(Recipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        arrow.draw(graphics, 66, 6);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable(recipe.translationKey()), 6, 44, 0x404040, false);
    }

    @Override
    public ResourceLocation getRegistryName(Recipe recipe) {
        return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID,
                recipe.translationKey().replace('.', '/'));
    }
}
