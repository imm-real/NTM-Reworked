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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class AnvilRecipeCategory implements IRecipeCategory<AnvilJeiRecipe> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 82;
    private final IDrawable icon;
    private final IDrawable arrow;

    public AnvilRecipeCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemLike(ModItems.ANVIL_IRON_ITEM.get());
        arrow = gui.getRecipeArrow();
    }

    @Override
    public RecipeType<AnvilJeiRecipe> getRecipeType() {
        return HbmJeiPlugin.ANVIL;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.hbm.anvil");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AnvilJeiRecipe recipe, IFocusGroup focuses) {
        for (int index = 0; index < recipe.inputs().size(); index++) {
            AnvilJeiRecipe.Input input = recipe.inputs().get(index);
            builder.addInputSlot(3 + index % 4 * 18, 3 + index / 4 * 18)
                    .setStandardSlotBackground()
                    .addItemStacks(input.displayStacks());
        }
        for (int index = 0; index < recipe.outputs().size(); index++) {
            var output = recipe.outputs().get(index);
            ItemStack stack = output.stack().get().copy();
            var slot = builder.addOutputSlot(137 + index % 2 * 18, 21 + index / 2 * 18)
                    .setOutputSlotBackground()
                    .addItemStack(stack);
            if (output.chance() < 1.0F) {
                slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(
                        "jei.hbm.chance", Math.round(output.chance() * 100.0F))));
            }
        }
    }

    @Override
    public void draw(AnvilJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        arrow.draw(graphics, 101, 27);
        Component tier = recipe.tierUpper() < 0 || recipe.tierUpper() == recipe.tierLower()
                ? Component.translatable("jei.hbm.anvil.tier", recipe.tierLower())
                : Component.translatable("jei.hbm.anvil.tier_range", recipe.tierLower(), recipe.tierUpper());
        graphics.drawString(Minecraft.getInstance().font, tier, 3, 69, 0x404040, false);
    }

    @Override
    public ResourceLocation getRegistryName(AnvilJeiRecipe recipe) {
        return recipe.id();
    }
}
