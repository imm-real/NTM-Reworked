package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.NukeCustomMenu;
import com.hbm.ntm.nuclear.CustomNukeExplosion;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Custom nuke schematic and seven increasingly concerning tooltips. */
public final class NukeCustomScreen extends AbstractContainerScreen<NukeCustomMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/weapon/gun_bomb_schematic.png");

    private CustomNukeExplosion.Yields yields = new CustomNukeExplosion.Yields(0, 0, 0, 0, 0, 0, 0);

    public NukeCustomScreen(NukeCustomMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
        inventoryLabelX = 8;
        inventoryLabelY = imageHeight - 96 + 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        yields = menu.yields();
        super.render(graphics, mouseX, mouseY, partialTick);

        drawStageTooltip(graphics, mouseX, mouseY, 16, tntTooltip());
        drawStageTooltip(graphics, mouseX, mouseY, 34, nukeTooltip());
        drawStageTooltip(graphics, mouseX, mouseY, 52, hydroTooltip());
        drawStageTooltip(graphics, mouseX, mouseY, 70, amatTooltip());
        drawStageTooltip(graphics, mouseX, mouseY, 88, saltedTooltip());
        drawStageTooltip(graphics, mouseX, mouseY, 106, schrabTooltip());
        drawStageTooltip(graphics, mouseX, mouseY, 142, iceCreamTooltip());
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        // Highest stage wins: euph > schrab > amat > hydro > nuke > tnt.
        if (yields.euph() > 0) overlay(graphics, 142, 176, 108);
        else if (yields.schrab() > 0) overlay(graphics, 106, 176, 90);
        else if (yields.amat() > 0) overlay(graphics, 70, 176, 54);
        else if (yields.hydro() > 0) overlay(graphics, 52, 176, 36);
        else if (yields.nuke() > 0) overlay(graphics, 34, 176, 18);
        else if (yields.tnt() > 0) overlay(graphics, 16, 176, 0);

        // Salted indicator: dirty active alongside a nuclear stage with nothing higher present.
        if (yields.dirty() > 0 && yields.nuke() > 0 && yields.amat() == 0
                && yields.schrab() == 0 && yields.euph() == 0) {
            overlay(graphics, 88, 176, 72);
        }
    }

    private void overlay(GuiGraphics graphics, int dx, int u, int v) {
        graphics.blit(TEXTURE, leftPos + dx, topPos + 89, u, v, 18, 18, 256, 256);
    }

    private void drawStageTooltip(GuiGraphics graphics, int mouseX, int mouseY, int dx, List<Component> text) {
        int x = leftPos + dx;
        int y = topPos + 89;
        if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
            graphics.renderComponentTooltip(font, text, mouseX, mouseY);
        }
    }

    private static MutableComponent yellow(String text) {
        return Component.literal(text).withStyle(ChatFormatting.YELLOW);
    }

    private static MutableComponent italic(String text) {
        return Component.literal(text).withStyle(ChatFormatting.ITALIC);
    }

    private List<Component> tntTooltip() {
        List<Component> text = new ArrayList<>();
        text.add(yellow("Conventional Explosives (Level " + yields.tnt() + "/"
                + Math.min(yields.tnt(), CustomNukeExplosion.MAX_TNT) + ")"));
        text.add(Component.literal("Caps at " + CustomNukeExplosion.MAX_TNT));
        text.add(Component.literal("N²-like above level 75"));
        text.add(italic("\"Goes boom\""));
        return text;
    }

    private List<Component> nukeTooltip() {
        List<Component> text = new ArrayList<>();
        text.add(yellow("Nuclear (Level " + yields.nuke() + "/" + yields.nukeAdj() + ")"));
        text.add(Component.literal("Requires TNT level 16"));
        text.add(Component.literal("Caps at " + CustomNukeExplosion.MAX_NUKE));
        text.add(Component.literal("Has fallout"));
        text.add(italic("\"Now I am become death, destroyer of worlds.\""));
        return text;
    }

    private List<Component> hydroTooltip() {
        List<Component> text = new ArrayList<>();
        text.add(yellow("Thermonuclear (Level " + yields.hydro() + "/" + yields.hydroAdj() + ")"));
        text.add(Component.literal("Requires nuclear level 100"));
        text.add(Component.literal("Caps at " + CustomNukeExplosion.MAX_HYDRO));
        text.add(Component.literal("Reduces added fallout by salted stage by 75%"));
        text.add(italic("\"And for my next trick, I'll make"));
        text.add(italic("the island of Elugelab disappear!\""));
        return text;
    }

    private List<Component> amatTooltip() {
        List<Component> text = new ArrayList<>();
        text.add(yellow("Antimatter (Level " + yields.amat() + "/" + yields.amatAdj() + ")"));
        text.add(Component.literal("Requires nuclear level 50"));
        text.add(Component.literal("Caps at " + CustomNukeExplosion.MAX_AMAT));
        text.add(italic("\"Antimatter, Balefire, whatever.\""));
        return text;
    }

    private List<Component> saltedTooltip() {
        List<Component> text = new ArrayList<>();
        text.add(yellow("Salted (Level " + yields.dirty() + "/"
                + Math.min(yields.dirty(), CustomNukeExplosion.MAX_DIRTY) + ")"));
        text.add(Component.literal("Extends fallout of nuclear and"));
        text.add(Component.literal("thermonuclear stages"));
        text.add(Component.literal("Caps at 100"));
        text.add(italic("\"Not to be confused with tablesalt.\""));
        return text;
    }

    private List<Component> schrabTooltip() {
        List<Component> text = new ArrayList<>();
        text.add(yellow("Schrabidium (Level " + yields.schrab() + "/" + yields.schrabAdj() + ")"));
        text.add(Component.literal("Requires nuclear level 50"));
        text.add(Component.literal("Caps at " + CustomNukeExplosion.MAX_SCHRAB));
        text.add(italic("\"For the hundredth time,"));
        text.add(italic("you can't bypass these caps!\""));
        return text;
    }

    private List<Component> iceCreamTooltip() {
        List<Component> text = new ArrayList<>();
        text.add(yellow("Ice cream (Level unknown)"));
        text.add(italic("\"Probably not ice cream but the label came off.\""));
        return text;
    }
}
