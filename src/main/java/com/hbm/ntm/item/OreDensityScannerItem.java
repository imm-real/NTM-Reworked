package com.hbm.ntm.item;

import com.hbm.ntm.worldgen.LegacyOctaveNoise2D;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;

/** Deterministic bedrock-ore density field with a handheld gossip device. */
public final class OreDensityScannerItem extends Item {
    private static final LegacyOctaveNoise2D LEVEL = new LegacyOctaveNoise2D(2_114_043L, 4);
    private static final LegacyOctaveNoise2D[] ORES = new LegacyOctaveNoise2D[OreType.values().length];

    static {
        for (OreType type : OreType.values()) {
            ORES[type.ordinal()] = new LegacyOctaveNoise2D(2_082_127L + type.ordinal(), 4);
        }
    }

    public OreDensityScannerItem() {
        super(new Properties().stacksTo(1));
    }

    public static double density(int x, int z, OreType type) {
        double scale = 0.01D;
        return Mth.clamp(Math.abs(LEVEL.value(x * scale, z * scale)
                * ORES[type.ordinal()].value(x * scale, z * scale)) * 0.05D, 0.0D, 2.0D);
    }

    public static Reading reading(int x, int z) {
        double[] densities = new double[OreType.values().length];
        double total = 0.0D;
        for (OreType type : OreType.values()) {
            double density = density(x, z, type);
            densities[type.ordinal()] = density;
            total += density;
        }
        double average = total / densities.length;
        int tier = average > 1.5D ? 4 : average > 1.0D ? 3 : average > 0.75D ? 2 : 1;
        return new Reading(densities, average, tier);
    }

    public static String densityKey(double density) {
        if (density <= 0.1D) return "item.hbm.ore_density_scanner.verypoor";
        if (density <= 0.35D) return "item.hbm.ore_density_scanner.poor";
        if (density <= 0.75D) return "item.hbm.ore_density_scanner.low";
        if (density >= 1.9D) return "item.hbm.ore_density_scanner.excellent";
        if (density >= 1.65D) return "item.hbm.ore_density_scanner.veryhigh";
        if (density >= 1.25D) return "item.hbm.ore_density_scanner.high";
        return "item.hbm.ore_density_scanner.moderate";
    }

    public static ChatFormatting densityColor(double density) {
        if (density <= 0.1D) return ChatFormatting.DARK_RED;
        if (density <= 0.35D) return ChatFormatting.RED;
        if (density <= 0.75D) return ChatFormatting.GOLD;
        if (density > 2.0D) return ChatFormatting.LIGHT_PURPLE;
        if (density >= 1.9D) return ChatFormatting.AQUA;
        if (density >= 1.65D) return ChatFormatting.BLUE;
        if (density >= 1.25D) return ChatFormatting.GREEN;
        return ChatFormatting.YELLOW;
    }

    public enum OreType {
        LIGHT_METAL("light"),
        HEAVY_METAL("heavy"),
        RARE_EARTH("rare"),
        ACTINIDE("actinide"),
        NON_METAL("nonmetal"),
        CRYSTALLINE("crystal");

        private final String suffix;

        OreType(String suffix) {
            this.suffix = suffix;
        }

        public String suffix() {
            return suffix;
        }
    }

    public record Reading(double[] densities, double average, int tier) {
        public int boreFluidAmount() {
            return tier == 4 ? 2_000 : tier >= 2 ? 1_000 : 0;
        }

        public String boreFluidKey() {
            return switch (tier) {
                case 2 -> "block.minecraft.water";
                case 3 -> "hbmfluid.sulfuric_acid";
                case 4 -> "hbmfluid.solvent";
                default -> "";
            };
        }
    }
}
