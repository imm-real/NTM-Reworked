package com.hbm.ntm.item;

import com.hbm.ntm.blockentity.RefineryBlockEntity;
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

/** Lists the five refinery tanks packed inside a harvested machine. */
public final class RefineryBlockItem extends BlockItem {
    public RefineryBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
                                          List<Component> tooltip, TooltipFlag flag) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains(RefineryBlockEntity.ITEM_SELECTED_FLUID)) return;
        FluidIdentifierItem.Selection input = FluidIdentifierItem.Selection.byId(
                data.getString(RefineryBlockEntity.ITEM_SELECTED_FLUID));
        addTank(tooltip, data.getInt(RefineryBlockEntity.ITEM_INPUT),
                RefineryBlockEntity.INPUT_CAPACITY, input.translationKey());
        addTank(tooltip, data.getInt(RefineryBlockEntity.ITEM_HEAVY),
                RefineryBlockEntity.OUTPUT_CAPACITY, "hbmfluid.heavyoil");
        addTank(tooltip, data.getInt(RefineryBlockEntity.ITEM_NAPHTHA),
                RefineryBlockEntity.OUTPUT_CAPACITY, "hbmfluid.naphtha");
        addTank(tooltip, data.getInt(RefineryBlockEntity.ITEM_LIGHT),
                RefineryBlockEntity.OUTPUT_CAPACITY, "hbmfluid.lightoil");
        addTank(tooltip, data.getInt(RefineryBlockEntity.ITEM_PETROLEUM),
                RefineryBlockEntity.OUTPUT_CAPACITY, "hbmfluid.petroleum");
    }

    private static void addTank(List<Component> tooltip, int amount, int capacity, String fluidKey) {
        tooltip.add(Component.literal(Math.clamp(amount, 0, capacity) + "/" + capacity + " mB ")
                .append(Component.translatable(fluidKey)).withStyle(ChatFormatting.YELLOW));
    }
}
