package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.AssemblyMachineBlockEntity;
import com.hbm.ntm.inventory.AssemblyMachineMenu;
import com.hbm.ntm.item.BlueprintItem;
import com.hbm.ntm.network.AssemblySelectRecipePayload;
import com.hbm.ntm.recipe.AssemblyClientRecipes;
import com.hbm.ntm.recipe.AssemblyRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class AssemblyMachineScreen extends AbstractContainerScreen<AssemblyMachineMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/processing/gui_assembler.png");
    private static final ResourceLocation SELECTOR_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/processing/gui_recipe_selector.png");
    private static final int SELECTOR_WIDTH = 176;
    private static final int SELECTOR_HEIGHT = 132;
    private static final int SELECTOR_COLUMNS = 8;
    private static final int SELECTOR_VISIBLE = 40;
    private static final int SELECTOR_STEP = 18;

    private final List<AssemblyRecipe> availableRecipes = new ArrayList<>();
    private final List<AssemblyRecipe> selectorRecipes = new ArrayList<>();
    private EditBox search;
    private boolean selecting;
    private int selectorPage;
    private ResourceLocation selectorSelection;

    public AssemblyMachineScreen(AssemblyMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 256;
        inventoryLabelX = 8;
        inventoryLabelY = 162;
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
        selectorSelection = menu.blockEntity() == null ? null : menu.blockEntity().recipeId();
        availableRecipes.clear();
        String pool = menu.blockEntity() == null ? ""
                : BlueprintItem.pool(menu.blockEntity().getItem(AssemblyMachineBlockEntity.BLUEPRINT));
        for (AssemblyRecipe recipe : AssemblyClientRecipes.all()) {
            if (recipe.pools().isEmpty() || !pool.isEmpty() && recipe.pools().contains(pool)) {
                availableRecipes.add(recipe);
            }
        }
        search.visible = true;
        search.active = true;
        search.setFocused(false);
        setFocused(null);
        search.setValue("");
        filterSelectorRecipes("");
    }

    private void closeSelector() {
        PacketDistributor.sendToServer(new AssemblySelectRecipePayload(selectorSelection));
        selecting = false;
        search.visible = false;
        search.active = false;
        search.setFocused(false);
        setFocused(null);
    }

    private void filterSelectorRecipes(String value) {
        selectorRecipes.clear();
        String needle = value.toLowerCase(Locale.ROOT);
        for (AssemblyRecipe recipe : availableRecipes) {
            if (needle.isEmpty() || recipeSearchText(recipe).contains(needle)) selectorRecipes.add(recipe);
        }
        selectorPage = 0;
    }

    private static String recipeSearchText(AssemblyRecipe recipe) {
        StringBuilder text = new StringBuilder(recipe.name()).append(' ')
                .append(recipe.output().getHoverName().getString());
        for (AssemblyRecipe.Input input : recipe.inputs()) {
            text.append(' ').append(input.display().getHoverName().getString());
        }
        recipe.fluidInput().ifPresent(fluid -> text.append(' ').append(fluid.fluid()));
        recipe.fluidOutput().ifPresent(fluid -> text.append(' ').append(fluid.fluid()));
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private int maxSelectorPage() {
        return Math.max(0, (selectorRecipes.size() - SELECTOR_VISIBLE + SELECTOR_COLUMNS - 1)
                / SELECTOR_COLUMNS);
    }

    private AssemblyRecipe selectedRecipe() {
        AssemblyMachineBlockEntity machine = menu.blockEntity();
        return machine == null || machine.recipeId() == null ? null : AssemblyClientRecipes.get(machine.recipeId());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (selecting) {
            renderRecipeSelector(graphics, mouseX, mouseY, partialTick);
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(152, 18, 16, 61, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(Component.literal(menu.power() + "/" + menu.maxPower() + " HE")),
                    Optional.empty(), mouseX, mouseY);
        }
        if (isHovering(7, 125, 18, 18, mouseX, mouseY)) {
            AssemblyRecipe recipe = selectedRecipe();
            if (recipe == null) {
                graphics.renderTooltip(font, Component.translatable("gui.recipe.setRecipe"), mouseX, mouseY);
            } else {
                graphics.renderTooltip(font, recipeTooltip(recipe), Optional.empty(), mouseX, mouseY);
            }
        }
    }

    private void renderRecipeSelector(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // AbstractContainerScreen#renderBackground also calls renderBg, which would redraw the
        // machine's selected-recipe icon underneath this standalone selector.
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
            AssemblyRecipe recipe = selectorRecipes.get(index);
            if (recipe.id().equals(selectorSelection)) {
                graphics.blit(SELECTOR_TEXTURE, x, y, 192, 0,
                        SELECTOR_STEP, SELECTOR_STEP, 256, 256);
            }
            graphics.renderItem(recipe.icon(), x + 1, y + 1);
        }

        AssemblyRecipe selection = selectorSelection == null ? null : AssemblyClientRecipes.get(selectorSelection);
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
                                        int start, int end, AssemblyRecipe selection) {
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

    private static List<Component> recipeTooltip(AssemblyRecipe recipe) {
        List<Component> lines = new ArrayList<>();
        lines.add(recipe.output().getHoverName());
        for (AssemblyRecipe.Input input : recipe.inputs()) {
            lines.add(Component.literal(input.count() + "x " + input.display().getHoverName().getString())
                    .withStyle(ChatFormatting.GRAY));
        }
        recipe.fluidInput().ifPresent(fluid -> lines.add(Component.literal(
                fluid.amount() + "mB " + fluid.fluid()).withStyle(ChatFormatting.AQUA)));
        lines.add(Component.literal(recipe.duration() + " ticks, " + recipe.power() + " HE/t")
                .withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        long max = Math.max(1L, menu.maxPower());
        int powerHeight = (int) Math.min(61L, menu.power() * 61L / max);
        if (powerHeight > 0) graphics.blit(TEXTURE, leftPos + 152, topPos + 79 - powerHeight,
                176, 61 - powerHeight, 16, powerHeight, 256, 256);
        int progressWidth = (int) Math.ceil(70D * menu.progress());
        if (progressWidth > 0) graphics.blit(TEXTURE, leftPos + 62, topPos + 126,
                176, 61, progressWidth, 16, 256, 256);
        AssemblyRecipe recipe = selectedRecipe();
        if (recipe != null) {
            if (menu.active()) {
                graphics.blit(TEXTURE, leftPos + 51, topPos + 121, 195, 0, 3, 6, 256, 256);
                graphics.blit(TEXTURE, leftPos + 56, topPos + 121, 195, 0, 3, 6, 256, 256);
            } else {
                graphics.blit(TEXTURE, leftPos + 51, topPos + 121, 192, 0, 3, 6, 256, 256);
                if (menu.power() >= recipe.power()) graphics.blit(TEXTURE, leftPos + 56, topPos + 121,
                        192, 0, 3, 6, 256, 256);
            }
            graphics.renderItem(recipe.icon(), leftPos + 8, topPos + 126);
            for (int lane = 0; lane < recipe.inputs().size(); lane++) {
                Slot slot = menu.getSlot(4 + lane);
                if (!slot.hasItem()) {
                    ItemStack display = recipe.inputs().get(lane).display();
                    graphics.renderItem(display, leftPos + slot.x, topPos + slot.y);
                    graphics.renderItemDecorations(font, display, leftPos + slot.x, topPos + slot.y);
                }
            }
        } else {
            graphics.renderItem(new ItemStack(com.hbm.ntm.registry.ModItems.BLUEPRINTS.get()), leftPos + 8, topPos + 126);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 70 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selecting) return selectorMouseClicked(mouseX, mouseY);
        if (inside(mouseX, mouseY, leftPos + 7, topPos + 125, 18, 18)) {
            openSelector();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean selectorMouseClicked(double mouseX, double mouseY) {
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
            ResourceLocation clicked = selectorRecipes.get(index).id();
            selectorSelection = clicked.equals(selectorSelection) ? null : clicked;
            click();
            return true;
        }
        if (inside(mouseX, mouseY, left + 151, top + 71, 18, 18) && selectorSelection != null) {
            selectorSelection = null;
            click();
            return true;
        }
        if (inside(mouseX, mouseY, left + 152, top + 90, 16, 16)) {
            click();
            closeSelector();
            return true;
        }
        if (search.mouseClicked(mouseX, mouseY, 0)) {
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
