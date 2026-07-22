package com.hbm.ntm.weapon;

import com.hbm.ntm.network.SpentCasingPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SpentCasingEffects {
    private SpentCasingEffects() { }

    public static void eject(LivingEntity shooter, SpentCasingPreset preset,
                             double frontOffset, double heightOffset, double sideOffset,
                             double frontMotion, double heightMotion, double sideMotion,
                             double variance, float momentumPitch, float momentumYaw) {
        if (!(shooter.level() instanceof ServerLevel)) return;
        if (shooter.isCrouching()) heightOffset -= 0.075D;

        float pitch = shooter.getXRot();
        float yaw = shooter.getYRot();
        Vec3 offset = rotate(sideOffset, heightOffset, frontOffset, pitch, yaw);
        Vec3 localMotion = rotate(sideMotion, heightMotion, frontMotion, pitch, yaw);
        Vec3 shooterMotion = shooter.getDeltaMovement();
        double motionY = shooterMotion.y + localMotion.y + shooter.getRandom().nextGaussian() * variance;
        if (shooter instanceof Player player && player.getAbilities().flying) motionY -= 0.04D;

        Vec3 eye = shooter.getEyePosition();
        SpentCasingPayload payload = new SpentCasingPayload(
                preset.ordinal(), eye.x + offset.x, eye.y + offset.y, eye.z + offset.z,
                shooterMotion.x + localMotion.x + shooter.getRandom().nextGaussian() * variance,
                motionY,
                shooterMotion.z + localMotion.z + shooter.getRandom().nextGaussian() * variance,
                yaw, pitch, momentumPitch, momentumYaw);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(shooter, payload);
    }

    static Vec3 rotate(double side, double height, double front, float pitch, float yaw) {
        return new Vec3(side, height, front)
                .xRot(-pitch * Mth.DEG_TO_RAD)
                .yRot(-yaw * Mth.DEG_TO_RAD);
    }
}
