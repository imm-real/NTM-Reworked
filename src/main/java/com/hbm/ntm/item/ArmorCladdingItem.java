package com.hbm.ntm.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class ArmorCladdingItem extends Item {
    public enum Effect { RADIATION, IRON, OBSIDIAN }

    private final Effect effect;
    private final float radiationResistance;

    public ArmorCladdingItem(Effect effect, float radiationResistance) {
        super(new Properties().stacksTo(1));
        this.effect = effect;
        this.radiationResistance = radiationResistance;
    }

    public Effect effect() { return effect; }
    public float radiationResistance() { return radiationResistance; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        switch (effect) {
            case RADIATION -> tooltip.add(Component.literal("+" + radiationResistance + " rad-resistance")
                    .withStyle(ChatFormatting.YELLOW));
            case IRON -> tooltip.add(Component.literal("+0.5 knockback resistance").withStyle(ChatFormatting.WHITE));
            case OBSIDIAN -> tooltip.add(Component.literal("Makes dropped armor indestructible")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("armorMod.applicableTo").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.literal("  ").append(Component.translatable("armorMod.all")));
        tooltip.add(Component.literal("Slot:").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.literal("  ").append(Component.translatable("armorMod.type.cladding")));
    }
}
