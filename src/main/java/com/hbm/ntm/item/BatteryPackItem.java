package com.hbm.ntm.item;

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

/** Component-backed carrier for the metadata-era {@code hbm:battery_pack}. */
public final class BatteryPackItem extends Item implements HeBatteryItem {
    private static final String TYPE = "type";
    private static final String CHARGE = "charge";

    public BatteryPackItem() {
        super(new Properties().stacksTo(1));
    }

    public static ItemStack create(Item item, BatteryType type, boolean full) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putString(TYPE, type.id());
        tag.putLong(CHARGE, full ? type.capacity() : 0L);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static BatteryType type(ItemStack stack) {
        String id = data(stack).getString(TYPE);
        for (BatteryType type : BatteryType.values()) {
            if (type.id().equals(id)) {
                return type;
            }
        }
        return BatteryType.BATTERY_REDSTONE;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.hbm.battery_pack." + type(stack).id());
    }

    @Override
    public long getCharge(ItemStack stack) {
        return data(stack).getLong(CHARGE);
    }

    @Override
    public long getMaxCharge(ItemStack stack) {
        return type(stack).capacity();
    }

    @Override
    public long getChargeRate(ItemStack stack) {
        return type(stack).chargeRate();
    }

    @Override
    public long getDischargeRate(ItemStack stack) {
        return type(stack).dischargeRate();
    }

    @Override
    public void charge(ItemStack stack, long amount) {
        setCharge(stack, getCharge(stack) + amount);
    }

    @Override
    public void setCharge(ItemStack stack, long amount) {
        BatteryType type = type(stack);
        CompoundTag tag = data(stack);
        tag.putString(TYPE, type.id());
        tag.putLong(CHARGE, amount);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public void discharge(ItemStack stack, long amount) {
        setCharge(stack, getCharge(stack) - amount);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getCharge(stack) != getMaxCharge(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * getCharge(stack) / Math.max(1L, getMaxCharge(stack)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float fraction = (float) getCharge(stack) / Math.max(1L, getMaxCharge(stack));
        return Mth.hsvToRgb(Math.max(0.0F, fraction) / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        long maxCharge = getMaxCharge(stack);
        long charge = getCharge(stack);
        long chargeRate = getChargeRate(stack);
        long dischargeRate = getDischargeRate(stack);
        double percent = charge * 1000L / maxCharge / 10.0D;
        tooltip.add(Component.literal("Energy stored: " + shortNumber(charge) + "/" + shortNumber(maxCharge)
                + "HE (" + percent + "%)").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("Charge rate: " + shortNumber(chargeRate) + "HE/t")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("Discharge rate: " + shortNumber(dischargeRate) + "HE/t")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("Time for full charge: " + (maxCharge / chargeRate / 20 / 60.0D) + "min")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("Charge lasts for: " + (maxCharge / dischargeRate / 20 / 60.0D) + "min")
                .withStyle(ChatFormatting.GOLD));
    }

    public static String shortNumber(long value) {
        long absolute = Math.abs(value);
        double result;
        String suffix;
        if (absolute >= 1_000_000_000_000_000_000L) {
            result = value / 1.0E18D;
            suffix = "E";
        } else if (absolute >= 1_000_000_000_000_000L) {
            result = value / 1.0E15D;
            suffix = "P";
        } else if (absolute >= 1_000_000_000_000L) {
            result = value / 1.0E12D;
            suffix = "T";
        } else if (absolute >= 1_000_000_000L) {
            result = value / 1.0E9D;
            suffix = "G";
        } else if (absolute >= 1_000_000L) {
            result = value / 1.0E6D;
            suffix = "M";
        } else if (absolute >= 1_000L) {
            result = value / 1.0E3D;
            suffix = "k";
        } else {
            return Long.toString(value);
        }
        result = result <= -100.0D ? Math.round(result * 10.0D) / 10.0D
                : Math.round(result * 100.0D) / 100.0D;
        return String.format(Locale.ROOT, "%s%s", result, suffix);
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public enum BatteryType {
        BATTERY_REDSTONE("battery_redstone", 100L, false),
        BATTERY_LEAD("battery_lead", 1_000L, false),
        BATTERY_LITHIUM("battery_lithium", 10_000L, false),
        BATTERY_SODIUM("battery_sodium", 50_000L, false),
        BATTERY_SCHRABIDIUM("battery_schrabidium", 250_000L, false),
        BATTERY_QUANTUM("battery_quantum", 1_000_000L, 20L * 60L * 60L),
        CAPACITOR_COPPER("capacitor_copper", 1_000L, true),
        CAPACITOR_GOLD("capacitor_gold", 10_000L, true),
        CAPACITOR_NIOBIUM("capacitor_niobium", 100_000L, true),
        CAPACITOR_TANTALUM("capacitor_tantalum", 500_000L, true),
        CAPACITOR_BISMUTH("capacitor_bismuth", 2_500_000L, true),
        CAPACITOR_SPARK("capacitor_spark", 10_000_000L, true);

        private final String id;
        private final long capacity;
        private final long chargeRate;
        private final long dischargeRate;
        private final boolean capacitor;

        BatteryType(String id, long dischargeRate, boolean capacitor) {
            this(id,
                    dischargeRate * 20L * (capacitor ? 30L : 60L * 15L),
                    dischargeRate * (capacitor ? 1L : 10L),
                    dischargeRate,
                    capacitor);
        }

        BatteryType(String id, long dischargeRate, long duration) {
            this(id, dischargeRate * duration, dischargeRate * 10L, dischargeRate, false);
        }

        BatteryType(String id, long capacity, long chargeRate, long dischargeRate, boolean capacitor) {
            this.id = id;
            this.capacity = capacity;
            this.chargeRate = chargeRate;
            this.dischargeRate = dischargeRate;
            this.capacitor = capacitor;
        }

        public String id() {
            return id;
        }

        public long capacity() {
            return capacity;
        }

        public long chargeRate() {
            return chargeRate;
        }

        public long dischargeRate() {
            return dischargeRate;
        }

        public boolean capacitor() {
            return capacitor;
        }
    }
}
