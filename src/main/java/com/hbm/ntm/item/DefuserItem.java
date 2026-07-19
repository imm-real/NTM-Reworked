package com.hbm.ntm.item;

import com.hbm.ntm.block.ChargeBlock;
import com.hbm.ntm.block.ConventionalExplosiveBlock;
import com.hbm.ntm.block.LandmineBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class DefuserItem extends Item {
    public DefuserItem() {
        super(new Properties().stacksTo(1).durability(100));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var state = context.getLevel().getBlockState(context.getClickedPos());
        if (!(state.getBlock() instanceof ChargeBlock)
                && !(state.getBlock() instanceof ConventionalExplosiveBlock)
                && !(state.getBlock() instanceof LandmineBlock)) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (context.getPlayer() == null) {
            return InteractionResult.PASS;
        }

        boolean handled;
        if (state.getBlock() instanceof ChargeBlock charge) {
            handled = charge.useDefuser((ServerLevel) context.getLevel(), context.getClickedPos(), context.getPlayer());
        } else if (state.getBlock() instanceof LandmineBlock mine) {
            // Source onBlockActivated: safe removal plus a gaussian toss of one mine item.
            mine.defuse((ServerLevel) context.getLevel(), context.getClickedPos());
            handled = true;
        } else {
            context.getLevel().removeBlock(context.getClickedPos(), false);
            Block.popResource(context.getLevel(), context.getClickedPos(), new net.minecraft.world.item.ItemStack(state.getBlock()));
            handled = true;
        }
        if (!handled) {
            return InteractionResult.PASS;
        }
        context.getItemInHand().hurtAndBreak(1, context.getPlayer(),
                net.minecraft.world.entity.LivingEntity.getSlotForHand(context.getHand()));
        return InteractionResult.CONSUME;
    }
}
