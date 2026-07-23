package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.RadioTelexBlockEntity;
import com.hbm.ntm.inventory.RadioTelexMenu;
import com.hbm.ntm.network.RadioTelexPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class RadioTelexScreen extends AbstractContainerScreen<RadioTelexMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/machine/gui_telex.png");
    private final String[] txBuffer = {"", "", "", "", ""};
    private EditBox txFrequency;
    private EditBox rxFrequency;
    private boolean textFocus;
    private boolean closeSaved;
    private int cursorLine;

    public RadioTelexScreen(RadioTelexMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 256;
        imageHeight = 244;
        RadioTelexBlockEntity telex = menu.blockEntity();
        if (telex != null) {
            for (int line = 0; line < 5; line++) txBuffer[line] = telex.txLine(line);
            for (int line = 4; line > 0; line--) {
                if (!txBuffer[line].isEmpty()) {
                    cursorLine = line;
                    break;
                }
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        txFrequency = frequencyBox(leftPos + 29, topPos + 110,
                menu.blockEntity() == null ? "" : menu.blockEntity().txChannel());
        rxFrequency = frequencyBox(leftPos + 29, topPos + 224,
                menu.blockEntity() == null ? "" : menu.blockEntity().rxChannel());
        addRenderableWidget(txFrequency);
        addRenderableWidget(rxFrequency);
    }

    private EditBox frequencyBox(int x, int y, String value) {
        EditBox box = new EditBox(font, x, y, 90, 14, Component.empty());
        box.setTextColor(0x00FF00);
        box.setTextColorUneditable(0x00FF00);
        box.setBordered(false);
        box.setMaxLength(10);
        box.setValue(value == null ? "" : value);
        return box;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltips(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        drawTransmitBuffer(graphics);
        drawReceiveBuffer(graphics);
        drawWaveform(graphics);
    }

    private void drawTransmitBuffer(GuiGraphics graphics) {
        for (int line = 0; line < 5; line++) {
            drawTransmitLine(graphics, txBuffer[line], 11 + 14 * line);
            if (textFocus && cursorLine == line && System.currentTimeMillis() % 1000L < 500L) {
                int x = Math.max(11 + 7 * txBuffer[line].length(), 11);
                graphics.drawString(font, "|", leftPos + x, topPos + 11 + 14 * line, 0x00FF00, false);
            }
        }
    }

    private void drawTransmitLine(GuiGraphics graphics, String text, int y) {
        String format = ChatFormatting.RESET.toString();
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            int x = 11 + 7 * index + (7 - font.width(Character.toString(character))) / 2;
            if (character == '\u00a7' && text.length() > index + 1) {
                format = "\u00a7" + text.charAt(index + 1);
                x -= 3;
            }
            String glyph = format + character;
            if (character == RadioTelexBlockEntity.BELL) glyph = ChatFormatting.RED + "B";
            if (character == RadioTelexBlockEntity.PRINT) glyph = ChatFormatting.RED + "P";
            if (character == RadioTelexBlockEntity.CLEAR) glyph = ChatFormatting.RED + "<";
            if (character == RadioTelexBlockEntity.PAUSE) glyph = ChatFormatting.RED + "W";
            graphics.drawString(font, glyph, leftPos + x, topPos + y, 0x00FF00, false);
        }
    }

    private void drawReceiveBuffer(GuiGraphics graphics) {
        RadioTelexBlockEntity telex = menu.blockEntity();
        if (telex == null) return;
        for (int line = 0; line < 5; line++) {
            String text = telex.rxLine(line);
            String format = ChatFormatting.RESET.toString();
            int x = 11;
            for (int index = 0; index < text.length(); index++) {
                char character = text.charAt(index);
                x += (7 - font.width(Character.toString(character))) / 2;
                if (character == '\u00a7' && text.length() > index + 1) {
                    format = "\u00a7" + text.charAt(index + 1);
                    character = ' ';
                } else if (character == '\u00a7') {
                    character = ' ';
                } else if (index > 0 && text.charAt(index - 1) == '\u00a7') {
                    character = ' ';
                    x -= 14;
                }
                graphics.drawString(font, format + character, leftPos + x,
                        topPos + 145 + 14 * line, 0x00FF00, false);
                x += 7;
            }
        }
    }

    private void drawWaveform(GuiGraphics graphics) {
        char seed = menu.blockEntity() == null ? ' ' : menu.blockEntity().sendingChar();
        Random random = new Random(seed);
        double offset = 0D;
        for (int i = 0; i < 48; i++) {
            int fromY = (int) Math.round(topPos + 93.5D + offset);
            if (seed != ' ' && i > 4 && i < 43) offset = Mth.clamp(random.nextGaussian() * 7D, -7D, 7D);
            else offset = 0D;
            int toY = (int) Math.round(topPos + 93.5D + offset);
            int minY = Math.min(fromY, toY);
            int maxY = Math.max(fromY, toY);
            graphics.fill(leftPos + 199 + i, minY, leftPos + 201 + i, maxY + 2, 0xFF00FF00);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        int x = (int) mouseX - leftPos;
        int y = (int) mouseY - topPos;
        textFocus = inside(x, y, 7, 7, 242, 74);
        if (textFocus) {
            txFrequency.setFocused(false);
            rxFrequency.setFocused(false);
        }

        char character = 0;
        String command = null;
        if (inside(x, y, 7, 85, 18, 18)) character = RadioTelexBlockEntity.BELL;
        if (inside(x, y, 27, 85, 18, 18)) character = RadioTelexBlockEntity.PRINT;
        if (inside(x, y, 47, 85, 18, 18)) character = RadioTelexBlockEntity.CLEAR;
        if (inside(x, y, 67, 85, 18, 18)) character = '\u00a7';
        if (inside(x, y, 87, 85, 18, 18)) character = RadioTelexBlockEntity.PAUSE;
        if (inside(x, y, 127, 105, 18, 18) || inside(x, y, 127, 219, 18, 18)) command = "sve";
        if (inside(x, y, 147, 105, 18, 18)) command = "snd";
        if (inside(x, y, 167, 105, 18, 18)) {
            for (int line = 0; line < 5; line++) txBuffer[line] = "";
            command = "";
        }
        if (inside(x, y, 147, 219, 18, 18)) command = "rxprt";
        if (inside(x, y, 167, 219, 18, 18)) command = "rxcls";

        if (command != null) {
            clickSound();
            send(command);
            handled = true;
        }
        if (character != 0) {
            clickSound();
            textFocus = true;
            txFrequency.setFocused(false);
            rxFrequency.setFocused(false);
            submit(character);
            handled = true;
        }
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (txFrequency.isFocused() || rxFrequency.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (textFocus) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                textFocus = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                cursorLine = Math.max(cursorLine - 1, 0);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                cursorLine = Math.min(cursorLine + 1, 4);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !txBuffer[cursorLine].isEmpty()) {
                txBuffer[cursorLine] = txBuffer[cursorLine].substring(0, txBuffer[cursorLine].length() - 1);
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                || minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (textFocus && StringUtil.isAllowedChatCharacter(character)) {
            submit(character);
            return true;
        }
        return super.charTyped(character, modifiers);
    }

    private void submit(char character) {
        if (txBuffer[cursorLine].length() < RadioTelexBlockEntity.LINE_WIDTH) {
            txBuffer[cursorLine] += character;
        }
    }

    @Override
    public void onClose() {
        saveOnClose();
        super.onClose();
    }

    @Override
    public void removed() {
        saveOnClose();
        super.removed();
    }

    private void saveOnClose() {
        if (closeSaved) return;
        closeSaved = true;
        send("");
    }

    private void send(String command) {
        if (txFrequency == null || rxFrequency == null) return;
        RadioTelexBlockEntity telex = menu.blockEntity();
        if (telex == null) return;
        PacketDistributor.sendToServer(new RadioTelexPayload(telex.getBlockPos(), command,
                txBuffer.clone(), txFrequency.getValue(), rxFrequency.getValue()));
    }

    private void clickSound() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1F));
        }
    }

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = mouseX - leftPos;
        int y = mouseY - topPos;
        if (inside(x, y, 7, 85, 18, 18)) tooltip(graphics, mouseX, mouseY, "BELL",
                "Plays a bell when this character is received");
        else if (inside(x, y, 27, 85, 18, 18)) tooltip(graphics, mouseX, mouseY, "PRINT",
                "Forces recipient to print message after transmission ends");
        else if (inside(x, y, 47, 85, 18, 18)) tooltip(graphics, mouseX, mouseY, "CLEAR SCREEN",
                "Wipes message buffer when this character is received");
        else if (inside(x, y, 67, 85, 18, 18)) tooltip(graphics, mouseX, mouseY, "FORMAT",
                "Inserts format character for message formatting");
        else if (inside(x, y, 87, 85, 18, 18)) tooltip(graphics, mouseX, mouseY, "PAUSE",
                "Pauses message transmission for one second");
        else if (inside(x, y, 127, 105, 18, 18) || inside(x, y, 127, 219, 18, 18))
            singleTooltip(graphics, mouseX, mouseY, "SAVE ID", ChatFormatting.GREEN);
        else if (inside(x, y, 147, 105, 18, 18))
            singleTooltip(graphics, mouseX, mouseY, "SEND MESSAGE", ChatFormatting.YELLOW);
        else if (inside(x, y, 167, 105, 18, 18))
            singleTooltip(graphics, mouseX, mouseY, "DELETE MESSAGE BUFFER", ChatFormatting.RED);
        else if (inside(x, y, 147, 219, 18, 18))
            singleTooltip(graphics, mouseX, mouseY, "PRINT MESSAGE", ChatFormatting.AQUA);
        else if (inside(x, y, 167, 219, 18, 18))
            singleTooltip(graphics, mouseX, mouseY, "CLEAR SCREEN", ChatFormatting.RED);
    }

    private void tooltip(GuiGraphics graphics, int mouseX, int mouseY, String title, String description) {
        graphics.renderTooltip(font, List.of(Component.literal(title).withStyle(ChatFormatting.GOLD),
                Component.literal(description)), Optional.empty(), mouseX, mouseY);
    }

    private void singleTooltip(GuiGraphics graphics, int mouseX, int mouseY,
                               String text, ChatFormatting color) {
        graphics.renderTooltip(font, Component.literal(text).withStyle(color), mouseX, mouseY);
    }

    private static boolean inside(int x, int y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y > top && y <= top + height;
    }
}
