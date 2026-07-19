package com.hbm.ntm.item;

import com.hbm.ntm.blockentity.FluidStorageTankBlockEntity;
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

public final class FluidStorageTankBlockItem extends BlockItem {
    public FluidStorageTankBlockItem(Block block, Properties properties) { super(block, properties); }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
                                          List<Component> tooltip, TooltipFlag flag) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        FluidIdentifierItem.Selection selection = FluidIdentifierItem.Selection.byId(
                data.getString(FluidStorageTankBlockEntity.ITEM_FLUID));
        int amount = Math.clamp(data.getInt(FluidStorageTankBlockEntity.ITEM_AMOUNT),
                0, FluidStorageTankBlockEntity.CAPACITY);
        tooltip.add(Component.literal(amount + "/" + FluidStorageTankBlockEntity.CAPACITY + " mB ")
                .append(Component.translatable(selection.translationKey())).withStyle(ChatFormatting.YELLOW));
        if (data.getBoolean(FluidStorageTankBlockEntity.ITEM_DAMAGED)) {
            tooltip.add(Component.translatable("item.hbm.machine_fluidtank.damaged").withStyle(ChatFormatting.RED));
        }
    }
}
