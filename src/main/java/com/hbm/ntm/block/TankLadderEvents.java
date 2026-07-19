package com.hbm.ntm.block;

import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashSet;
import java.util.Set;

/** The tank ladder is not a block, but your legs do not need to know that. */
public final class TankLadderEvents {
    private TankLadderEvents() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TankLadderEvents::onPlayerTick);
    }

    private static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (!touchesTankLadder(player)) return;

        double climbSpeed = .15D;
        Vec3 movement = player.getDeltaMovement();
        double x = Mth.clamp(movement.x, -climbSpeed, climbSpeed);
        double y = Math.max(movement.y, -climbSpeed);
        double z = Mth.clamp(movement.z, -climbSpeed, climbSpeed);
        if (player.isShiftKeyDown() && y < 0D) y = 0D;
        if (player.horizontalCollision) y = .2D;
        player.setDeltaMovement(x, y, z);
        player.fallDistance = 0F;
    }

    private static boolean touchesTankLadder(Player player) {
        AABB playerBounds = player.getBoundingBox();
        AABB search = playerBounds.inflate(2D, 1D, 2D);
        BlockPos min = BlockPos.containing(search.minX, search.minY, search.minZ);
        BlockPos max = BlockPos.containing(search.maxX, search.maxY, search.maxZ);
        Set<BlockPos> checkedCores = new HashSet<>();

        for (BlockPos mutable : BlockPos.betweenClosed(min, max)) {
            BlockState state = player.level().getBlockState(mutable);
            if (!state.is(ModBlocks.MACHINE_FLUIDTANK.get())) continue;
            BlockPos position = mutable.immutable();
            BlockPos core = FluidStorageTankBlock.corePosition(position, state);
            if (!checkedCores.add(core)) continue;
            if (FluidStorageTankBlock.ladderBounds(core, state.getValue(FluidStorageTankBlock.FACING))
                    .intersects(playerBounds)) return true;
        }
        return false;
    }
}
