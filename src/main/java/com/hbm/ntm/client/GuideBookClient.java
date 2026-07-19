package com.hbm.ntm.client;

import com.hbm.ntm.client.screen.GuideBookScreen;
import com.hbm.ntm.network.GuideBookOpenPayload;
import net.minecraft.client.Minecraft;

/** Client-only endpoint kept out of the common guide item and join handler. */
public final class GuideBookClient {
    private GuideBookClient() {
    }

    public static void register() {
        GuideBookOpenPayload.installClientHandler(GuideBookClient::open);
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new GuideBookScreen());
    }
}
