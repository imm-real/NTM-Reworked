package com.hbm.ntm.client.compat.jei;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.item.BatteryPackItem;
import com.hbm.ntm.recipe.ArcWelderRecipes.ArcWelderRecipe;
import com.hbm.ntm.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;

public final class ArcWelderRecipeCategory implements IRecipeCategory<ArcWelderRecipe> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/nei/gui_nei.png");

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable machineFrame;

    public ArcWelderRecipeCategory(IGuiHelper gui) {
        background = gui.createDrawable(TEXTURE, 5, 11, 166, 65);
        icon = gui.createDrawableItemLike(ModItems.MACHINE_ARC_WELDER_ITEM.get());
        slot = gui.createDrawable(TEXTURE, 5, 87, 18, 18);
        machineFrame = gui.createDrawable(TEXTURE, 59, 87, 18, 36);
    }

    @Override
    public RecipeType<ArcWelderRecipe> getRecipeType() {
        return HbmJeiPlugin.ARC_WELDER;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.hbm.machine_arc_welder");
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
    public void setRecipe(IRecipeLayoutBuilder builder, ArcWelderRecipe recipe,
                          IFocusGroup focuses) {
        int count = recipe.ingredients().size() + (recipe.fluid() == null ? 0 : 1);
        int[][] positions = inputPositions(count);
        int index = 0;
        for (var input : recipe.ingredients()) {
            List<ItemStack> stacks = Arrays.stream(input.ingredient().getItems())
                    .map(stack -> stack.copyWithCount(input.count()))
                    .toList();
            builder.addInputSlot(positions[index][0], positions[index][1])
                    .setBackground(slot, -1, -1)
                    .addItemStacks(stacks);
            index++;
        }
        if (recipe.fluid() != null) {
            var fluid = recipe.fluid();
            builder.addInputSlot(positions[index][0], positions[index][1])
                    .setBackground(slot, -1, -1)
                    .setFluidRenderer(fluid.getAmount(), false, 16, 16)
                    .addFluidStack(fluid.getFluid(), fluid.getAmount(), fluid.getComponentsPatch());
        }
        builder.addOutputSlot(102, 24)
                .setBackground(slot, -1, -1)
                .addItemStack(recipe.output().copy());
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, 75, 31)
                .addItemStack(new ItemStack(ModItems.MACHINE_ARC_WELDER_ITEM.get()));
    }

    @Override
    public void draw(ArcWelderRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        background.draw(graphics, 0, 0);
        machineFrame.draw(graphics, 74, 14);

        var font = Minecraft.getInstance().font;
        String duration = BatteryPackItem.shortNumber(recipe.duration()) + " ticks";
        String consumption = BatteryPackItem.shortNumber(recipe.consumption()) + "HE/t";
        graphics.drawString(font, duration, 164 - font.width(duration), 45, 0x404040, false);
        graphics.drawString(font, consumption, 164 - font.width(consumption), 57, 0x404040, false);
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }

    private static int[][] inputPositions(int count) {
        return switch (count) {
            case 1 -> new int[][]{{48, 24}};
            case 2 -> new int[][]{{48, 24}, {30, 24}};
            case 3 -> new int[][]{{48, 24}, {30, 24}, {12, 24}};
            case 4 -> new int[][]{{48, 15}, {30, 15}, {48, 33}, {30, 33}};
            default -> new int[count][2];
        };
    }
}
