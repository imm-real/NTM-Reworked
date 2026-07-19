package com.hbm.ntm.data;

import net.neoforged.neoforge.data.event.GatherDataEvent;

public final class ModDataGenerators {
    private ModDataGenerators() {
    }

    public static void gatherData(GatherDataEvent event) {
        event.addProvider(new MaterialResourcesProvider(event.getGenerator().getPackOutput()));
    }
}
