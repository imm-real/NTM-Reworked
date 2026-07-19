package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.guide.GuideBookContent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Two-page reader for the starter manual. */
public final class GuideBookScreen extends Screen {
    private static final ResourceLocation BOOK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/book/book.png");
    private static final ResourceLocation COVER_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/book/book_cover.png");
    private static final int BOOK_WIDTH = 272;
    private static final int BOOK_HEIGHT = 182;
    private static final int PAGE_WIDTH = 100;
    private static final int PAGE_STEP = 130;
    private static final int LEFT_ARROW_X = 24;
    private static final int RIGHT_ARROW_X = 230;
    private static final int ARROW_Y = 155;
    private static final int ARROW_WIDTH = 18;
    private static final int ARROW_HEIGHT = 10;

    /** -1 is the cover; non-negative values are two-page spread indices. */
    private int spread = -1;

    public GuideBookScreen() {
        super(Component.translatable("item.hbm.book_guide"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = (width - BOOK_WIDTH) / 2;
        int top = (height - BOOK_HEIGHT) / 2;

        if (spread < 0) {
            graphics.blit(COVER_TEXTURE, left, top, 0, 0, BOOK_WIDTH, BOOK_HEIGHT, 512, 512);
            renderCover(graphics, left, top);
            return;
        }

        graphics.blit(BOOK_TEXTURE, left, top, 0, 0, BOOK_WIDTH, BOOK_HEIGHT, 512, 512);
        renderPage(graphics, spread * 2, left + 20, top, mouseX, mouseY);
        renderPage(graphics, spread * 2 + 1, left + 20 + PAGE_STEP, top, mouseX, mouseY);
        renderArrows(graphics, left, top, mouseX, mouseY);
        drawCenteredScaled(graphics, Component.translatable("guide.hbm.controls"),
                left + BOOK_WIDTH / 2, top + BOOK_HEIGHT + 3, 0.65F, 0xE0E0E0, true);
    }

    private void renderCover(GuiGraphics graphics, int left, int top) {
        int center = left + BOOK_WIDTH / 2;
        drawCenteredScaled(graphics, Component.translatable("guide.hbm.cover.line1"),
                center, top + 49, 0.9F, 0xFECE00, false);
        drawCenteredScaled(graphics, Component.translatable("guide.hbm.cover.line2"),
                center, top + 61, 0.82F, 0xFECE00, false);
        drawCenteredScaled(graphics, Component.translatable("guide.hbm.cover.line3"),
                center, top + 73, 0.95F, 0xFECE00, false);
        drawCenteredScaled(graphics, Component.translatable("guide.hbm.cover.edition"),
                center, top + 105, 0.65F, 0xD8D8D8, false);
        drawCenteredScaled(graphics, Component.translatable("guide.hbm.cover.open"),
                center, top + 143, 0.62F, 0xB8B8B8, false);
    }

    private void renderPage(GuiGraphics graphics, int pageIndex, int pageLeft, int top,
                            int mouseX, int mouseY) {
        List<GuideBookContent.Page> pages = GuideBookContent.pages();
        if (pageIndex >= pages.size()) return;
        GuideBookContent.Page page = pages.get(pageIndex);

        Component title = Component.translatable(page.titleKey());
        float titleScale = Math.min(0.88F, 96.0F / Math.max(font.width(title), 1));
        drawCenteredScaled(graphics, title, pageLeft + PAGE_WIDTH / 2, top + 20,
                titleScale, 0x404040, false);

        List<ItemStack> icons = page.iconIds().stream()
                .map(GuideBookScreen::resolveIcon)
                .filter(stack -> !stack.isEmpty())
                .limit(4)
                .toList();
        renderBody(graphics, Component.translatable(page.bodyKey()), pageLeft, top, !icons.isEmpty());
        renderIcons(graphics, icons, pageLeft, top, mouseX, mouseY);

        String number = (pageIndex + 1) + "/" + pages.size();
        int numberX = pageIndex % 2 == 0
                ? pageLeft + 24
                : pageLeft + PAGE_WIDTH - 24 - font.width(number);
        graphics.drawString(font, number, numberX, top + 156, 0x404040, false);
    }

    private void renderBody(GuiGraphics graphics, Component body, int pageLeft, int top, boolean hasIcons) {
        float scale = 0.76F;
        int availableHeight = (hasIcons ? 125 : 145) - 35;
        List<FormattedCharSequence> lines = font.split(body, (int) (96 / scale));
        while (scale > 0.58F && lines.size() * 9.0F * scale > availableHeight) {
            scale -= 0.03F;
            lines = font.split(body, (int) (96 / scale));
        }

        graphics.pose().pushPose();
        graphics.pose().translate(pageLeft + 2, top + 35, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        for (int line = 0; line < lines.size(); line++) {
            graphics.drawString(font, lines.get(line), 0, line * 9, 0x404040, false);
        }
        graphics.pose().popPose();
    }

    private void renderIcons(GuiGraphics graphics, List<ItemStack> icons, int pageLeft, int top,
                             int mouseX, int mouseY) {
        if (icons.isEmpty()) return;
        int gap = 5;
        int width = icons.size() * 16 + (icons.size() - 1) * gap;
        int firstX = pageLeft + (PAGE_WIDTH - width) / 2;
        int y = top + 130;
        for (int index = 0; index < icons.size(); index++) {
            ItemStack stack = icons.get(index);
            int x = firstX + index * (16 + gap);
            graphics.renderItem(stack, x, y);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                graphics.renderTooltip(font, stack, mouseX, mouseY);
            }
        }
    }

    private void renderArrows(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        boolean leftHover = inside(mouseX, mouseY, left + LEFT_ARROW_X, top + ARROW_Y,
                ARROW_WIDTH, ARROW_HEIGHT);
        graphics.blit(BOOK_TEXTURE, left + LEFT_ARROW_X, top + ARROW_Y,
                leftHover ? 26 : 3, 207, ARROW_WIDTH, ARROW_HEIGHT, 512, 512);

        if (spread < maxSpread()) {
            boolean rightHover = inside(mouseX, mouseY, left + RIGHT_ARROW_X, top + ARROW_Y,
                    ARROW_WIDTH, ARROW_HEIGHT);
            graphics.blit(BOOK_TEXTURE, left + RIGHT_ARROW_X, top + ARROW_Y,
                    rightHover ? 26 : 3, 194, ARROW_WIDTH, ARROW_HEIGHT, 512, 512);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);
        if (spread < 0) {
            setSpread(0);
            return true;
        }

        int left = (width - BOOK_WIDTH) / 2;
        int top = (height - BOOK_HEIGHT) / 2;
        if (inside(mouseX, mouseY, left + LEFT_ARROW_X, top + ARROW_Y, ARROW_WIDTH, ARROW_HEIGHT)) {
            setSpread(spread - 1);
            return true;
        }
        if (spread < maxSpread()
                && inside(mouseX, mouseY, left + RIGHT_ARROW_X, top + ARROW_Y, ARROW_WIDTH, ARROW_HEIGHT)) {
            setSpread(spread + 1);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D) {
            setSpread(spread + (scrollY < 0.0D ? 1 : -1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            setSpread(spread - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            setSpread(spread + 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            setSpread(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            setSpread(maxSpread());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void setSpread(int next) {
        int clamped = Math.max(-1, Math.min(maxSpread(), next));
        if (clamped == spread) return;
        spread = clamped;
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }
    }

    private int maxSpread() {
        return (GuideBookContent.pages().size() - 1) / 2;
    }

    private static ItemStack resolveIcon(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return ItemStack.EMPTY;
        return BuiltInRegistries.ITEM.getOptional(location)
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
    }

    private void drawCenteredScaled(GuiGraphics graphics, Component text, int centerX, int y,
                                    float scale, int color, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, shadow);
        graphics.pose().popPose();
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
