package com.hbm.ntm.thermal;

import com.hbm.ntm.item.AshItem;
import com.hbm.ntm.registry.ModItems;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;

public final class FuelEvents {
    private FuelEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FuelEvents::fuelBurnTime);
    }

    private static void fuelBurnTime(FurnaceFuelBurnTimeEvent event) {
        if (event.getItemStack().is(ModItems.SOLID_FUEL.get())) {
            event.setBurnTime(3_200);
        } else if (event.getItemStack().is(ModItems.SOLID_FUEL_PRESTO.get())) {
            event.setBurnTime(8_000);
        } else if (event.getItemStack().is(ModItems.SOLID_FUEL_PRESTO_TRIPLET.get())) {
            event.setBurnTime(40_000);
        } else if (event.getItemStack().is(ModItems.SOLID_FUEL_BF.get())) {
            event.setBurnTime(32_000);
        } else if (event.getItemStack().is(ModItems.SOLID_FUEL_PRESTO_BF.get())) {
            event.setBurnTime(80_000);
        } else if (event.getItemStack().is(ModItems.SOLID_FUEL_PRESTO_TRIPLET_BF.get())) {
            event.setBurnTime(400_000);
        } else if (event.getItemStack().is(ModItems.ROCKET_FUEL.get())) {
            event.setBurnTime(6_400);
        } else if (event.getItemStack().is(ModItems.COKE_COAL.get())
                || event.getItemStack().is(ModItems.COKE_LIGNITE.get())
                || event.getItemStack().is(ModItems.COKE_PETROLEUM.get())) {
            event.setBurnTime(3_200);
        } else if (event.getItemStack().is(ModItems.BLOCK_COKE_COAL_ITEM.get())
                || event.getItemStack().is(ModItems.BLOCK_COKE_LIGNITE_ITEM.get())
                || event.getItemStack().is(ModItems.BLOCK_COKE_PETROLEUM_ITEM.get())) {
            event.setBurnTime(32_000);
        } else if (event.getItemStack().is(ModItems.legacyOreResourceItem("lignite").get())
                || event.getItemStack().is(ModItems.get("powder_lignite").get())) {
            event.setBurnTime(1_200);
        } else if (event.getItemStack().is(ModItems.get("powder_coal").get())) {
            event.setBurnTime(1_600);
        } else if (event.getItemStack().is(ModItems.legacyOreResourceItem("coal_infernal").get())) {
            event.setBurnTime(4_800);
        } else if (event.getItemStack().is(ModItems.get("scrap").get())) {
            event.setBurnTime(50);
        } else if (event.getItemStack().is(ModItems.get("dust").get())) {
            event.setBurnTime(25);
        } else if (event.getItemStack().is(ModItems.POWDER_ASH.get())) {
            event.setBurnTime(AshItem.type(event.getItemStack()).burnTime());
        }
    }
}
