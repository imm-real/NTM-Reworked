package com.hbm.ntm.world;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.network.MoonStatePayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-authoritative targeting and state transition for the B92 moon easter egg. */
public final class MoonDestruction {
    public static final long TARGET_WINDOW_START = 17_000L;
    public static final long TARGET_WINDOW_END = 19_000L;
    public static final long BLACK_NIGHT_START = 13_000L;
    public static final long BLACK_NIGHT_END = 23_000L;
    private static final double MOON_HIT_DOT = Math.cos(Math.toRadians(10.0D));

    private MoonDestruction() {
    }

    public static boolean tryDestroy(ServerLevel level, LivingEntity shooter) {
        if (!canHitMoon(level, shooter.getLookAngle())) return false;

        MoonDestructionData data = MoonDestructionData.get(level);
        if (!data.destroy()) return false;

        ServerLevel overworld = level.getServer().overworld();
        MoonDebrisCraters.begin(overworld);
        PacketDistributor.sendToPlayersInDimension(overworld, new MoonStatePayload(true, true));
        HbmNtm.LOGGER.warn("{} destroyed the moon with a B92 at game time {}",
                shooter.getScoreboardName(), level.getDayTime());
        return true;
    }

    public static boolean canHitMoon(Level level, Vec3 shotDirection) {
        if (level.dimension() != Level.OVERWORLD || !isTargetWindow(level.getDayTime())) return false;
        return isAlignedWithMoon(shotDirection, moonDirection(level));
    }

    public static boolean isAlignedWithMoon(Vec3 shotDirection, Vec3 moonDirection) {
        Vec3 normalized = shotDirection.normalize();
        Vec3 normalizedMoon = moonDirection.normalize();
        return normalized.lengthSqr() > 0.0D && normalizedMoon.lengthSqr() > 0.0D
                && normalized.dot(normalizedMoon) >= MOON_HIT_DOT;
    }

    public static boolean isTargetWindow(long dayTime) {
        long time = Math.floorMod(dayTime, 24_000L);
        return time >= TARGET_WINDOW_START && time <= TARGET_WINDOW_END;
    }

    public static boolean isBlackNight(long dayTime) {
        long time = Math.floorMod(dayTime, 24_000L);
        return time >= BLACK_NIGHT_START && time <= BLACK_NIGHT_END;
    }

    /** Matches the moon quad transformed by vanilla's Overworld sky renderer. */
    public static Vec3 moonDirection(Level level) {
        float angle = level.getSunAngle(1.0F);
        return new Vec3(Mth.sin(angle), -Mth.cos(angle), 0.0D).normalize();
    }
}
