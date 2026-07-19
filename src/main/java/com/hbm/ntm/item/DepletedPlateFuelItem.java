package com.hbm.ntm.item;

import com.hbm.ntm.hazard.HazardCarrier;
import com.hbm.ntm.hazard.HazardProfile;
import com.hbm.ntm.hazard.HazardTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/** One depleted plate, either cooled or still a terrible potholder. */
public final class DepletedPlateFuelItem extends Item implements HazardCarrier, WasteDrumCoolable {
    private static final String HOT = "hot";
    private final Type type;

    public DepletedPlateFuelItem(Properties properties, Type type) {
        super(properties.stacksTo(1));
        this.type = type;
    }

    public Type type() { return type; }

    public static ItemStack hot(Item item) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(HOT, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static boolean isHot(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getBoolean(HOT);
    }

    public static void cool(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(HOT);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public boolean isWasteDrumInput(ItemStack stack) {
        return isHot(stack);
    }

    @Override
    public ItemStack wasteDrumResult(ItemStack stack) {
        ItemStack result = stack.copy();
        cool(result);
        return result;
    }

    @Override
    public boolean canExtractFromWasteDrum(ItemStack stack) {
        return !isHot(stack);
    }

    @Override
    public HazardProfile hbm$getHazards(ItemStack stack) {
        HazardProfile profile = HazardProfile.radiation(isHot(stack) ? type.hotRadiation : type.cooledRadiation);
        return isHot(stack) ? profile.withHeat(5.0F) : profile;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        if (isHot(stack)) {
            tooltip.add(Component.translatable("desc.item.wasteCooling").withStyle(ChatFormatting.GOLD));
        }
        HazardTooltip.append(tooltip, hbm$getHazards(stack), stack);
    }

    public enum Type {
        U233(195.0F, false), U235(150.0F, false), MOX(240.0F, false),
        PU239(202.5F, false), SA326(150.0F, false),
        RA226BE(67.5F, true), PU238BE(3.0F, true);

        private final float hotRadiation;
        private final float cooledRadiation;

        Type(float hotRadiation, boolean radSource) {
            this.hotRadiation = hotRadiation;
            this.cooledRadiation = radSource ? hotRadiation : hotRadiation * 0.075F;
        }

        public float hotRadiation() { return hotRadiation; }
        public float cooledRadiation() { return cooledRadiation; }
    }
}
