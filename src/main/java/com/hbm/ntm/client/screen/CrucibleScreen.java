package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.foundry.FoundryMaterial;
import com.hbm.ntm.inventory.CrucibleMenu;
import com.hbm.ntm.recipe.CrucibleRecipes;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class CrucibleScreen extends AbstractContainerScreen<CrucibleMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/processing/gui_crucible.png");
    private static final ResourceLocation SELECTOR_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/processing/gui_recipe_selector.png");
    private static final int SELECTOR_WIDTH = 176;
    private static final int SELECTOR_HEIGHT = 132;
    private static final int SELECTOR_COLUMNS = 8;
    private static final int SELECTOR_VISIBLE = 40;
    private static final int SELECTOR_STEP = 18;

    private final List<CrucibleRecipes.Recipe> selectorRecipes = new ArrayList<>();
    private EditBox search;
    private boolean selecting;
    private int selectorPage;
    private int selectorSelection;

    public CrucibleScreen(CrucibleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 214;
        inventoryLabelX = 8;
        inventoryLabelY = 120;
    }

    @Override
    protected void init() {
        super.init();
        search = new EditBox(font, selectorLeft() + 28, selectorTop() + 111,
                102, 12, Component.translatable("gui.recipe.search"));
        search.setBordered(false);
        search.setTextColor(0xFFFFFF);
        search.setTextColorUneditable(0xFFFFFF);
        search.setMaxLength(32);
        search.setResponder(this::filterSelectorRecipes);
        search.visible = false;
        search.active = false;
        addRenderableWidget(search);
    }

    private void openSelector() {
        selecting = true;
        selectorSelection = menu.selectedRecipe();
        search.visible = true;
        search.active = true;
        search.setFocused(false);
        setFocused(null);
        search.setValue("");
        filterSelectorRecipes("");
    }

    private void closeSelector() {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, selectorSelection);
        }
        selecting = false;
        search.visible = false;
        search.active = false;
        search.setFocused(false);
        setFocused(null);
    }

    private void filterSelectorRecipes(String value) {
        selectorRecipes.clear();
        String needle = value.toLowerCase(Locale.ROOT);
        for (CrucibleRecipes.Recipe recipe : CrucibleRecipes.selectorOrder()) {
            String name = Component.translatable(recipe.translationKey()).getString().toLowerCase(Locale.ROOT);
            if (needle.isEmpty() || name.contains(needle)) selectorRecipes.add(recipe);
        }
        selectorPage = 0;
    }

    private int maxSelectorPage() {
        return Math.max(0, (selectorRecipes.size() - SELECTOR_VISIBLE + SELECTOR_COLUMNS - 1)
                / SELECTOR_COLUMNS);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (selecting) {
            renderRecipeSelector(graphics, mouseX, mouseY, partialTick);
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(125, 81, 34, 7, mouseX, mouseY)) graphics.renderTooltip(font,
                List.of(Component.literal(menu.progress() + " / 20000 TU")), Optional.empty(), mouseX, mouseY);
        if (isHovering(125, 90, 34, 7, mouseX, mouseY)) graphics.renderTooltip(font,
                List.of(Component.literal(menu.heat() + " / 100000 TU")), Optional.empty(), mouseX, mouseY);
        if (isHovering(106, 80, 18, 18, mouseX, mouseY)) {
            CrucibleRecipes.Recipe recipe = CrucibleRecipes.byId(menu.selectedRecipe());
            if (recipe == null) {
                graphics.renderTooltip(font, Component.literal("Click to set recipe")
                        .withStyle(ChatFormatting.YELLOW), mouseX, mouseY);
            } else {
                graphics.renderTooltip(font, recipeTooltip(recipe), Optional.empty(), mouseX, mouseY);
            }
        }
        if (isHovering(16, 17, 36, 81, mouseX, mouseY)) graphics.renderTooltip(font,
                moltenTooltip(false), Optional.empty(), mouseX, mouseY);
        if (isHovering(61, 17, 36, 81, mouseX, mouseY)) graphics.renderTooltip(font,
                moltenTooltip(true), Optional.empty(), mouseX, mouseY);
    }

    private void renderRecipeSelector(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        int left = selectorLeft();
        int top = selectorTop();
        graphics.blit(SELECTOR_TEXTURE, left, top, 0, 0,
                SELECTOR_WIDTH, SELECTOR_HEIGHT, 256, 256);

        if (search.isFocused()) {
            graphics.blit(SELECTOR_TEXTURE, left + 26, top + 108,
                    0, 132, 106, 16, 256, 256);
        }
        drawSelectorButtonHover(graphics, mouseX, mouseY, left + 152, top + 18, 0);
        drawSelectorButtonHover(graphics, mouseX, mouseY, left + 152, top + 36, 16);
        drawSelectorButtonHover(graphics, mouseX, mouseY, left + 152, top + 90, 32);
        drawSelectorButtonHover(graphics, mouseX, mouseY, left + 134, top + 108, 48);
        drawSelectorButtonHover(graphics, mouseX, mouseY, left + 8, top + 108, 64);

        int start = selectorPage * SELECTOR_COLUMNS;
        int end = Math.min(start + SELECTOR_VISIBLE, selectorRecipes.size());
        for (int index = start; index < end; index++) {
            int relative = index - start;
            int x = left + 7 + SELECTOR_STEP * (relative % SELECTOR_COLUMNS);
            int y = top + 17 + SELECTOR_STEP * (relative / SELECTOR_COLUMNS);
            CrucibleRecipes.Recipe recipe = selectorRecipes.get(index);
            if (recipe.id() == selectorSelection) {
                graphics.blit(SELECTOR_TEXTURE, x, y, 192, 0,
                        SELECTOR_STEP, SELECTOR_STEP, 256, 256);
            }
            graphics.renderItem(recipe.icon(), x + 1, y + 1);
        }

        CrucibleRecipes.Recipe selection = CrucibleRecipes.byId(selectorSelection);
        if (selection != null) graphics.renderItem(selection.icon(), left + 152, top + 72);
        search.render(graphics, mouseX, mouseY, partialTick);
        renderSelectorTooltips(graphics, mouseX, mouseY, start, end, selection);
    }

    private void drawSelectorButtonHover(GuiGraphics graphics, int mouseX, int mouseY,
                                         int x, int y, int textureY) {
        if (inside(mouseX, mouseY, x, y, 16, 16)) {
            graphics.blit(SELECTOR_TEXTURE, x, y, 176, textureY, 16, 16, 256, 256);
        }
    }

    private void renderSelectorTooltips(GuiGraphics graphics, int mouseX, int mouseY,
                                        int start, int end, CrucibleRecipes.Recipe selection) {
        int left = selectorLeft();
        int top = selectorTop();
        for (int index = start; index < end; index++) {
            int relative = index - start;
            int x = left + 7 + SELECTOR_STEP * (relative % SELECTOR_COLUMNS);
            int y = top + 17 + SELECTOR_STEP * (relative / SELECTOR_COLUMNS);
            if (inside(mouseX, mouseY, x, y, SELECTOR_STEP, SELECTOR_STEP)) {
                graphics.renderTooltip(font, recipeTooltip(selectorRecipes.get(index)),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
        }
        if (selection != null && inside(mouseX, mouseY, left + 151, top + 71, 18, 18)) {
            graphics.renderTooltip(font, recipeTooltip(selection), Optional.empty(), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, left + 152, top + 90, 16, 16)) {
            graphics.renderTooltip(font, Component.literal("Close").withStyle(ChatFormatting.YELLOW), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, left + 134, top + 108, 16, 16)) {
            graphics.renderTooltip(font, Component.literal("Clear search").withStyle(ChatFormatting.YELLOW),
                    mouseX, mouseY);
        } else if (inside(mouseX, mouseY, left + 8, top + 108, 16, 16)) {
            graphics.renderTooltip(font, Component.literal("Press ENTER to toggle focus")
                    .withStyle(ChatFormatting.ITALIC), mouseX, mouseY);
        }
    }

    private static List<Component> recipeTooltip(CrucibleRecipes.Recipe recipe) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(recipe.translationKey()).withStyle(ChatFormatting.YELLOW));
        if (hasShiftDown()) {
            lines.add(Component.literal("Internal: " + recipe.translationKey()).withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.literal("Input:").withStyle(ChatFormatting.BOLD));
        for (FoundryMaterial.MaterialAmount input : recipe.inputs()) lines.add(materialAmount(input));
        lines.add(Component.literal("Output:").withStyle(ChatFormatting.BOLD));
        for (FoundryMaterial.MaterialAmount output : recipe.outputs()) lines.add(materialAmount(output));
        return lines;
    }

    private static Component materialAmount(FoundryMaterial.MaterialAmount amount) {
        return Component.translatable("hbmmat." + amount.material().id())
                .append(": " + formatAmount(amount.amount()));
    }

    private static String formatAmount(int amount) {
        if (hasShiftDown()) return amount * 2 + "mB";
        List<String> parts = new ArrayList<>(4);
        int blocks = amount / FoundryMaterial.BLOCK;
        amount -= blocks * FoundryMaterial.BLOCK;
        int ingots = amount / FoundryMaterial.INGOT;
        amount -= ingots * FoundryMaterial.INGOT;
        int nuggets = amount / FoundryMaterial.NUGGET;
        amount -= nuggets * FoundryMaterial.NUGGET;
        if (blocks > 0) parts.add(blocks + (blocks == 1 ? " Block" : " Blocks"));
        if (ingots > 0) parts.add(ingots + (ingots == 1 ? " Ingot" : " Ingots"));
        if (nuggets > 0) parts.add(nuggets + (nuggets == 1 ? " Nugget" : " Nuggets"));
        if (amount > 0) parts.add(amount + (amount == 1 ? " Quantum" : " Quanta"));
        return String.join(" ", parts);
    }

    private List<Component> moltenTooltip(boolean recipe) {
        List<Component> lines = new ArrayList<>();
        var blockEntity = menu.blockEntity();
        if (blockEntity == null || (recipe ? menu.recipeTotal() : menu.wasteTotal()) == 0) {
            lines.add(Component.literal("Empty").withStyle(ChatFormatting.RED));
            return lines;
        }
        for (FoundryMaterial material : FoundryMaterial.values()) {
            int amount = recipe ? blockEntity.recipeAmount(material) : blockEntity.wasteAmount(material);
            if (amount > 0) lines.add(Component.translatable("hbmmat." + material.id())
                    .append(": " + formatAmount(amount)).withStyle(ChatFormatting.YELLOW));
        }
        return lines;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        int progress = menu.progress() * 33 / 20000;
        if (progress > 0) graphics.blit(TEXTURE, leftPos + 126, topPos + 82, 176, 0, progress, 5, 256, 256);
        int heat = menu.heat() * 33 / 100000;
        if (heat > 0) graphics.blit(TEXTURE, leftPos + 126, topPos + 91, 176, 5, heat, 5, 256, 256);
        CrucibleRecipes.Recipe recipe = CrucibleRecipes.byId(menu.selectedRecipe());
        ItemStack icon = recipe == null ? ItemStack.EMPTY : recipe.icon();
        graphics.renderItem(icon.isEmpty() ? new ItemStack(ModItems.BLUEPRINTS.get()) : icon,
                leftPos + 107, topPos + 81);
        drawColumn(graphics, false, 17);
        drawColumn(graphics, true, 62);
    }

    private void drawColumn(GuiGraphics graphics, boolean recipe, int x) {
        int amount = recipe ? menu.recipeTotal() : menu.wasteTotal();
        int height = amount * 79 / 10368;
        if (height <= 0) return;
        int color = recipe ? 0xD0D06020 : 0xD0805030;
        graphics.fill(leftPos + x, topPos + 97 - height, leftPos + x + 34, topPos + 97, color);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0xFFFFFF, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selecting) return selectorMouseClicked(mouseX, mouseY, button);
        if (inside(mouseX, mouseY, leftPos + 106, topPos + 80, 18, 18)) {
            openSelector();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean selectorMouseClicked(double mouseX, double mouseY, int button) {
        int left = selectorLeft();
        int top = selectorTop();
        if (inside(mouseX, mouseY, left + 152, top + 18, 16, 16)) {
            selectorPage = Math.max(0, selectorPage - 1);
            click();
            return true;
        }
        if (inside(mouseX, mouseY, left + 152, top + 36, 16, 16)) {
            selectorPage = Math.min(maxSelectorPage(), selectorPage + 1);
            click();
            return true;
        }
        if (inside(mouseX, mouseY, left + 134, top + 108, 16, 16)) {
            search.setValue("");
            setSearchFocused(true);
            return true;
        }
        int start = selectorPage * SELECTOR_COLUMNS;
        int end = Math.min(start + SELECTOR_VISIBLE, selectorRecipes.size());
        for (int index = start; index < end; index++) {
            int relative = index - start;
            int x = left + 7 + SELECTOR_STEP * (relative % SELECTOR_COLUMNS);
            int y = top + 17 + SELECTOR_STEP * (relative / SELECTOR_COLUMNS);
            if (!inside(mouseX, mouseY, x, y, SELECTOR_STEP, SELECTOR_STEP)) continue;
            int clicked = selectorRecipes.get(index).id();
            selectorSelection = clicked == selectorSelection ? CrucibleRecipes.NONE : clicked;
            click();
            return true;
        }
        if (inside(mouseX, mouseY, left + 151, top + 71, 18, 18)
                && selectorSelection != CrucibleRecipes.NONE) {
            selectorSelection = CrucibleRecipes.NONE;
            click();
            return true;
        }
        if (inside(mouseX, mouseY, left + 152, top + 90, 16, 16)) {
            click();
            closeSelector();
            return true;
        }
        if (search.mouseClicked(mouseX, mouseY, button)) {
            setSearchFocused(true);
            return true;
        }
        setSearchFocused(false);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!selecting) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (scrollY > 0D) selectorPage = Math.max(0, selectorPage - 1);
        if (scrollY < 0D) selectorPage = Math.min(maxSelectorPage(), selectorPage + 1);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!selecting) return super.keyPressed(keyCode, scanCode, modifiers);
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            setSearchFocused(!search.isFocused());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                || minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            closeSelector();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) selectorPage--;
        else if (keyCode == GLFW.GLFW_KEY_DOWN) selectorPage++;
        else if (keyCode == GLFW.GLFW_KEY_PAGE_UP) selectorPage -= 5;
        else if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) selectorPage += 5;
        else if (keyCode == GLFW.GLFW_KEY_HOME) selectorPage = 0;
        else if (keyCode == GLFW.GLFW_KEY_END) selectorPage = maxSelectorPage();
        else return search.keyPressed(keyCode, scanCode, modifiers);
        selectorPage = Math.clamp(selectorPage, 0, maxSelectorPage());
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return selecting ? search.charTyped(codePoint, modifiers) : super.charTyped(codePoint, modifiers);
    }

    private void setSearchFocused(boolean focused) {
        search.setFocused(focused);
        setFocused(focused ? search : null);
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1F));
        }
    }

    private int selectorLeft() {
        return (width - SELECTOR_WIDTH) / 2;
    }

    private int selectorTop() {
        return (height - SELECTOR_HEIGHT) / 2;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
