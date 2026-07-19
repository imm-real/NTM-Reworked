package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.anvil.AnvilRecipes;
import com.hbm.ntm.inventory.AnvilMenu;
import com.hbm.ntm.network.AnvilCraftPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AnvilScreen extends AbstractContainerScreen<AnvilMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/processing/gui_anvil.png");
    private final List<AnvilRecipes.Construction> allRecipes = new ArrayList<>();
    private final List<AnvilRecipes.Construction> recipes = new ArrayList<>();
    private EditBox search;
    private int page;
    private int selected = -1;

    public AnvilScreen(AnvilMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
        inventoryLabelY = imageHeight - 94;
        for (AnvilRecipes.Construction recipe : AnvilRecipes.construction()) {
            if (recipe.validForTier(menu.tier())) allRecipes.add(recipe);
        }
        recipes.addAll(allRecipes);
    }

    @Override
    protected void init() {
        super.init();
        search = new EditBox(font, leftPos + 10, topPos + 109, 84, 14, Component.empty());
        search.setBordered(false);
        search.setMaxLength(25);
        search.setResponder(this::filter);
        addRenderableWidget(search);
    }

    private void filter(String value) {
        String needle = value.toLowerCase(Locale.ROOT);
        recipes.clear();
        for (AnvilRecipes.Construction recipe : allRecipes) {
            if (needle.isEmpty() || searchable(recipe).contains(needle)) recipes.add(recipe);
        }
        page = 0;
        selected = -1;
    }

    private String searchable(AnvilRecipes.Construction recipe) {
        StringBuilder result = new StringBuilder(recipe.icon().getHoverName().getString().toLowerCase(Locale.ROOT));
        for (AnvilRecipes.Input input : recipe.inputs()) result.append(' ')
                .append(input.display().get().getHoverName().getString().toLowerCase(Locale.ROOT));
        return result.toString();
    }

    private int maxPage() { return Math.max(0, (int) Math.ceil((recipes.size() - 10) / 2.0)); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        int start = page * 2;
        for (int i = start; i < Math.min(start + 10, recipes.size()); i++) {
            int relative = i - start;
            int x = leftPos + 17 + 18 * (relative / 2);
            int y = topPos + 72 + 18 * (relative % 2);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                graphics.renderTooltip(font, recipes.get(i).icon(), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        int start = page * 2;
        for (int i = start; i < Math.min(start + 10, recipes.size()); i++) {
            int relative = i - start;
            int x = leftPos + 16 + 18 * (relative / 2);
            int y = topPos + 71 + 18 * (relative % 2);
            AnvilRecipes.Construction recipe = recipes.get(i);
            if (i == selected) graphics.blit(TEXTURE, x, y, 0, 222, 18, 18, 256, 256);
            int overlay = recipe.overlay().ordinal();
            if (overlay > 0) graphics.blit(TEXTURE, x, y, 18 + 18 * overlay, 222, 18, 18, 256, 256);
            graphics.renderItem(recipe.icon(), x + 1, y + 1);
        }
        if (search.isFocused()) graphics.blit(TEXTURE, leftPos + 8, topPos + 108, 168, 222, 88, 16, 256, 256);
        if (selected >= 0 && selected < recipes.size()) renderDetails(graphics, recipes.get(selected));
    }

    private void renderDetails(GuiGraphics graphics, AnvilRecipes.Construction recipe) {
        graphics.blit(TEXTURE, leftPos + 125, topPos + 17, 125, 17, 54, 108, 256, 256);
        int x = leftPos + 130;
        int y = topPos + 25;
        graphics.pose().pushPose();
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        int drawX = x * 2;
        int drawY = y * 2;
        graphics.drawString(font, Component.literal("Inputs:"), drawX, drawY, 0xFFFF55, false);
        drawY += 9;
        for (AnvilRecipes.Input input : recipe.inputs()) {
            ItemStack display = input.display().get();
            int color = countMatching(input) >= input.count() ? 0xFFFFFF : 0xFF5555;
            graphics.drawString(font, Component.literal(input.count() + "x " + display.getHoverName().getString()),
                    drawX, drawY, color, false);
            drawY += 9;
        }
        graphics.drawString(font, Component.literal("Outputs:"), drawX, drawY, 0xFFFF55, false);
        drawY += 9;
        for (AnvilRecipes.Output output : recipe.outputs()) {
            ItemStack stack = output.stack().get();
            String chance = output.chance() < 1.0F ? " (" + Math.round(output.chance() * 100) + "%)" : "";
            graphics.drawString(font, Component.literal(stack.getCount() + "x " + stack.getHoverName().getString() + chance),
                    drawX, drawY, 0xFFFFFF, false);
            drawY += 9;
        }
        graphics.pose().popPose();
    }

    private int countMatching(AnvilRecipes.Input input) {
        int count = 0;
        for (ItemStack stack : menu.playerInventory().items) if (input.matches(stack)) count += stack.getCount();
        return count;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component label = Component.translatable("container.anvil", menu.tier());
        graphics.drawString(font, label, 61 - font.width(label) / 2, 8, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inside(mouseX, mouseY, 7, 71, 9, 36)) { page = Math.max(0, page - 1); click(); return true; }
        if (inside(mouseX, mouseY, 106, 71, 9, 36)) { page = Math.min(maxPage(), page + 1); click(); return true; }
        if (inside(mouseX, mouseY, 52, 53, 18, 18) && selected >= 0 && selected < recipes.size()) {
            long window = Minecraft.getInstance().getWindow().getWindow();
            boolean bulk = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT);
            PacketDistributor.sendToServer(new AnvilCraftPayload(recipes.get(selected).id(), bulk));
            click();
            return true;
        }
        int start = page * 2;
        for (int i = start; i < Math.min(start + 10, recipes.size()); i++) {
            int relative = i - start;
            if (inside(mouseX, mouseY, 16 + 18 * (relative / 2), 71 + 18 * (relative % 2), 18, 18)) {
                selected = selected == i ? -1 : i;
                click();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0) page = Math.max(0, page - 1);
        if (scrollY < 0) page = Math.min(maxPage(), page + 1);
        return true;
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + width
                && mouseY >= topPos + y && mouseY < topPos + y + height;
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
