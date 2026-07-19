package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.ChemicalPlantBlockEntity;
import com.hbm.ntm.inventory.ChemicalPlantMenu;
import com.hbm.ntm.item.BlueprintItem;
import com.hbm.ntm.network.ChemicalPlantSelectPayload;
import com.hbm.ntm.recipe.ChemicalPlantRecipes;
import com.hbm.ntm.recipe.ChemicalPlantRecipes.ChemicalRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ChemicalPlantScreen extends AbstractContainerScreen<ChemicalPlantMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/processing/gui_chemplant.png");
    private static final ResourceLocation SELECTOR_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/processing/gui_recipe_selector.png");
    private static final int SELECTOR_WIDTH = 176;
    private static final int SELECTOR_HEIGHT = 132;
    private static final int SELECTOR_COLUMNS = 8;
    private static final int SELECTOR_VISIBLE = 40;
    private static final int SELECTOR_STEP = 18;

    private final List<ChemicalRecipe> availableRecipes = new ArrayList<>();
    private final List<ChemicalRecipe> selectorRecipes = new ArrayList<>();
    private EditBox search;
    private boolean selecting;
    private int selectorPage;
    private ResourceLocation selectorSelection;

    public ChemicalPlantScreen(ChemicalPlantMenu menu, Inventory inventory, Component title) {
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
                : BlueprintItem.pool(menu.blockEntity().getItem(ChemicalPlantBlockEntity.BLUEPRINT));
        for (ChemicalRecipe recipe : ChemicalPlantRecipes.all()) {
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
        PacketDistributor.sendToServer(new ChemicalPlantSelectPayload(selectorSelection));
        selecting = false;
        search.visible = false;
        search.active = false;
        search.setFocused(false);
        setFocused(null);
    }

    private void filterSelectorRecipes(String value) {
        selectorRecipes.clear();
        String needle = value.toLowerCase(Locale.ROOT);
        for (ChemicalRecipe recipe : availableRecipes) {
            if (needle.isEmpty() || recipeSearchText(recipe).contains(needle)) selectorRecipes.add(recipe);
        }
        selectorPage = 0;
    }

    private static String recipeSearchText(ChemicalRecipe recipe) {
        StringBuilder text = new StringBuilder(recipe.name()).append(' ')
                .append(Component.translatable("recipe.hbm." + recipe.name()).getString()).append(' ')
                .append(recipe.icon().getHoverName().getString());
        for (var input : recipe.itemInputs()) text.append(' ').append(input.display().getHoverName().getString());
        for (var input : recipe.fluidInputs()) text.append(' ')
                .append(BuiltInRegistries.FLUID.getKey(input.fluid().get()));
        for (ItemStack output : recipe.itemOutputs()) text.append(' ').append(output.getHoverName().getString());
        for (var output : recipe.fluidOutputs()) text.append(' ')
                .append(BuiltInRegistries.FLUID.getKey(output.fluid().get()));
        return text.toString().toLowerCase(Locale.ROOT);
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
        if (isHovering(152, 18, 16, 61, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(Component.literal(menu.power() + "/" + menu.maxPower() + " HE")),
                    Optional.empty(), mouseX, mouseY);
        }
        if (menu.blockEntity() != null) for (int lane = 0; lane < 3; lane++) {
            if (isHovering(8 + lane * 18, 18, 16, 34, mouseX, mouseY)) {
                var tank = menu.blockEntity().inputTank(lane);
                graphics.renderTooltip(font, List.of(Component.literal(tank.getFluidAmount() + "/24000 mB")),
                        Optional.empty(), mouseX, mouseY);
            }
            if (isHovering(80 + lane * 18, 18, 16, 34, mouseX, mouseY)) {
                var tank = menu.blockEntity().outputTank(lane);
                graphics.renderTooltip(font, List.of(Component.literal(tank.getFluidAmount() + "/24000 mB")),
                        Optional.empty(), mouseX, mouseY);
            }
        }
        if (isHovering(7, 125, 18, 18, mouseX, mouseY)) {
            ChemicalRecipe recipe = menu.blockEntity() == null ? null : menu.blockEntity().selectedRecipe();
            if (recipe == null) {
                graphics.renderTooltip(font, Component.translatable("gui.recipe.setRecipe"), mouseX, mouseY);
            } else {
                graphics.renderTooltip(font, recipeTooltip(recipe), Optional.empty(), mouseX, mouseY);
            }
        }
    }

    private void renderRecipeSelector(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Do not call AbstractContainerScreen#renderBackground here: it also invokes renderBg and
        // leaves the machine's selected-recipe icon sitting in the selector grid.
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
            ChemicalRecipe recipe = selectorRecipes.get(index);
            if (recipe.id().equals(selectorSelection)) {
                graphics.blit(SELECTOR_TEXTURE, x, y, 192, 0,
                        SELECTOR_STEP, SELECTOR_STEP, 256, 256);
            }
            graphics.renderItem(recipe.icon(), x + 1, y + 1);
        }

        ChemicalRecipe selection = selectorSelection == null ? null : ChemicalPlantRecipes.get(selectorSelection);
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
                                        int start, int end, ChemicalRecipe selection) {
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

    private static List<Component> recipeTooltip(ChemicalRecipe recipe) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("recipe.hbm." + recipe.name()));
        for (var input : recipe.itemInputs()) {
            lines.add(Component.literal(input.count() + "x " + input.display().getHoverName().getString())
                    .withStyle(ChatFormatting.GRAY));
        }
        for (var input : recipe.fluidInputs()) {
            lines.add(Component.literal(input.amount() + "mB "
                    + BuiltInRegistries.FLUID.getKey(input.fluid().get())).withStyle(ChatFormatting.AQUA));
        }
        lines.add(Component.literal(recipe.duration() + " ticks, " + recipe.power() + " HE/t")
                .withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        int powerHeight = (int) Math.min(61L, menu.power() * 61L / Math.max(menu.maxPower(), 1L));
        if (powerHeight > 0) graphics.blit(TEXTURE, leftPos + 152, topPos + 79 - powerHeight,
                176, 61 - powerHeight, 16, powerHeight, 256, 256);
        int progressWidth = (int) Math.ceil(70D * menu.progress());
        if (progressWidth > 0) graphics.blit(TEXTURE, leftPos + 62, topPos + 126,
                176, 61, progressWidth, 16, 256, 256);
        ChemicalRecipe recipe = menu.blockEntity() == null ? null : menu.blockEntity().selectedRecipe();
        if (recipe != null) graphics.renderItem(recipe.icon(), leftPos + 8, topPos + 126);
        else graphics.renderItem(new ItemStack(com.hbm.ntm.registry.ModItems.BLUEPRINTS.get()),
                leftPos + 8, topPos + 126);
        if (menu.blockEntity() != null) for (int lane = 0; lane < 3; lane++) {
            drawTank(graphics, menu.blockEntity().inputTank(lane).getFluid(), leftPos + 8 + lane * 18, topPos + 18);
            drawTank(graphics, menu.blockEntity().outputTank(lane).getFluid(), leftPos + 80 + lane * 18, topPos + 18);
        }
    }

    private void drawTank(GuiGraphics graphics, net.neoforged.neoforge.fluids.FluidStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        int height = Math.max(1, stack.getAmount() * 34 / 24_000);
        int color = 0xB0FF3300;
        for (com.hbm.ntm.item.FluidIdentifierItem.Selection selection
                : com.hbm.ntm.item.FluidIdentifierItem.Selection.values()) {
            if (selection.accepts(stack.getFluid())) {
                color = 0xB0000000 | selection.color();
                break;
            }
        }
        graphics.fill(x, y + 34 - height, x + 16, y + 34, color);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 70 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selecting) return selectorMouseClicked(mouseX, mouseY, button);
        if (inside(mouseX, mouseY, leftPos + 7, topPos + 125, 18, 18)) {
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
