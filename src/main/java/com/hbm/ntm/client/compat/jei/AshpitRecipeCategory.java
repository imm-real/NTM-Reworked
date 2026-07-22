package com.hbm.ntm.client.compat.jei;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

public final class AshpitRecipeCategory implements IRecipeCategory<AshpitJeiRecipe> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/nei/gui_nei.png");

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable machineFrame;

    public AshpitRecipeCategory(IGuiHelper gui) {
        background = gui.createDrawable(TEXTURE, 5, 11, 166, 65);
        icon = gui.createDrawableItemLike(ModItems.MACHINE_ASHPIT_ITEM.get());
        slot = gui.createDrawable(TEXTURE, 5, 87, 18, 18);
        machineFrame = gui.createDrawable(TEXTURE, 59, 87, 18, 36);
    }

    @Override
    public RecipeType<AshpitJeiRecipe> getRecipeType() {
        return HbmJeiPlugin.ASHPIT;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.hbm.machine_ashpit");
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
    public void setRecipe(IRecipeLayoutBuilder builder, AshpitJeiRecipe recipe,
                          IFocusGroup focuses) {
        builder.addInputSlot(48, 24)
                .setBackground(slot, -1, -1)
                .addItemStacks(Arrays.asList(recipe.source().getItems()));
        builder.addInputSlot(30, 24)
                .setBackground(slot, -1, -1)
                .addItemStacks(Arrays.asList(recipe.fuel().getItems()));
        builder.addOutputSlot(102, 24)
                .setBackground(slot, -1, -1)
                .addItemStack(recipe.output().copy());
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, 75, 31)
                .addItemStack(new ItemStack(ModItems.MACHINE_ASHPIT_ITEM.get()));
    }

    @Override
    public void draw(AshpitJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        background.draw(graphics, 0, 0);
        machineFrame.draw(graphics, 74, 14);
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }

    @Override
    public ResourceLocation getRegistryName(AshpitJeiRecipe recipe) {
        return recipe.id();
    }
}
