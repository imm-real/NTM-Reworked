package com.hbm.ntm.item;

import com.hbm.ntm.block.ChargeBlock;
import com.hbm.ntm.block.ChargeType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class ChargeBlockItem extends BlockItem {
    private final ChargeType type;

    public ChargeBlockItem(ChargeBlock block, Properties properties, ChargeType type) {
        super(block, properties);
        this.type = type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.hbm.charge.timer").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("block.hbm.charge.arm").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("block.hbm.charge.defuser").withStyle(ChatFormatting.RED));
        if (type == ChargeType.MINER || type == ChargeType.SEMTEX) {
            tooltip.add(Component.translatable("block.hbm.charge.all_drops").withStyle(ChatFormatting.BLUE));
            tooltip.add(Component.translatable("block.hbm.charge.no_damage").withStyle(ChatFormatting.BLUE));
        } else if (type == ChargeType.C4) {
            tooltip.add(Component.translatable("block.hbm.charge.no_drops").withStyle(ChatFormatting.BLUE));
        }
        if (type == ChargeType.SEMTEX) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("enchantment.minecraft.fortune")
                    .append(" III").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}
