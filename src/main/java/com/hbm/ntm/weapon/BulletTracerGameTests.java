package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BulletTracerGameTests {
    private BulletTracerGameTests() { }

    @GameTest(template = "empty")
    public static void standardTracerPaletteMatchesTheSourceClientTable(GameTestHelper helper) {
        assertTracer(helper, PepperboxAmmoType.STONE, 0xFFFFBF00, 0xFFFFFFFF, false, "standard");
        assertTracer(helper, Shotgun12GaugeAmmoType.FLECHETTE,
                0xFF8C8C8C, 0xFFCACACA, false, "flechette");
        assertTracer(helper, FiveFiveSixAmmoType.ARMOR_PIERCING,
                0xFFFF6A00, 0xFFFFE28D, false, "armor-piercing");
        assertTracer(helper, Magnum357AmmoType.EXPRESS,
                0xFF9E082E, 0xFFFF8A79, false, "express");
        assertTracer(helper, SevenSixTwoAmmoType.DEPLETED_URANIUM,
                0xFF5CCD41, 0xFFE9FF8D, false, "depleted-uranium");
        assertTracer(helper, SevenSixTwoAmmoType.HIGH_EXPLOSIVE,
                0xFFD8CA00, 0xFFFFF19D, true, "high-explosive");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bulletEntityKeepsSourceTrackingAndZebraOverride(GameTestHelper helper) {
        var type = ModEntities.BULLET.get();
        helper.assertTrue(type.getWidth() == 0.5F && type.getHeight() == 0.5F && type.fireImmune()
                        && type.clientTrackingRange() == 16 && type.updateInterval() == 1
                        && !type.trackDeltas(),
                "MK4 bullet must retain its half-block bounds, fire immunity, 250-block tracking, one-tick updates and disabled velocity updates");

        ServerLevel level = helper.getLevel();
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        owner.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        BulletEntity zebra = new BulletEntity(level, owner, FiveFiveSixAmmoType.HOLLOW_POINT,
                7.5F, 0.0F, owner.getEyePosition(), new Vec3(0.0D, 0.0D, 1.0D), true);
        helper.assertTrue(zebra.tracerDarkColor() == 0xFFFF6A00
                        && zebra.tracerLightColor() == 0xFFFFE28D
                        && !zebra.tracerFullbright(),
                "Every Zebra load must use the source armor-piercing tracer renderer");
        helper.assertTrue(zebra.shouldRenderAtSqrDistance(127.0D * 127.0D)
                        && !zebra.shouldRenderAtSqrDistance(128.0D * 128.0D),
                "MK4 tracer render distance must retain the source half-block range calculation");
        helper.succeed();
    }

    private static void assertTracer(GameTestHelper helper, SednaAmmoType ammo,
                                     int dark, int light, boolean fullbright, String name) {
        helper.assertTrue(ammo.tracerDarkColor() == dark
                        && ammo.tracerLightColor() == light
                        && ammo.tracerFullbright() == fullbright,
                name + " tracer must match LegoClient's source renderer assignment");
    }
}
