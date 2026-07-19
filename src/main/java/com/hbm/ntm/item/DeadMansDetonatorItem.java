package com.hbm.ntm.item;

import com.hbm.ntm.explosion.RemoteDetonation;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/** Source dead man's switch: dropping it or carrying a linked switch on death consumes it and triggers its target. */
public final class DeadMansDetonatorItem extends DetonatorItem {
    public DeadMansDetonatorItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!context.getLevel().isClientSide) {
            writeLink(context.getItemInHand(), context.getClickedPos());
            player.sendSystemMessage(Component.literal("Position set!"));
            context.getLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.TECH_BOOP.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level() instanceof ServerLevel server) {
            trigger(stack, server, null);
            // World#createExplosion's sole boolean in 1.7.10 enabled terrain
            // processing, not fire. TNT interaction supplies the former here.
            server.explode(entity, entity.getX(), entity.getY(), entity.getZ(), 0.0F, false,
                    Level.ExplosionInteraction.TNT);
            entity.discard();
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Shift right-click to set position,"));
        tooltip.add(Component.literal("drop to detonate!"));
        BlockPos position = readLink(stack);
        if (position == null) {
            tooltip.add(Component.literal("No position set!"));
        } else {
            tooltip.add(Component.literal("Set pos to " + position.getX() + ", "
                    + position.getY() + ", " + position.getZ()));
        }
        tooltip.add(Component.translatable("trait.drop").withStyle(ChatFormatting.RED));
    }

    public static boolean trigger(ItemStack stack, ServerLevel level, Player owner) {
        BlockPos position = readLink(stack);
        if (position == null) return false;
        RemoteDetonation.Attempt attempt = RemoteDetonation.trigger(level, position);
        if (attempt.compatible()) {
            if (owner == null) logAttempt(position, "dead man's switch");
            else logAttempt(owner, position, "dead man's switch from ");
        }
        return attempt.compatible() && attempt.result().wasSuccessful();
    }
}
