package com.hbm.ntm.hazard;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.Map;

/** Hazards attached directly to vanilla items reckless enough to participate. */
public final class HazardRegistry {
    private static final Map<Item, HazardProfile> ITEM_HAZARDS = new IdentityHashMap<>();
    private static boolean initialized;

    private HazardRegistry() {
    }

    public static void bootstrap() {
        if (initialized) {
            return;
        }
        initialized = true;

        register(Items.GUNPOWDER, HazardProfile.NONE.withExplosive(1.0F));
        register(Items.TNT, HazardProfile.NONE.withExplosive(4.0F));
        register(Items.PUMPKIN_PIE, HazardProfile.NONE.withExplosive(1.0F));
    }

    public static void register(Item item, HazardProfile hazards) {
        ITEM_HAZARDS.merge(item, hazards, HazardProfile::add);
    }

    public static HazardProfile get(ItemStack stack) {
        return ITEM_HAZARDS.getOrDefault(stack.getItem(), HazardProfile.NONE);
    }
}
