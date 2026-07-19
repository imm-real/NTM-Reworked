package com.hbm.ntm.item;

import com.hbm.ntm.radiation.ChunkRadiationData;
import com.hbm.ntm.radiation.RadiationClicker;
import com.hbm.ntm.radiation.RadiationReadout;
import com.hbm.ntm.radiation.RadiationSystem;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Original Handheld Geiger Counter behavior and readout. */
public final class GeigerCounterItem extends Item {
    public GeigerCounterItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof LivingEntity living) || level.getGameTime() % 5L != 0L) return;
        RadiationClicker.tickCarriedGeiger(level, entity, RadiationSystem.data(living).radBuf());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.TECH_BOOP.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            RadiationReadout.sendGeiger(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static int check(ServerLevel level, BlockPos position) {
        return (int) Math.ceil(ChunkRadiationData.get(level).get(position));
    }
}
