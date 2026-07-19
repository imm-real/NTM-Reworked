package com.hbm.ntm.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-side knobs for eyes and ears only. */
public final class HbmClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue GEIGER_OFFSET_HORIZONTAL;
    public static final ModConfigSpec.IntValue GEIGER_OFFSET_VERTICAL;
    public static final ModConfigSpec.IntValue INFO_OFFSET_HORIZONTAL;
    public static final ModConfigSpec.IntValue INFO_OFFSET_VERTICAL;
    public static final ModConfigSpec.IntValue INFO_POSITION;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("hud");
        GEIGER_OFFSET_HORIZONTAL = builder
                .comment("Horizontal pixel offset for the Geiger Counter HUD. Original default: 0.")
                .defineInRange("GEIGER_OFFSET_HORIZONTAL", 0, -10_000, 10_000);
        GEIGER_OFFSET_VERTICAL = builder
                .comment("Vertical pixel offset for the Geiger Counter HUD. Original default: 0.")
                .defineInRange("GEIGER_OFFSET_VERTICAL", 0, -10_000, 10_000);
        INFO_OFFSET_HORIZONTAL = builder
                .comment("Horizontal pixel offset for source-style information messages. Original default: 0.")
                .defineInRange("INFO_OFFSET_HORIZONTAL", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        INFO_OFFSET_VERTICAL = builder
                .comment("Vertical pixel offset for source-style information messages. Original default: 0.")
                .defineInRange("INFO_OFFSET_VERTICAL", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        INFO_POSITION = builder
                .comment("Information-message position: 0 top-left, 1 top-right, 2 crosshair-right, 3 crosshair-left. Original default: 0.")
                .defineInRange("INFO_POSITION", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        builder.pop();
        SPEC = builder.build();
    }

    private HbmClientConfig() {
    }
}
