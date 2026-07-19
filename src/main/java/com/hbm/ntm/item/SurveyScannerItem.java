package com.hbm.ntm.item;

import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Source Survey Scanner with its sparse 11x11-column prospecting pattern. */
public final class SurveyScannerItem extends Item {
    public SurveyScannerItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) scan(level, serverPlayer, hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void scan(Level level, ServerPlayer player, InteractionHand hand) {
        int x = player.blockPosition().getX();
        int y = player.blockPosition().getY();
        int z = player.blockPosition().getZ();
        int floor = level.getMinBuildHeight();
        int ceiling = level.getMaxBuildHeight() - 1;

        boolean oil = false;
        boolean coltan = false;
        boolean bedrockOil = false;
        boolean depth = false;
        boolean schist = false;
        boolean bedrockOre = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int a = -5; a <= 5; a++) {
            for (int b = -5; b <= 5; b++) {
                int sampleX = x + a * 5;
                int sampleZ = z + b * 5;
                for (int sampleY = Math.min(y + 15, ceiling); sampleY > floor + 1; sampleY -= 2) {
                    cursor.set(sampleX, sampleY, sampleZ);
                    if (!level.hasChunkAt(cursor)) continue;
                    var block = level.getBlockState(cursor).getBlock();
                    if (block == ModBlocks.ORE_OIL.get()) oil = true;
                    else if (block == ModBlocks.ORE_COLTAN.get()) coltan = true;
                    else if (block == ModBlocks.legacy("ore_bedrock_oil").get()) bedrockOil = true;
                    else if (block == ModBlocks.legacy("stone_depth").get()
                            || block == ModBlocks.legacy("stone_depth_nether").get()) depth = true;
                    else if (block == ModBlocks.legacy("stone_gneiss").get()) schist = true;
                }

                cursor.set(x + a * 2, floor, z + b * 2);
                if (level.hasChunkAt(cursor)
                        && level.getBlockState(cursor).is(ModBlocks.legacy("ore_bedrock").get())) {
                    bedrockOre = true;
                }
            }
        }

        if (oil) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Found OIL!")
                .withStyle(ChatFormatting.BLACK));
        if (bedrockOil) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Found BEDROCK OIL!")
                .withStyle(ChatFormatting.BLACK));
        if (coltan) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Found COLTAN!")
                .withStyle(ChatFormatting.GOLD));
        if (depth) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Found DEPTH ROCK!")
                .withStyle(ChatFormatting.GRAY));
        if (schist) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Found SCHIST!")
                .withStyle(ChatFormatting.DARK_AQUA));
        if (bedrockOre) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Found BEDROCK ORE!")
                .withStyle(ChatFormatting.RED));
        player.swing(hand, true);
    }
}
