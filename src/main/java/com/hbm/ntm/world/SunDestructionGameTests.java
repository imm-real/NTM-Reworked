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
public final class SunDestructionGameTests {
    private SunDestructionGameTests() {
    }

    @GameTest(template = "empty")
    public static void daylightWindowAndAlignmentMatchTheVisibleSun(GameTestHelper helper) {
        helper.assertTrue(SunDestruction.isTargetWindow(0L)
                        && SunDestruction.isTargetWindow(6_000L)
                        && SunDestruction.isTargetWindow(12_000L),
                "The B93 solar shot must be accepted while the vanilla sun is above the horizon");
        helper.assertTrue(!SunDestruction.isTargetWindow(12_001L)
                        && !SunDestruction.isTargetWindow(18_000L)
                        && !SunDestruction.isTargetWindow(23_999L),
                "The B93 must not target the absent nighttime sun");

        Vec3 sun = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 insideEdge = new Vec3(Math.sin(Math.toRadians(9.0D)), Math.cos(Math.toRadians(9.0D)), 0.0D);
        Vec3 outsideEdge = new Vec3(Math.sin(Math.toRadians(11.0D)), Math.cos(Math.toRadians(11.0D)), 0.0D);
        helper.assertTrue(SunDestruction.isAlignedWithSun(insideEdge, sun),
                "A B93 shot within the sun's apparent disc must pass the angular target");
        helper.assertTrue(!SunDestruction.isAlignedWithSun(outsideEdge, sun)
                        && !SunDestruction.isAlignedWithSun(new Vec3(0.0D, -1.0D, 0.0D), sun),
                "A B93 shot outside the sun's apparent disc must fail the angular target");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void solarDestructionIsOneWayAndPersistent(GameTestHelper helper) {
        SunDestructionData data = new SunDestructionData();
        helper.assertTrue(data.destroy(), "A pristine world must accept its first solar destruction");
        helper.assertTrue(!data.destroy(), "The solar destruction transition must be one-way");

        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        SunDestructionData loaded = SunDestructionData.load(saved, helper.getLevel().registryAccess());
        helper.assertTrue(loaded.isDestroyed(), "The destroyed sun must persist in world SavedData");
        helper.succeed();
    }
}
