package com.hbm.ntm.item;

import com.hbm.ntm.blockentity.FensuBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;

public final class FensuBlockItem extends BlockItem {
    public FensuBlockItem(Block block, Properties properties) { super(block, properties); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains(FensuBlockEntity.ITEM_POWER)) return;
        byte[] bytes = data.getByteArray(FensuBlockEntity.ITEM_POWER);
        BigInteger power = bytes.length == 0 ? BigInteger.ZERO : new BigInteger(bytes).max(BigInteger.ZERO);
        tooltip.add(Component.literal(String.format(Locale.US, "%,d", power) + " HE")
                .withStyle(ChatFormatting.YELLOW));
    }
}
