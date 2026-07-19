package com.hbm.ntm.item;

import com.hbm.ntm.explosion.RemoteDetonation;
import com.hbm.ntm.network.DetonatorInfoPayload;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Five-hundred-block argument against standing near the target. */
public final class LaserDetonatorItem extends Item {
    public static final double RANGE = 500.0D;

    public LaserDetonatorItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = rayTrace(level, player, RANGE);
        BlockPos position = hit.getType() == HitResult.Type.BLOCK
                ? hit.getBlockPos() : BlockPos.containing(hit.getLocation());

        if (level instanceof ServerLevel server) {
            RemoteDetonation.Attempt attempt = RemoteDetonation.trigger(server, position);
            server.playSound(null, player.getX(), player.getY(), player.getZ(),
                    attempt.compatible() ? ModSounds.TECH_BLEEP.get() : ModSounds.TECH_BOOP.get(),
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            if (attempt.compatible()) DetonatorItem.logAttempt(player, position, "");
            PacketDistributor.sendToPlayer((net.minecraft.server.level.ServerPlayer) player,
                    DetonatorInfoPayload.sourceDefault(DetonatorItem.resultMessage(attempt.result())));
        } else {
            spawnTraceParticles(level, player, position);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Aim & click to detonate!"));
    }

    static BlockHitResult rayTrace(Level level, Player player, double range) {
        Vec3 start = player.getEyePosition(1.0F);
        Vec3 end = start.add(player.getViewVector(1.0F).scale(range));
        // Follow selectable outlines, not merely solid collision shapes.
        return level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
    }

    private static void spawnTraceParticles(Level level, Player player, BlockPos position) {
        Vec3 vector = Vec3.atCenterOf(position).subtract(player.position());
        double length = Math.min(vector.length(), 15.0D);
        if (vector.lengthSqr() == 0.0D) return;
        vector = vector.normalize();
        for (int i = 0; i < length; i++) {
            double distance = level.random.nextDouble() * length + 3.0D;
            level.addParticle(DustParticleOptions.REDSTONE,
                    player.getX() + vector.x * distance,
                    player.getY() + vector.y * distance,
                    player.getZ() + vector.z * distance,
                    0.0D, 0.0D, 0.0D);
        }
    }
}
