package com.hbm.ntm.item;

import com.hbm.ntm.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Source last-priority player-death scan for linked dead man's switches. */
public final class DetonatorEvents {
    private DetonatorEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, DetonatorEvents::onLivingDeath);
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && player.level() instanceof ServerLevel server) {
            triggerDeathSwitches(player, server);
        }
    }

    public static int triggerDeathSwitches(Player player, ServerLevel level) {
        int consumed = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(ModItems.DETONATOR_DEADMAN.get()) || DetonatorItem.readLink(stack) == null) continue;
            DeadMansDetonatorItem.trigger(stack, level, player);
            player.getInventory().setItem(slot, ItemStack.EMPTY);
            consumed++;
        }
        return consumed;
    }
}
