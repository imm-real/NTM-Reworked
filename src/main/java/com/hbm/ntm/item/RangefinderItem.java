package com.hbm.ntm.item;

import com.hbm.ntm.network.DetonatorInfoPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Source 300-block rangefinder required by the Laser Detonator recipe. */
public final class RangefinderItem extends Item {
    public static final double RANGE = 300.0D;

    public RangefinderItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            Vec3 start = player.getEyePosition(1.0F);
            Vec3 end = start.add(player.getViewVector(1.0F).scale(RANGE));
            BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK) {
                double distance = ((int) (start.distanceTo(hit.getLocation()) * 10.0D)) / 10.0D;
                PacketDistributor.sendToPlayer(serverPlayer,
                        new DetonatorInfoPayload(Component.literal(distance + "m"), 5_000));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
