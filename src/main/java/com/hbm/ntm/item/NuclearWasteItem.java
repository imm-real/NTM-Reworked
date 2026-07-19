package com.hbm.ntm.item;

import com.hbm.ntm.hazard.HazardCarrier;
import com.hbm.ntm.hazard.HazardProfile;
import com.hbm.ntm.hazard.HazardTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;

import java.util.List;

/** Long- and short-lived waste, distinguished by custom model data and bad decisions. */
public final class NuclearWasteItem extends Item implements HazardCarrier {
    private static final String[] SHORT_CLASSES = {
            "Uranium-235", "Uranium-233", "Neptunium-237", "Plutonium-239",
            "Plutonium-240", "Plutonium-241", "Americium-242", "Schrabidium-326"
    };
    private static final String[] LONG_CLASSES = {
            "Uranium-235", "Uranium-233", "Neptunium-237", "Thorium-232", "Schrabidium-326"
    };

    private final Family family;
    private final boolean tiny;
    private final boolean depleted;

    public NuclearWasteItem(Properties properties, Family family, boolean tiny, boolean depleted) {
        super(properties);
        this.family = family;
        this.tiny = tiny;
        this.depleted = depleted;
    }

    public Family family() {
        return family;
    }

    public boolean tiny() {
        return tiny;
    }

    public boolean depleted() {
        return depleted;
    }

    public int variantCount() {
        return family == Family.SHORT ? SHORT_CLASSES.length : LONG_CLASSES.length;
    }

    @Override
    public HazardProfile hbm$getHazards(ItemStack stack) {
        float radiation;
        float heat = 0.0F;
        if (family == Family.SHORT) {
            radiation = depleted ? 3.0F : 30.0F;
            if (!depleted) heat = 5.0F;
        } else {
            radiation = depleted ? 0.5F : 5.0F;
        }
        if (tiny) radiation *= 0.1F;
        HazardProfile profile = HazardProfile.radiation(radiation);
        return heat > 0.0F ? profile.withHeat(heat) : profile;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(className(stack)).withStyle(ChatFormatting.ITALIC));
        HazardTooltip.append(tooltip, hbm$getHazards(stack), stack);
    }

    /** Long-lived waste refuses damage, despawning and accountability. */
    @Override
    public int getEntityLifespan(ItemStack stack, Level level) {
        return family == Family.LONG ? Integer.MAX_VALUE : super.getEntityLifespan(stack, level);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (family == Family.LONG && !entity.isInvulnerable()) entity.setInvulnerable(true);
        return false;
    }

    public String className(ItemStack stack) {
        String[] classes = family == Family.SHORT ? SHORT_CLASSES : LONG_CLASSES;
        return classes[variant(stack, classes.length)];
    }

    public static ItemStack stack(Item item, int metadata, int count) {
        if (!(item instanceof NuclearWasteItem waste)) return new ItemStack(item, count);
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(rectify(metadata, waste.variantCount())));
        return stack;
    }

    public static int variant(ItemStack stack) {
        if (!(stack.getItem() instanceof NuclearWasteItem waste)) return 0;
        return variant(stack, waste.variantCount());
    }

    private static int variant(ItemStack stack, int count) {
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return rectify(data == null ? 0 : data.value(), count);
    }

    /** Keeps even nonsense metadata inside the family. */
    private static int rectify(int metadata, int count) {
        int positive = metadata == Integer.MIN_VALUE ? 0 : Math.abs(metadata);
        return positive % count;
    }

    public enum Family { SHORT, LONG }
}
