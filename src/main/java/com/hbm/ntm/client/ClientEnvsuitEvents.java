package com.hbm.ntm.client;

import com.hbm.ntm.hazard.HazardProtection;
import com.hbm.ntm.item.EnvsuitArmorItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Hides the vanilla hat layer when the envsuit has other plans. */
final class ClientEnvsuitEvents {
    private static final List<HazardProtection> FULL_PACKAGE = List.of(
            HazardProtection.PARTICLE_COARSE,
            HazardProtection.PARTICLE_FINE,
            HazardProtection.GAS_LUNG,
            HazardProtection.BACTERIA,
            HazardProtection.GAS_BLISTERING,
            HazardProtection.GAS_MONOXIDE,
            HazardProtection.LIGHT,
            HazardProtection.SAND
    );
    private static final Map<Player, Boolean> PREVIOUS_HAT_VISIBILITY = new WeakHashMap<>();
    private static boolean registered;

    private ClientEnvsuitEvents() {
    }

    static void register() {
        if (registered) return;
        registered = true;
        // Run after ordinary Pre listeners and do not mutate the shared player model
        // when an earlier listener has already cancelled this render.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                ClientEnvsuitEvents::beforePlayerRender);
        NeoForge.EVENT_BUS.addListener(ClientEnvsuitEvents::afterPlayerRender);
        NeoForge.EVENT_BUS.addListener(ClientEnvsuitEvents::appendTooltip);
    }

    private static void beforePlayerRender(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                .is(ModItems.ENVSUIT_HELMET.get())) return;
        boolean previous = event.getRenderer().getModel().hat.visible;
        PREVIOUS_HAT_VISIBILITY.put(player, previous);
        event.getRenderer().getModel().hat.visible = false;
    }

    private static void afterPlayerRender(RenderPlayerEvent.Post event) {
        Boolean previous = PREVIOUS_HAT_VISIBILITY.remove(event.getEntity());
        if (previous != null) event.getRenderer().getModel().hat.visible = previous;
    }

    /** Three tooltip passes because one was apparently too efficient. */
    private static void appendTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof EnvsuitArmorItem envsuit)) return;

        List<Component> tooltip = event.getToolTip();
        tooltip.add(Component.translatable("damage.inset").withStyle(ChatFormatting.DARK_PURPLE));
        addSetPiece(tooltip, ModItems.ENVSUIT_HELMET.get().getDefaultInstance());
        addSetPiece(tooltip, ModItems.ENVSUIT_PLATE.get().getDefaultInstance());
        addSetPiece(tooltip, ModItems.ENVSUIT_LEGS.get().getDefaultInstance());
        addSetPiece(tooltip, ModItems.ENVSUIT_BOOTS.get().getDefaultInstance());
        tooltip.add(resistance("damage.category.FIRE", "2.0/75%"));
        tooltip.add(resistance("damage.exact.drown", "0.0/100%"));
        tooltip.add(resistance("damage.exact.fall", "5.0/75%"));
        tooltip.add(resistance("damage.other", "0.0/10%"));

        if (envsuit.getType() == ArmorItem.Type.HELMET) {
            if (Screen.hasShiftDown()) {
                tooltip.add(Component.translatable("hazard.prot").withStyle(ChatFormatting.GOLD));
                for (HazardProtection protection : FULL_PACKAGE) {
                    tooltip.add(Component.literal("  ")
                            .append(Component.translatable(protection.translationKey()))
                            .withStyle(ChatFormatting.YELLOW));
                }
            } else {
                tooltip.add(Component.literal("Hold <")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
                        .append(Component.literal("LSHIFT")
                                .withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC))
                        .append(Component.literal("> to display protection info")
                                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
            }
        }

        tooltip.add(Component.translatable("trait.radResistance",
                        Float.toString(envsuit.radiationResistance()))
                .withStyle(ChatFormatting.YELLOW));
    }

    private static void addSetPiece(List<Component> tooltip, ItemStack stack) {
        tooltip.add(Component.literal("  ").append(stack.getHoverName())
                .withStyle(ChatFormatting.DARK_PURPLE));
    }

    private static Component resistance(String key, String value) {
        return Component.translatable(key).append(": " + value);
    }
}
