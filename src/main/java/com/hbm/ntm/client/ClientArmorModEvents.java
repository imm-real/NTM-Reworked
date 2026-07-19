package com.hbm.ntm.client;

import com.hbm.ntm.armor.ArmorModHandler;
import com.hbm.ntm.client.screen.ArmorTableScreen;
import com.hbm.ntm.item.ArmorCladdingItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Tells the client which questionable upgrades are sewn into the armor. */
public final class ClientArmorModEvents {
    private ClientArmorModEvents() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientArmorModEvents::onItemTooltip);
    }

    private static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack armor = event.getItemStack();
        if (!(armor.getItem() instanceof ArmorItem) || !ArmorModHandler.hasMods(armor)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (!Screen.hasShiftDown() && !(minecraft.screen instanceof ArmorTableScreen)) {
            event.getToolTip().add(Component.literal("Hold <").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
                    .append(Component.literal("LSHIFT").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC))
                    .append(Component.literal("> to display installed armor mods")
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
            return;
        }

        HolderLookup.Provider registries = event.getEntity() != null
                ? event.getEntity().registryAccess()
                : minecraft.level != null ? minecraft.level.registryAccess() : null;
        if (registries == null) return;
        ItemStack cladding = ArmorModHandler.pryMod(armor, ArmorModHandler.CLADDING, registries);
        if (!(cladding.getItem() instanceof ArmorCladdingItem item)) return;

        event.getToolTip().add(Component.literal("Mods:").withStyle(ChatFormatting.YELLOW));
        Component line = Component.literal("  ").append(cladding.getHoverName());
        if (item.effect() == ArmorCladdingItem.Effect.RADIATION) {
            line = line.copy().append(Component.literal(" (+" + item.radiationResistance()
                    + " radiation resistance)"));
            event.getToolTip().add(line.copy().withStyle(ChatFormatting.YELLOW));
        } else if (item.effect() == ArmorCladdingItem.Effect.IRON) {
            event.getToolTip().add(line.copy().append(Component.literal(" (+0.5 knockback resistence)"))
                    .withStyle(ChatFormatting.WHITE));
        } else {
            event.getToolTip().add(line.copy().append(Component.literal(" (Item indestructible)"))
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
    }
}
