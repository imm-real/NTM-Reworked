package com.hbm.ntm.block;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.world.item.ItemStack;

public enum LargeNukeType {
    GADGET("gadget", "container.nukeGadget", 6, 1, 176, 166, 8, 84),
    BOY("boy", "container.nukeBoy", 5, 64, 176, 222, 8, 140),
    MIKE("mike", "container.nukeMike", 8, 1, 176, 217, 8, 135),
    TSAR("tsar", "container.nukeTsar", 6, 1, 256, 233, 48, 151);

    private final String id;
    private final String containerKey;
    private final int slots;
    private final int stackLimit;
    private final int guiWidth;
    private final int guiHeight;
    private final int inventoryX;
    private final int inventoryY;

    LargeNukeType(String id, String containerKey, int slots, int stackLimit, int guiWidth, int guiHeight,
                  int inventoryX, int inventoryY) {
        this.id = id;
        this.containerKey = containerKey;
        this.slots = slots;
        this.stackLimit = stackLimit;
        this.guiWidth = guiWidth;
        this.guiHeight = guiHeight;
        this.inventoryX = inventoryX;
        this.inventoryY = inventoryY;
    }

    public String id() { return id; }
    public String containerKey() { return containerKey; }
    public int slots() { return slots; }
    public int stackLimit() { return stackLimit; }
    public int guiWidth() { return guiWidth; }
    public int guiHeight() { return guiHeight; }
    public int inventoryX() { return inventoryX; }
    public int inventoryY() { return inventoryY; }

    public int slotX(int slot) {
        return switch (this) {
            case GADGET -> new int[]{26, 8, 44, 8, 44, 98}[slot];
            case BOY -> 26 + slot * 18;
            case MIKE -> new int[]{26, 26, 44, 44, 39, 98, 116, 134}[slot];
            case TSAR -> new int[]{48, 66, 84, 102, 55, 138}[slot];
        };
    }

    public int slotY(int slot) {
        return switch (this) {
            case GADGET -> new int[]{35, 17, 17, 53, 53, 35}[slot];
            case BOY -> 36;
            case MIKE -> new int[]{83, 101, 83, 101, 35, 91, 91, 91}[slot];
            case TSAR -> new int[]{101, 101, 101, 101, 51, 101}[slot];
        };
    }

    public boolean isReady(ItemStack[] items) {
        return switch (this) {
            case GADGET -> items[0].is(ModItems.GADGET_WIREING.get())
                    && lenses(items, ModItems.EARLY_EXPLOSIVE_LENSES.get())
                    && items[5].is(ModItems.GADGET_CORE.get());
            case BOY -> items[0].is(ModItems.BOY_SHIELDING.get())
                    && items[1].is(ModItems.BOY_TARGET.get())
                    && items[2].is(ModItems.BOY_BULLET.get())
                    && items[3].is(ModItems.BOY_PROPELLANT.get())
                    && items[4].is(ModItems.BOY_IGNITER.get());
            case MIKE, TSAR -> items[0].is(ModItems.EXPLOSIVE_LENSES.get())
                    && items[1].is(ModItems.EXPLOSIVE_LENSES.get())
                    && items[2].is(ModItems.EXPLOSIVE_LENSES.get())
                    && items[3].is(ModItems.EXPLOSIVE_LENSES.get())
                    && items[4].is(ModItems.MAN_CORE.get());
        };
    }

    public boolean isFilled(ItemStack[] items) {
        if (!isReady(items)) return false;
        return switch (this) {
            case MIKE -> items[5].is(ModItems.MIKE_CORE.get())
                    && items[6].is(ModItems.MIKE_DEUT.get())
                    && items[7].is(ModItems.MIKE_COOLING_UNIT.get());
            case TSAR -> items[5].is(ModItems.TSAR_CORE.get());
            default -> true;
        };
    }

    public int detonationRadius(ItemStack[] items) {
        return switch (this) {
            case GADGET -> HbmConfig.GADGET_RADIUS.get();
            case BOY -> HbmConfig.BOY_RADIUS.get();
            // Source quirk: NukeMike passes a fallback radius but igniteTestBomb ignores it.
            case MIKE -> HbmConfig.MIKE_RADIUS.get();
            case TSAR -> isFilled(items) ? HbmConfig.TSAR_RADIUS.get() : HbmConfig.FAT_MAN_RADIUS.get();
        };
    }

    private static boolean lenses(ItemStack[] items, net.minecraft.world.item.Item lens) {
        return items[1].is(lens) && items[2].is(lens) && items[3].is(lens) && items[4].is(lens);
    }
}
