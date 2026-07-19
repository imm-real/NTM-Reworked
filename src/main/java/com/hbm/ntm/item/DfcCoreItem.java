package com.hbm.ntm.item;

import com.hbm.ntm.HbmNtm;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class DfcCoreItem extends Item {
    public enum Kind { SINGULARITY, WORMHOLE, EYE_OF_HARMONY, THINGY }

    private final Kind kind;
    private final long powerBase;
    private final int heatBase;
    private final int fuelBase;
    private final int dfcMultiplier;

    public DfcCoreItem(Kind kind, long powerBase, int heatBase, int fuelBase, int dfcMultiplier) {
        super(new Properties().stacksTo(1).rarity(kind == Kind.THINGY ? Rarity.EPIC : Rarity.UNCOMMON));
        this.kind = kind;
        this.powerBase = powerBase;
        this.heatBase = heatBase;
        this.fuelBase = fuelBase;
        this.dfcMultiplier = dfcMultiplier;
    }

    public Kind kind() { return kind; }
    public long powerBase() { return powerBase; }
    public int heatBase() { return heatBase; }
    public int fuelBase() { return fuelBase; }
    public int dfcMultiplier() { return dfcMultiplier; }

    @Override public boolean isFoil(ItemStack stack) {
        return kind == Kind.THINGY && HbmNtm.POLAROID_ID == 11 || super.isFoil(stack);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
                                          List<Component> tooltip, TooltipFlag flag) {
        switch (kind) {
            case SINGULARITY -> add(tooltip,
                    "A modified undefined state of spacetime",
                    "used to aid in inter-gluon fusion and",
                    "spacetime annihilation. Yes, this destroys",
                    "the universe itself, slowly but steadily,",
                    "but at least you can power your toaster with",
                    "this, so it's all good.");
            case WORMHOLE -> add(tooltip,
                    "A cloud of billions of nano-wormholes which",
                    "deliberately fail at tunneling matter from",
                    "another dimension, rather it converts all",
                    "that matter into pure energy. That means",
                    "you're actively contributing to the destruction",
                    "of another dimension, sucking it dry like a",
                    "juicebox.", "That dimension probably sucked, anyways. I",
                    "bet it was full of wasps or some crap, man,",
                    "I hate these things.");
            case EYE_OF_HARMONY -> add(tooltip,
                    "A star collapsing in on itself, mere nanoseconds",
                    "away from being turned into a black hole,",
                    "frozen in time. If I didn't know better I",
                    "would say this is some deep space magic",
                    "bullcrap some guy made up to sound intellectual.",
                    "Probably Steve from accounting. You still owe me",
                    "ten bucks.");
            case THINGY -> {
                if (HbmNtm.POLAROID_ID == 11) add(tooltip, "Yeah I'm not even gonna question that one.");
                else add(tooltip, "...", "...", "...am I even holding this right?",
                        "It's a small metal thing. I dunno where it's from",
                        "or what it does, maybe they found it on a",
                        "junkyard and sold it as some kind of antique",
                        "artifact. If it weren't for the fact that I can",
                        "actually stuff this into some great big laser",
                        "reactor thing, I'd probably bring it back to where",
                        "it belongs. In the trash.");
            }
        }
    }

    private static void add(List<Component> tooltip, String... lines) {
        for (String line : lines) tooltip.add(Component.literal(line));
    }
}
