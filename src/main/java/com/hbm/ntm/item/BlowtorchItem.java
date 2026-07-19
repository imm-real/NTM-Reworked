package com.hbm.ntm.item;

import com.hbm.ntm.block.FluidStorageTankBlock;
import com.hbm.ntm.blockentity.FluidStorageTankBlockEntity;
import com.hbm.ntm.registry.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/** Gas blowtorch, issued full and trusted with tank repair for some reason. */
public final class BlowtorchItem extends Item {
    public static final int CAPACITY = 4_000;
    public static final int REPAIR_COST = 250;
    public static final int STEEL_PLATES_PER_TANK = 6;
    private static final String GAS = "gas";
    private static final TagKey<Item> STEEL_PLATES = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "plates/steel"));

    public BlowtorchItem() {
        super(new Properties().stacksTo(1));
    }

    /** No saved gas value means factory fresh and completely full. */
    public static int gas(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return data.contains(GAS) ? Math.clamp(data.getInt(GAS), 0, CAPACITY) : CAPACITY;
    }

    public static void setGas(ItemStack stack, int amount) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putInt(GAS, Math.clamp(amount, 0, CAPACITY));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (!(state.getBlock() instanceof FluidStorageTankBlock)) return InteractionResult.PASS;
        BlockPos core = FluidStorageTankBlock.corePosition(context.getClickedPos(), state);
        if (!(context.getLevel().getBlockEntity(core) instanceof FluidStorageTankBlockEntity tank)
                || !tank.damaged()) return InteractionResult.PASS;

        Player player = context.getPlayer();
        ItemStack torch = context.getItemInHand();
        if (player == null || gas(torch) < REPAIR_COST || countSteelPlates(player) < STEEL_PLATES_PER_TANK) {
            return InteractionResult.FAIL;
        }
        if (!context.getLevel().isClientSide && tank.repair()) {
            consumeSteelPlates(player, STEEL_PLATES_PER_TANK);
            setGas(torch, gas(torch) - REPAIR_COST);
            ServerLevel level = (ServerLevel) context.getLevel();
            double x = context.getClickLocation().x;
            double y = context.getClickLocation().y;
            double z = context.getClickLocation().z;
            level.sendParticles(ModParticles.TAU_SPARK.get(), x, y, z,
                    10, .12D, .12D, .12D, .03D);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    public static int countSteelPlates(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) if (stack.is(STEEL_PLATES)) count += stack.getCount();
        return count;
    }

    private static void consumeSteelPlates(Player player, int amount) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.is(STEEL_PLATES)) continue;
            int consumed = Math.min(amount, stack.getCount());
            stack.shrink(consumed);
            amount -= consumed;
            if (amount == 0) return;
        }
    }

    @Override public boolean isBarVisible(ItemStack stack) { return gas(stack) < CAPACITY; }
    @Override public int getBarWidth(ItemStack stack) { return Math.round(13F * gas(stack) / CAPACITY); }
    @Override public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(Math.max(0F, (float) gas(stack) / CAPACITY) / 3F, 1F, 1F);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
                                          List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(Component.translatable("hbmfluid.gas").getString() + ": "
                + String.format(Locale.US, "%,d / %,d", gas(stack), CAPACITY)).withStyle(ChatFormatting.YELLOW));
    }
}
