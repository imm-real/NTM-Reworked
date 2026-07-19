package com.hbm.ntm.world;

import com.hbm.ntm.HbmNtm;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MoonDestructionGameTests {
    private MoonDestructionGameTests() {
    }

    @GameTest(template = "empty")
    public static void targetWindowAndAlignmentAreNarrow(GameTestHelper helper) {
        helper.assertTrue(MoonDestruction.isTargetWindow(17_000L)
                        && MoonDestruction.isTargetWindow(18_000L)
                        && MoonDestruction.isTargetWindow(19_000L),
                "The B92 moon shot must be available through the intended midnight window");
        helper.assertTrue(!MoonDestruction.isTargetWindow(16_999L)
                        && !MoonDestruction.isTargetWindow(19_001L),
                "The B92 must not destroy the moon outside the midnight window");

        Vec3 moon = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 insideEdge = new Vec3(Math.sin(Math.toRadians(9.0D)), Math.cos(Math.toRadians(9.0D)), 0.0D);
        Vec3 outsideEdge = new Vec3(Math.sin(Math.toRadians(11.0D)), Math.cos(Math.toRadians(11.0D)), 0.0D);
        helper.assertTrue(MoonDestruction.isAlignedWithMoon(insideEdge, moon),
                "A B92 shot within the moon's apparent disc must pass the angular target");
        helper.assertTrue(!MoonDestruction.isAlignedWithMoon(outsideEdge, moon)
                        && !MoonDestruction.isAlignedWithMoon(new Vec3(0.0D, -1.0D, 0.0D), moon),
                "A B92 shot outside the moon's apparent disc must fail the angular target");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void destructionIsOneWayAndPersistent(GameTestHelper helper) {
        MoonDestructionData data = new MoonDestructionData();
        helper.assertTrue(data.destroy(), "A pristine world must accept its first moon destruction");
        helper.assertTrue(!data.destroy(), "The moon destruction transition must be one-way");

        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        MoonDestructionData loaded = MoonDestructionData.load(saved, helper.getLevel().registryAccess());
        helper.assertTrue(loaded.isDestroyed(), "The destroyed moon must persist in world SavedData");
        helper.assertTrue(MoonDestruction.isBlackNight(13_000L)
                        && MoonDestruction.isBlackNight(18_000L)
                        && MoonDestruction.isBlackNight(23_000L)
                        && !MoonDestruction.isBlackNight(12_999L),
                "Destroyed-moon blackout must cover every full Overworld night");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void debrisCraterScaleIsBoundedAndSizeDependent(GameTestHelper helper) {
        float smallRadius = MoonDebrisCraters.radiusForSize(0.8F);
        float largeRadius = MoonDebrisCraters.radiusForSize(8.0F);
        float smallDepth = MoonDebrisCraters.depthForSize(0.8F);
        float largeDepth = MoonDebrisCraters.depthForSize(8.0F);
        helper.assertTrue(Math.abs(smallRadius - 1.75F) < 0.001F
                        && Math.abs(largeRadius - 5.25F) < 0.001F && largeRadius > smallRadius,
                "Debris footprints must produce bounded, visibly different crater radii");
        helper.assertTrue(Math.abs(smallDepth - 1.25F) < 0.001F
                        && Math.abs(largeDepth - 3.4F) < 0.001F && largeDepth > smallDepth,
                "Debris footprints must produce bounded, visibly different crater depths");
        helper.succeed();
    }
}
