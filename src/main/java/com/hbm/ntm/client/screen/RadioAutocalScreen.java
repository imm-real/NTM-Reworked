package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.RadioAutocalBlockEntity;
import com.hbm.ntm.inventory.RadioAutocalMenu;
import com.hbm.ntm.network.RadioAutocalPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class RadioAutocalScreen extends AbstractContainerScreen<RadioAutocalMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/machine/gui_rtty_autocal.png");

    public RadioAutocalScreen(RadioAutocalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 170;
        imageHeight = 138;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (menu.blockEntity() == null) {
            onClose();
            return;
        }
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltips(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        RadioAutocalBlockEntity autocal = menu.blockEntity();
        if (autocal == null) return;
        if (autocal.isOn()) graphics.blit(TEXTURE, leftPos + 8, topPos + 36, 170, 0, 18, 18, 256, 256);
        if (!autocal.ignoreError()) {
            graphics.blit(TEXTURE, leftPos + 28, topPos + 36, 170, 18, 18, 18, 256, 256);
        }
        if (!autocal.autoReboot()) {
            graphics.blit(TEXTURE, leftPos + 48, topPos + 36, 170, 36, 18, 18, 256, 256);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        RadioAutocalBlockEntity autocal = menu.blockEntity();
        if (autocal == null) return;
        for (int index = 0; index < 6; index++) {
            String line = autocal.history(index);
            if (!line.isEmpty()) graphics.drawString(font, line, 7, 73 + index * 10, 0x00FF00, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) mouseX - leftPos;
        int y = (int) mouseY - topPos;
        String command = null;
        String payload = "";

        if (inside(x, y, 8, 36, 18, 18)) command = "on";
        if (inside(x, y, 28, 36, 18, 18)) command = "ignore";
        if (inside(x, y, 48, 36, 18, 18)) command = "auto";
        if (inside(x, y, 84, 36, 18, 18)) {
            String script = uploadProgram();
            if (script != null) {
                command = "upload";
                payload = script;
            }
        }
        if (inside(x, y, 104, 36, 18, 18)) openProgram();
        if (inside(x, y, 144, 36, 18, 18)) openDocumentation();

        if (command != null) {
            send(command, payload);
            clickSound();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void send(String command, String payload) {
        RadioAutocalBlockEntity autocal = menu.blockEntity();
        if (autocal == null) return;
        PacketDistributor.sendToServer(new RadioAutocalPayload(
                autocal.getBlockPos(), command, payload));
    }

    private String uploadProgram() {
        try {
            Path script = programFile();
            if (Files.notExists(script)) {
                Files.createDirectories(script.getParent());
                Files.createFile(script);
                script.toFile().setExecutable(false);
                return null;
            }
            return Files.readString(script, StandardCharsets.UTF_8);
        } catch (Throwable exception) {
            HbmNtm.LOGGER.error("Couldn't read AUTOCAL program", exception);
            return null;
        }
    }

    private void openProgram() {
        try {
            Path script = programFile();
            Files.createDirectories(script.getParent());
            if (Files.notExists(script)) Files.createFile(script);
            script.toFile().setExecutable(false);
            Util.getPlatform().openFile(script.toFile());
        } catch (Throwable exception) {
            HbmNtm.LOGGER.error("Couldn't open AUTOCAL program", exception);
        }
    }

    private void openDocumentation() {
        try {
            Path folder = uploadFolder();
            Path documentation = folder.resolve("documentation_v1.1.md");
            Files.createDirectories(folder);
            if (Files.notExists(documentation)) {
                try (InputStream stream = RadioAutocalScreen.class.getResourceAsStream(
                        "/assets/hbm/autocal_documentation.md")) {
                    if (stream == null) throw new IOException("Missing AUTOCAL documentation");
                    Files.copy(stream, documentation);
                }
            }
            Util.getPlatform().openFile(documentation.toFile());
        } catch (Throwable exception) {
            HbmNtm.LOGGER.error("Couldn't open AUTOCAL documentation", exception);
        }
    }

    private Path programFile() {
        return uploadFolder().resolve("script.txt");
    }

    private Path uploadFolder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("hbmComputerUpload");
    }

    private void clickSound() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1F));
        }
    }

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = mouseX - leftPos;
        int y = mouseY - topPos;
        if (inside(x, y, 8, 36, 18, 18)) {
            singleTooltip(graphics, mouseX, mouseY, "ON/OFF", ChatFormatting.RED);
        } else if (inside(x, y, 28, 36, 18, 18)) {
            tooltip(graphics, mouseX, mouseY, "Ignore Errors",
                    "Skips instructions that error,",
                    "leaving the computer turned on.",
                    "May cause unintended behavior",
                    "and inconsistencies.");
        } else if (inside(x, y, 48, 36, 18, 18)) {
            tooltip(graphics, mouseX, mouseY, "Automatic Reboot",
                    "Restarts the computer automatically when",
                    "the program stops due to an error",
                    "or after finishing.");
        } else if (inside(x, y, 84, 36, 18, 18)) {
            singleTooltip(graphics, mouseX, mouseY, "Upload Program", ChatFormatting.BLUE);
        } else if (inside(x, y, 104, 36, 18, 18)) {
            singleTooltip(graphics, mouseX, mouseY, "Open Program File", ChatFormatting.BLUE);
        } else if (inside(x, y, 124, 36, 18, 18)) {
            graphics.renderTooltip(font, List.of(
                    Component.literal("Download Program").withStyle(ChatFormatting.BLUE),
                    Component.literal("Currently unsupported!").withStyle(ChatFormatting.RED)),
                    Optional.empty(), mouseX, mouseY);
        } else if (inside(x, y, 144, 36, 18, 18)) {
            singleTooltip(graphics, mouseX, mouseY, "Open Documentation", ChatFormatting.BLUE);
        }
    }

    private void tooltip(GuiGraphics graphics, int mouseX, int mouseY,
                         String title, String... lines) {
        java.util.ArrayList<Component> tooltip = new java.util.ArrayList<>();
        tooltip.add(Component.literal(title).withStyle(ChatFormatting.RED));
        for (String line : lines) tooltip.add(Component.literal(line));
        graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    private void singleTooltip(GuiGraphics graphics, int mouseX, int mouseY,
                               String text, ChatFormatting color) {
        graphics.renderTooltip(font, Component.literal(text).withStyle(color), mouseX, mouseY);
    }

    private static boolean inside(int x, int y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y > top && y <= top + height;
    }
}
