package com.hbm.ntm.radiation;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Source-formatted Geiger Counter and Dosimeter chat readouts. */
public final class RadiationReadout {
    private static final double DOSIMETER_LIMIT = 3.6D;

    private RadiationReadout() {
    }

    public static GeigerReading geigerReading(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            throw new IllegalArgumentException("Geiger readings require a server-side player");
        }
        double playerRadiation = truncate(RadiationSystem.data(player).radiation(), 10.0D);
        double chunkRadiation = truncate(
                ChunkRadiationData.get(serverLevel).get(player.blockPosition()), 10.0D);
        double environmentalRadiation = truncate(RadiationSystem.data(player).radBuf(), 10.0D);
        double resistance = RadiationSystem.calculateResistance(player);
        double resistanceCoefficient = truncate(resistance, 100.0D);
        double radiationModifier = Math.pow(10.0D, -resistance);
        double resistancePercent = truncate(100.0D - radiationModifier * 100.0D, 100.0D);
        return new GeigerReading(chunkRadiation, environmentalRadiation, playerRadiation,
                resistancePercent, resistanceCoefficient);
    }

    public static DosimeterReading dosimeterReading(float environmentalRadiation) {
        double displayed = truncate(environmentalRadiation, 10.0D);
        boolean overLimit = displayed > DOSIMETER_LIMIT;
        return new DosimeterReading(overLimit ? DOSIMETER_LIMIT : displayed, overLimit);
    }

    public static void sendGeiger(ServerPlayer player) {
        GeigerReading reading = geigerReading(player);
        player.sendSystemMessage(header("geiger.title"));
        player.sendSystemMessage(line("geiger.chunkRad", reading.chunkRadiation() + " RAD/s",
                colorForRate(reading.chunkRadiation())));
        player.sendSystemMessage(line("geiger.envRad", reading.environmentalRadiation() + " RAD/s",
                colorForRate(reading.environmentalRadiation())));
        player.sendSystemMessage(line("geiger.playerRad", reading.playerRadiation() + " RAD",
                colorForDose(reading.playerRadiation())));
        player.sendSystemMessage(line("geiger.playerRes",
                reading.resistancePercent() + "% (" + reading.resistanceCoefficient() + ")",
                reading.resistanceCoefficient() > 0 ? ChatFormatting.GREEN : ChatFormatting.WHITE));
    }

    public static void sendDosimeter(ServerPlayer player) {
        DosimeterReading reading = dosimeterReading(RadiationSystem.data(player).radBuf());
        player.sendSystemMessage(header("geiger.title.dosimeter"));
        player.sendSystemMessage(line("geiger.envRad",
                (reading.overLimit() ? ">" : "") + reading.environmentalRadiation() + " RAD/s",
                colorForRate(reading.environmentalRadiation())));
    }

    public static ChatFormatting colorForRate(double radiationPerSecond) {
        if (radiationPerSecond == 0) return ChatFormatting.GREEN;
        if (radiationPerSecond < 1) return ChatFormatting.YELLOW;
        if (radiationPerSecond < 10) return ChatFormatting.GOLD;
        if (radiationPerSecond < 100) return ChatFormatting.RED;
        if (radiationPerSecond < 1_000) return ChatFormatting.DARK_RED;
        return ChatFormatting.DARK_GRAY;
    }

    public static ChatFormatting colorForDose(double radiation) {
        if (radiation < 200) return ChatFormatting.GREEN;
        if (radiation < 400) return ChatFormatting.YELLOW;
        if (radiation < 600) return ChatFormatting.GOLD;
        if (radiation < 800) return ChatFormatting.RED;
        if (radiation < 1_000) return ChatFormatting.DARK_RED;
        return ChatFormatting.DARK_GRAY;
    }

    private static Component header(String key) {
        return Component.literal("===== ☢ ")
                .append(Component.translatable(key))
                .append(Component.literal(" ☢ ====="))
                .withStyle(ChatFormatting.GOLD);
    }

    private static Component line(String key, String value, ChatFormatting valueColor) {
        return Component.translatable(key)
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" " + value).withStyle(valueColor));
    }

    private static double truncate(double value, double factor) {
        return ((int) (value * factor)) / factor;
    }

    public record GeigerReading(double chunkRadiation, double environmentalRadiation,
                                double playerRadiation, double resistancePercent,
                                double resistanceCoefficient) {
    }

    public record DosimeterReading(double environmentalRadiation, boolean overLimit) {
    }
}
