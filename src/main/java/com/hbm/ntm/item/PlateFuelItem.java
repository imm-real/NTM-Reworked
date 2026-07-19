package com.hbm.ntm.item;

import com.hbm.ntm.hazard.HazardCarrier;
import com.hbm.ntm.hazard.HazardProfile;
import com.hbm.ntm.hazard.HazardTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Locale;

/** Seven research-reactor plates with increasingly argumentative flux curves. */
public final class PlateFuelItem extends Item implements HazardCarrier {
    private static final String LIFE = "life";
    private final Type type;

    public PlateFuelItem(Properties properties, Type type) {
        super(properties.stacksTo(1));
        this.type = type;
    }

    public Type type() {
        return type;
    }

    public int life(ItemStack stack) {
        return Math.max(0, stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getInt(LIFE));
    }

    public void setLife(ItemStack stack, int life) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(LIFE, Math.max(life, 0));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** Mirrors ItemPlateFuel.react, including passive plates consuming life without incoming flux. */
    public int react(ItemStack stack, int flux) {
        flux = Math.max(flux, 0);
        if (type.function != Function.PASSIVE) setLife(stack, life(stack) + flux);
        return switch (type.function) {
            case LOGARITHM -> (int) (Math.log10(flux + 1.0D) * 0.5D * type.reactivity);
            case SQUARE_ROOT -> (int) (Math.sqrt(flux) * type.reactivity / 10.0D);
            case NEGATIVE_QUADRATIC ->
                    (int) Math.max((flux - flux * (double) flux / 10_000.0D) / 100.0D * type.reactivity, 0.0D);
            case LINEAR -> (int) (flux / 100.0D * type.reactivity);
            case PASSIVE -> {
                setLife(stack, life(stack) + type.reactivity);
                yield type.reactivity;
            }
        };
    }

    public boolean depleted(ItemStack stack) {
        return life(stack) > type.maxLife;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return life(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        float remaining = 1.0F - Mth.clamp(life(stack) / (float) type.maxLife, 0.0F, 1.0F);
        return Math.round(13.0F * remaining);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float remaining = 1.0F - Mth.clamp(life(stack) / (float) type.maxLife, 0.0F, 1.0F);
        return Mth.hsvToRgb(remaining / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public HazardProfile hbm$getHazards(ItemStack stack) {
        double depletion = Math.pow(life(stack) / (double) type.maxLife, 0.4D);
        float radiation = (float) (type.freshRadiation
                + (type.depletedRadiation - type.freshRadiation) * depletion);
        HazardProfile profile = HazardProfile.radiation(radiation);
        return type.blinding ? profile.withBlinding(20.0F) : profile;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("[Research Reactor Plate Fuel]").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("   " + type.description()).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.literal("   Yield of " + shortNumber(type.maxLife) + " events")
                .withStyle(ChatFormatting.DARK_AQUA));
        HazardTooltip.append(tooltip, hbm$getHazards(stack), stack);
    }

    private static String shortNumber(long value) {
        if (value < 1_000L) return Long.toString(value);
        if (value < 1_000_000L) return trim(value / 1_000.0D) + "k";
        if (value < 1_000_000_000L) return trim(value / 1_000_000.0D) + "M";
        return trim(value / 1_000_000_000.0D) + "G";
    }

    private static String trim(double value) {
        String text = String.format(Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    public enum Function { LOGARITHM, SQUARE_ROOT, NEGATIVE_QUADRATIC, LINEAR, PASSIVE }

    public enum Type {
        U233(2_200_000, Function.SQUARE_ROOT, 50, 5.0F, 195.0F, false, "waste_plate_u233"),
        U235(2_200_000, Function.SQUARE_ROOT, 40, 1.0F, 150.0F, false, "waste_plate_u235"),
        MOX(2_400_000, Function.LOGARITHM, 50, 2.5F, 240.0F, false, "waste_plate_mox"),
        PU239(2_000_000, Function.NEGATIVE_QUADRATIC, 50, 5.0F, 202.5F, false, "waste_plate_pu239"),
        SA326(2_000_000, Function.LINEAR, 80, 15.0F, 150.0F, true, "waste_plate_sa326"),
        RA226BE(1_300_000, Function.PASSIVE, 30, 11.25F, 67.5F, false, "waste_plate_ra226be"),
        PU238BE(1_000_000, Function.PASSIVE, 50, 15.0F, 3.0F, false, "waste_plate_pu238be");

        private final int maxLife;
        private final Function function;
        private final int reactivity;
        private final float freshRadiation;
        private final float depletedRadiation;
        private final boolean blinding;
        private final String depletedId;

        Type(int maxLife, Function function, int reactivity, float freshRadiation,
             float depletedRadiation, boolean blinding, String depletedId) {
            this.maxLife = maxLife;
            this.function = function;
            this.reactivity = reactivity;
            this.freshRadiation = freshRadiation;
            this.depletedRadiation = depletedRadiation;
            this.blinding = blinding;
            this.depletedId = depletedId;
        }

        public int maxLife() { return maxLife; }
        public Function function() { return function; }
        public int reactivity() { return reactivity; }
        public String depletedId() { return depletedId; }

        public String description() {
            return switch (function) {
                case LOGARITHM -> "f(x) = log10(x + 1) * 0.5 * " + reactivity;
                case SQUARE_ROOT -> "f(x) = sqrt(x) * " + reactivity + " / 10";
                case NEGATIVE_QUADRATIC -> "f(x) = [x - (x² / 10000)] / 100 * " + reactivity;
                case LINEAR -> "f(x) = x / 100 * " + reactivity;
                case PASSIVE -> "f(x) = " + reactivity;
            };
        }
    }
}
