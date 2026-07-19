package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.entity.B93BeamEntity;
import com.hbm.ntm.entity.BlackHoleEntity;
import com.hbm.ntm.entity.RagingVortexEntity;
import com.hbm.ntm.entity.VortexEntity;
import com.hbm.ntm.item.B93Item;
import com.hbm.ntm.nuclear.FleijaExplosionEntity;
import com.hbm.ntm.nuclear.FleijaRainbowCloudEntity;
import com.hbm.ntm.nuclear.NuclearExplosionEntity;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Comparator;
import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class B93GameTests {
    private B93GameTests() { }

    @GameTest(template = "empty")
    public static void releaseFiresOneBeamUsingStoredChargeAsMode(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack gun = new ItemStack(ModItems.GUN_B93.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        B93Item.setEnergy(gun, 7);
        ((B93Item) gun.getItem()).releaseUsing(gun, helper.getLevel(), player,
                B93Item.MAX_USE_DURATION - B93Item.MIN_CHARGE_TICKS);

        List<B93BeamEntity> beams = entities(helper, B93BeamEntity.class);
        helper.assertTrue(beams.size() == 1 && beams.getFirst().mode() == 6,
                "Seven B93 charges must fire one beam in historical mode six");
        helper.assertTrue(B93Item.energy(gun) == 0 && B93Item.animation(gun) == 1,
                "B93 firing must empty the capacitor and begin its reload animation");
        beams.forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eleventhChargeStillTriggersRangeFiftyOverload(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack gun = new ItemStack(ModItems.GUN_B93.get());
        B93Item.setEnergy(gun, 10);
        B93Item.setAnimation(gun, 15);
        ((B93Item) gun.getItem()).inventoryTick(gun, helper.getLevel(), player, 0, true);

        List<FleijaExplosionEntity> explosions = entities(helper, FleijaExplosionEntity.class);
        List<FleijaRainbowCloudEntity> clouds = entities(helper, FleijaRainbowCloudEntity.class);
        helper.assertTrue(B93Item.energy(gun) == 0 && explosions.size() == 1
                        && explosions.getFirst().radius() == 50,
                "The eleventh B93 charge must create the source range-50 FLEIJA overload");
        helper.assertTrue(clouds.size() == 1 && clouds.getFirst().maxAge() == 50,
                "The B93 overload must create its matching rainbow cloud");
        explosions.forEach(Entity::discard);
        clouds.forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modesTwoThroughEightCreateHistoricalHazards(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 5, 5)));
        for (int mode = 2; mode <= 8; mode++) {
            B93BeamEntity.impact(level, mode, center.x, center.y, center.z);
        }

        List<FleijaExplosionEntity> fleijas = entities(helper, FleijaExplosionEntity.class).stream()
                .sorted(Comparator.comparingInt(FleijaExplosionEntity::radius)).toList();
        List<VortexEntity> vortices = entities(helper, VortexEntity.class).stream()
                .sorted(Comparator.comparingDouble(VortexEntity::size)).toList();
        List<RagingVortexEntity> raging = entities(helper, RagingVortexEntity.class).stream()
                .sorted(Comparator.comparingDouble(RagingVortexEntity::size)).toList();
        List<BlackHoleEntity> singularities = entities(helper, BlackHoleEntity.class);
        helper.assertTrue(fleijas.size() == 2 && fleijas.get(0).radius() == 10 && fleijas.get(1).radius() == 20,
                "Modes two and three must create range-10 and range-20 FLEIJA blasts");
        helper.assertTrue(vortices.size() == 2 && vortices.get(0).size() == 1.0F
                        && vortices.get(1).size() == 2.5F,
                "Modes four and five must create source-sized blue vortices");
        helper.assertTrue(raging.size() == 2 && raging.get(0).size() == 2.5F
                        && raging.get(1).size() == 5.0F,
                "Modes six and seven must create source-sized raging vortices");
        helper.assertTrue(singularities.stream().filter(entity -> entity.getClass() == BlackHoleEntity.class)
                        .anyMatch(entity -> entity.size() == 2.0F),
                "Mode eight must create a permanent size-two black hole");
        fleijas.forEach(Entity::discard);
        entities(helper, FleijaRainbowCloudEntity.class).forEach(Entity::discard);
        singularities.forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tenthChargeUsesConfiguredGadgetBlast(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 5, 5)));
        B93BeamEntity.impact(level, 9, center.x, center.y, center.z);
        List<NuclearExplosionEntity> explosions = entities(helper, NuclearExplosionEntity.class);
        helper.assertTrue(explosions.size() == 1
                        && explosions.getFirst().strength() == HbmConfig.GADGET_RADIUS.get() * 2,
                "Mode nine must use the configured Gadget MK5 radius");
        explosions.forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void beamPersistsItsSelectedModeButNotOwnerGrace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        B93BeamEntity beam = new B93BeamEntity(level, player(helper));
        beam.setMode(8);
        beam.tick();
        CompoundTag saved = beam.saveWithoutId(new CompoundTag());
        B93BeamEntity loaded = new B93BeamEntity(ModEntities.B93_BEAM.get(), level);
        loaded.load(saved);
        helper.assertTrue(loaded.mode() == 8 && loaded.ticksInAir() == 0,
                "B93 beams must persist impact mode while restoring the five-tick owner grace");
        beam.discard();
        loaded.discard();
        helper.succeed();
    }

    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        player.setYRot(-90.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static <T extends Entity> List<T> entities(GameTestHelper helper, Class<T> type) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntitiesOfClass(type, new AABB(origin).inflate(96.0D));
    }
}
