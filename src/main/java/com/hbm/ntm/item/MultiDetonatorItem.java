package com.hbm.ntm.item;

import com.hbm.ntm.explosion.RemoteDetonation;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Source unbounded multi-position detonator, including duplicate-position behavior. */
public final class MultiDetonatorItem extends Item {
    private static final String XS = "xValues";
    private static final String YS = "yValues";
    private static final String ZS = "zValues";

    public MultiDetonatorItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!context.getLevel().isClientSide) {
            addLocation(context.getItemInHand(), context.getClickedPos());
            player.sendSystemMessage(DetonatorItem.prefixed(context.getItemInHand(),
                    Component.literal("Position added!").withStyle(ChatFormatting.GREEN)));
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

        List<BlockPos> positions = locations(stack);
        if (positions.isEmpty()) {
            player.sendSystemMessage(DetonatorItem.prefixed(stack,
                    Component.literal("No position set!").withStyle(ChatFormatting.RED)));
            return InteractionResultHolder.success(stack);
        }

        if (player.isShiftKeyDown()) {
            clear(stack);
            server.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.TECH_BOOP.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
            player.sendSystemMessage(DetonatorItem.prefixed(stack,
                    Component.literal("Locations cleared!").withStyle(ChatFormatting.RED)));
            return InteractionResultHolder.success(stack);
        }

        int successes = 0;
        for (BlockPos position : positions) {
            RemoteDetonation.Attempt attempt = RemoteDetonation.trigger(server, position);
            if (attempt.compatible()) {
                if (attempt.result().wasSuccessful()) successes++;
                DetonatorItem.logAttempt(player, position, "");
            }
        }
        server.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.TECH_BLEEP.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        player.sendSystemMessage(DetonatorItem.prefixed(stack,
                Component.literal("Triggered " + successes + "/" + positions.size() + "!")
                        .withStyle(ChatFormatting.YELLOW)));
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Shift right-click block to add position,"));
        tooltip.add(Component.literal("right-click to detonate!"));
        tooltip.add(Component.literal("Shift right-click in the air to clear positions."));
        List<BlockPos> positions = locations(stack);
        if (positions.isEmpty()) {
            tooltip.add(Component.literal("No position set!").withStyle(ChatFormatting.RED));
        } else {
            for (BlockPos position : positions) {
                tooltip.add(Component.literal(position.getX() + " / " + position.getY() + " / "
                        + position.getZ()).withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    public static void addLocation(ItemStack stack, BlockPos position) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putIntArray(XS, append(tag.getIntArray(XS), position.getX()));
        tag.putIntArray(YS, append(tag.getIntArray(YS), position.getY()));
        tag.putIntArray(ZS, append(tag.getIntArray(ZS), position.getZ()));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static List<BlockPos> locations(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return List.of();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int[] xs = tag.getIntArray(XS);
        int[] ys = tag.getIntArray(YS);
        int[] zs = tag.getIntArray(ZS);
        int count = Math.min(xs.length, Math.min(ys.length, zs.length));
        if (count == 0) return List.of();
        List<BlockPos> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(new BlockPos(xs[i], ys[i], zs[i]));
        return List.copyOf(result);
    }

    private static void clear(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putIntArray(XS, new int[0]);
        tag.putIntArray(YS, new int[0]);
        tag.putIntArray(ZS, new int[0]);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static int[] append(int[] values, int value) {
        int[] result = Arrays.copyOf(values, values.length + 1);
        result[values.length] = value;
        return result;
    }
}
