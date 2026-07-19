package com.hbm.ntm.item;

import com.hbm.ntm.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Water dampens dropped cloth; deliberate use makes the improvised soaked rag. */
public final class ClothRagItem extends Item {
    public ClothRagItem() {
        super(new Properties());
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!entity.level().isClientSide && !stack.isEmpty()
                && (entity.isInWater() || entity.level().getFluidState(entity.blockPosition()).is(FluidTags.WATER))) {
            entity.setItem(new ItemStack(ModItems.RAG_DAMP.get(), stack.getCount()));
            return true;
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!level.isClientSide) {
            held.shrink(1);
            ItemStack soaked = new ItemStack(ModItems.RAG_PISS.get());
            if (!player.getInventory().add(soaked)) player.drop(soaked, false);
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hbm.rag.water"));
        tooltip.add(Component.translatable("tooltip.hbm.rag.use"));
    }
}
