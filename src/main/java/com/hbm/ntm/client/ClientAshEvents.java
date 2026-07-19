package com.hbm.ntm.client;

import com.hbm.ntm.block.DigammaAshBlock;
import com.hbm.ntm.hazard.HazardProtection;
import com.hbm.ntm.hazard.HazardSystem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Keeps the ash counter alive while its two render hooks remain unemployed. */
public final class ClientAshEvents {
    private ClientAshEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientAshEvents::clientTick);
    }

    private static void clientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        // Source quirk: BlockAshes.ashes is static and survives world/player changes.
        // Its client tick simply returns while either side of the client world is absent.
        if (minecraft.level == null || player == null) return;

        int limit;
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.ASHGLASSES.get())) {
            limit = 64;
        } else if (HazardSystem.hasProtection(player, HazardProtection.SAND, 0)
                || HazardSystem.hasProtection(player, HazardProtection.LIGHT, 0)) {
            limit = 192;
        } else {
            // Source comparison is int ashes < 256 * 0.95, so the counter can reach 244.
            limit = 244;
        }
        DigammaAshBlock.clientTick(limit);
    }
}
