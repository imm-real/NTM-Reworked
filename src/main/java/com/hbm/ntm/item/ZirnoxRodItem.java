package com.hbm.ntm.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/** Eleven ways to make a ZIRNOX operator nervous. */
public final class ZirnoxRodItem extends Item {
    private static final String LIFE = "zirnoxLife";
    private final Type type;

    public ZirnoxRodItem(Properties properties, Type type) {
        super(properties.stacksTo(1));
        this.type = type;
    }

    public Type type() { return type; }

    public int life(ItemStack stack) {
        return Mth.clamp(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getInt(LIFE), 0, type.maxLife());
    }

    public void setLife(ItemStack stack, int life) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(LIFE, Mth.clamp(life, 0, type.maxLife() + 1));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public int advance(ItemStack stack, int amount) {
        int next = life(stack) + Math.max(amount, 0);
        setLife(stack, next);
        return next;
    }

    @Override public boolean isBarVisible(ItemStack stack) { return life(stack) > 0; }
    @Override public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * (type.maxLife() - life(stack)) / type.maxLife());
    }
    @Override public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(Math.max(0.0F, (type.maxLife() - life(stack)) / (float) type.maxLife()) / 3.0F,
                1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int remaining = Math.max(type.maxLife() - life(stack), 0);
        tooltip.add(Component.literal(type.breeding() ? "Breeding rod" : type.heat() + " heat / reaction")
                .withStyle(type.breeding() ? net.minecraft.ChatFormatting.AQUA : net.minecraft.ChatFormatting.GOLD));
        tooltip.add(Component.literal(remaining + " / " + type.maxLife() + " life")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    public enum Type {
        NATURAL_URANIUM_FUEL(250_000, 30, false),
        URANIUM_FUEL(200_000, 50, false),
        TH232(20_000, 0, true),
        THORIUM_FUEL(200_000, 40, false),
        MOX_FUEL(165_000, 75, false),
        PLUTONIUM_FUEL(175_000, 65, false),
        U233_FUEL(150_000, 100, false),
        U235_FUEL(165_000, 85, false),
        LES_FUEL(150_000, 150, false),
        LITHIUM(20_000, 0, true),
        ZFB_MOX(50_000, 35, false);

        private final int maxLife;
        private final int heat;
        private final boolean breeding;

        Type(int maxLife, int heat, boolean breeding) {
            this.maxLife = maxLife;
            this.heat = heat;
            this.breeding = breeding;
        }

        public int maxLife() { return maxLife; }
        public int heat() { return heat; }
        public boolean breeding() { return breeding; }
    }
}
