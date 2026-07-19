package com.hbm.ntm.item;

import com.hbm.ntm.block.OilDerrickBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** Packs the derrick's HE and fluids before the machine moves house. */
public final class OilDerrickBlockItem extends BlockItem {
    public OilDerrickBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains(OilDerrickBlock.POWER)) return;
        tooltip.add(Component.literal(data.getLong(OilDerrickBlock.POWER) + " HE").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(data.getInt(OilDerrickBlock.OIL) + "/64000 mB ")
                .append(Component.translatable("hbmfluid.oil")).withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal(data.getInt(OilDerrickBlock.GAS) + "/64000 mB ")
                .append(Component.translatable("hbmfluid.gas")).withStyle(ChatFormatting.YELLOW));
    }
}
