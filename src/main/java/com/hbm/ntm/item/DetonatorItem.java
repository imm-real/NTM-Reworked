package com.hbm.ntm.item;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.explosion.DetonationResult;
import com.hbm.ntm.explosion.RemoteDetonation;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/** Source single-position detonator. Coordinates intentionally remain dimension-relative. */
public class DetonatorItem extends Item {
    private static final String X = "x";
    private static final String Y = "y";
    private static final String Z = "z";

    public DetonatorItem() {
        this(new Properties().stacksTo(1));
    }

    protected DetonatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;

        if (!context.getLevel().isClientSide) {
            writeLink(context.getItemInHand(), context.getClickedPos());
            player.sendSystemMessage(prefixed(context.getItemInHand(),
                    Component.literal("Position set!").withStyle(ChatFormatting.GREEN)));
            context.getLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.TECH_BOOP.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        BlockPos position = readLink(stack);
        if (position == null) {
            player.sendSystemMessage(prefixed(stack,
                    Component.literal("No position set!").withStyle(ChatFormatting.RED)));
            return InteractionResultHolder.success(stack);
        }

        RemoteDetonation.Attempt attempt = RemoteDetonation.trigger(server, position);
        if (attempt.compatible()) {
            server.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.TECH_BLEEP.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            logAttempt(player, position, "");
        }
        player.sendSystemMessage(prefixed(stack, resultMessage(attempt.result())));
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Shift right-click to set position,"));
        tooltip.add(Component.literal("right-click to detonate!"));
        BlockPos position = readLink(stack);
        if (position == null) {
            tooltip.add(Component.literal("No position set!").withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.literal("Linked to " + position.getX() + ", "
                    + position.getY() + ", " + position.getZ()).withStyle(ChatFormatting.YELLOW));
        }
    }

    public static void writeLink(ItemStack stack, BlockPos position) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(X, position.getX());
        tag.putInt(Y, position.getY());
        tag.putInt(Z, position.getZ());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static BlockPos readLink(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return null;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return new BlockPos(tag.getInt(X), tag.getInt(Y), tag.getInt(Z));
    }

    protected static MutableComponent prefixed(ItemStack stack, Component message) {
        return Component.literal("[").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.translatable(stack.getDescriptionId()).withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_AQUA))
                .append(message);
    }

    protected static Component resultMessage(DetonationResult result) {
        return Component.translatable(result.translationKey())
                .withStyle(result.wasSuccessful() ? ChatFormatting.YELLOW : ChatFormatting.RED);
    }

    protected static void logAttempt(Player player, BlockPos position, String source) {
        if (!HbmConfig.ENABLE_EXTENDED_LOGGING.get()) return;
        HbmNtm.LOGGER.info("[DET] Tried to detonate block at {} / {} / {} by {}{}!",
                position.getX(), position.getY(), position.getZ(), source, player.getScoreboardName());
    }

    protected static void logAttempt(BlockPos position, String source) {
        if (!HbmConfig.ENABLE_EXTENDED_LOGGING.get()) return;
        HbmNtm.LOGGER.info("[DET] Tried to detonate block at {} / {} / {} by {}!",
                position.getX(), position.getY(), position.getZ(), source);
    }
}
