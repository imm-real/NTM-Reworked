package com.hbm.ntm.world;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.network.SunStatePayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-authoritative targeting and one-way state transition for the B93 solar event. */
public final class SunDestruction {
    public static final long TARGET_WINDOW_START = 0L;
    public static final long TARGET_WINDOW_END = 12_000L;
    private static final double SUN_HIT_DOT = Math.cos(Math.toRadians(10.0D));

    private SunDestruction() {
    }

    public static boolean tryDestroy(ServerLevel level, LivingEntity shooter) {
        if (!canHitSun(level, shooter.getLookAngle())) return false;

        SunDestructionData data = SunDestructionData.get(level);
        if (!data.destroy()) return false;

        ServerLevel overworld = level.getServer().overworld();
        PacketDistributor.sendToPlayersInDimension(overworld, new SunStatePayload(true, true));
        HbmNtm.LOGGER.warn("{} destroyed the sun with a B93 at game time {}",
                shooter.getScoreboardName(), level.getDayTime());
        return true;
    }

    public static boolean canHitSun(Level level, Vec3 shotDirection) {
        if (level.dimension() != Level.OVERWORLD || !isTargetWindow(level.getDayTime())) return false;
        return isAlignedWithSun(shotDirection, sunDirection(level));
    }

    public static boolean isAlignedWithSun(Vec3 shotDirection, Vec3 sunDirection) {
        Vec3 normalized = shotDirection.normalize();
        Vec3 normalizedSun = sunDirection.normalize();
        return normalized.lengthSqr() > 0.0D && normalizedSun.lengthSqr() > 0.0D
                && normalized.dot(normalizedSun) >= SUN_HIT_DOT;
    }

    public static boolean isTargetWindow(long dayTime) {
        long time = Math.floorMod(dayTime, 24_000L);
        return time >= TARGET_WINDOW_START && time <= TARGET_WINDOW_END;
    }

    /** Exact opposite of the moon quad transformed by vanilla's Overworld sky renderer. */
    public static Vec3 sunDirection(Level level) {
        float angle = level.getSunAngle(1.0F);
        return new Vec3(-Mth.sin(angle), Mth.cos(angle), 0.0D).normalize();
    }
}
