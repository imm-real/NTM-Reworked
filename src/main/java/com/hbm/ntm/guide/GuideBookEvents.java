package com.hbm.ntm.guide;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.registry.ModAttachments;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Gives each player one starter guide and then stops being helpful. */
public final class GuideBookEvents {
    private GuideBookEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(GuideBookEvents::playerLoggedIn);
    }

    private static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (HbmConfig.ENABLE_GUIDE_BOOK.get() && event.getEntity() instanceof ServerPlayer player) {
            giveIfNeeded(player);
        }
    }

    public static boolean giveIfNeeded(Player player) {
        if (player.level().isClientSide || player.getData(ModAttachments.RECEIVED_GUIDE_BOOK)) {
            return false;
        }

        ItemStack guide = new ItemStack(ModItems.BOOK_GUIDE.get());
        if (!player.getInventory().add(guide)) {
            player.drop(guide, false);
        }
        player.setData(ModAttachments.RECEIVED_GUIDE_BOOK, true);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.broadcastChanges();
        }
        return true;
    }
}
