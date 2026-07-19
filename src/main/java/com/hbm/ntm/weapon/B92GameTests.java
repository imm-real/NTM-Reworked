package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.B92BeamEntity;
import com.hbm.ntm.item.B92EnergyCellItem;
import com.hbm.ntm.item.B92Item;
import com.hbm.ntm.item.WeaponizedStarblasterCellItem;
import com.hbm.ntm.nuclear.FleijaExplosionEntity;
import com.hbm.ntm.nuclear.FleijaRainbowCloudEntity;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class B92GameTests {
    private B92GameTests() { }

    @GameTest(template = "empty")
    public static void sneakChargeUsesThirtyTickAnimationAndAwardsAtFifteen(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack gun = new ItemStack(ModItems.GUN_B92.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setShiftKeyDown(true);
        gun.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(B92Item.animation(gun) == 1 && B92Item.energy(gun) == 0,
                "Sneak-use must start an empty B92 charge cycle");

        tickGun(helper, player, gun, 14);
        helper.assertTrue(B92Item.animation(gun) == 15 && B92Item.energy(gun) == 0,
                "The charge must not be awarded before source animation state 15");
        tickGun(helper, player, gun, 1);
        helper.assertTrue(B92Item.animation(gun) == 16 && B92Item.energy(gun) == 1,
                "State 15 must award exactly one B92 charge");
        tickGun(helper, player, gun, 15);
        helper.assertTrue(B92Item.animation(gun) == 0 && B92Item.energy(gun) == 1,
                "The complete source charge animation must unlock after 30 updates");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tenTickReleaseFiresEveryStoredChargeAtOnce(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack gun = new ItemStack(ModItems.GUN_B92.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        B92Item.setEnergy(gun, 4);
        ((B92Item) gun.getItem()).releaseUsing(gun, helper.getLevel(), player,
                B92Item.MAX_USE_DURATION - B92Item.MIN_CHARGE_TICKS);

        List<B92BeamEntity> beams = entities(helper, B92BeamEntity.class);
        helper.assertTrue(beams.size() == 4,
                "A B92 release must spawn one simultaneous beam per stored charge");
        helper.assertTrue(B92Item.energy(gun) == 0 && B92Item.animation(gun) == 1,
                "Firing must empty the capacitors and immediately start auto-charge");
        beams.forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void energyCellStealsChargeButAlwaysLeavesOneInGun(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack gun = new ItemStack(ModItems.GUN_B92.get());
        ItemStack cell = new ItemStack(ModItems.GUN_B92_AMMO.get());
        player.getInventory().items.set(0, gun);
        player.getInventory().items.set(1, cell);
        B92Item.setEnergy(gun, 3);

        B92EnergyCellItem item = (B92EnergyCellItem) cell.getItem();
        item.inventoryTick(cell, helper.getLevel(), player, 1, false);
        item.inventoryTick(cell, helper.getLevel(), player, 1, false);
        item.inventoryTick(cell, helper.getLevel(), player, 1, false);
        helper.assertTrue(B92Item.energy(gun) == 1 && B92EnergyCellItem.energy(cell) == 2,
                "The passive cell must drain in slot order but refuse to take the B92's final charge");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eleventhChargeTriggersRangeFiftyFleijaOverload(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack gun = new ItemStack(ModItems.GUN_B92.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        B92Item.setEnergy(gun, 10);
        B92Item.setAnimation(gun, 1);
        tickGun(helper, player, gun, 15);

        List<FleijaExplosionEntity> explosions = entities(helper, FleijaExplosionEntity.class);
        List<FleijaRainbowCloudEntity> clouds = entities(helper, FleijaRainbowCloudEntity.class);
        helper.assertTrue(B92Item.energy(gun) == 0 && explosions.size() == 1
                        && explosions.getFirst().radius() == 50,
                "Charging beyond ten must reset energy and create the source range-50 FLEIJA blast");
        helper.assertTrue(clouds.size() == 1 && clouds.getFirst().maxAge() == 50,
                "B92 overload must create the matching 50-tick rainbow cloud");
        explosions.forEach(Entity::discard);
        clouds.forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void completedSmallFleijaStillDealsItsFinalDamagePulse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 4, 5)));
        Zombie target = EntityType.ZOMBIE.create(level);
        if (target == null) throw new IllegalStateException("Could not create B92 test zombie");
        target.setPos(center.add(1.0D, -0.5D, 0.0D));
        level.addFreshEntity(target);

        FleijaExplosionEntity explosion = FleijaExplosionEntity.create(
                level, center.x, center.y, center.z, 1);
        level.addFreshEntity(explosion);
        explosion.tick();

        helper.assertTrue(!target.isAlive() || target.getHealth() < 20.0F,
                "A FLEIJA that completes terrain processing in one tick must still deal nuclear damage");
        helper.assertTrue(!explosion.isAlive(),
                "The range-one processor must complete at the source default blast speed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blockImpactIsStoredThenExplodesOneTickLate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player owner = player(helper);
        Vec3 start = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
        BlockPos wall = helper.absolutePos(new BlockPos(5, 2, 2));
        level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        B92BeamEntity beam = new B92BeamEntity(level, owner);
        beam.setPos(start);
        beam.setDeltaMovement(new Vec3(4.5D, 0.0D, 0.0D));
        level.addFreshEntity(beam);

        beam.tick();
        helper.assertTrue(beam.pendingBlock() != null && entities(helper, FleijaExplosionEntity.class).isEmpty(),
                "The first block intercept must only cache the impacted block");
        beam.tick();
        List<FleijaExplosionEntity> explosions = entities(helper, FleijaExplosionEntity.class);
        helper.assertTrue(explosions.size() == 1 && explosions.getFirst().radius() == 10,
                "A still-solid cached block must trigger the B92's range-10 blast next update");
        beam.discard();
        explosions.forEach(Entity::discard);
        entities(helper, FleijaRainbowCloudEntity.class).forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shortOrSneakingReleasePreservesStoredCharge(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack gun = new ItemStack(ModItems.GUN_B92.get());
        B92Item.setEnergy(gun, 3);
        B92Item item = (B92Item) gun.getItem();

        item.releaseUsing(gun, helper.getLevel(), player,
                B92Item.MAX_USE_DURATION - (B92Item.MIN_CHARGE_TICKS - 1));
        helper.assertTrue(B92Item.energy(gun) == 3 && B92Item.animation(gun) == 0
                        && entities(helper, B92BeamEntity.class).isEmpty(),
                "A release before ten ticks must leave all B92 charge untouched");

        player.setShiftKeyDown(true);
        player.setPose(Pose.CROUCHING);
        item.releaseUsing(gun, helper.getLevel(), player,
                B92Item.MAX_USE_DURATION - B92Item.MIN_CHARGE_TICKS);
        helper.assertTrue(B92Item.energy(gun) == 3 && entities(helper, B92BeamEntity.class).isEmpty(),
                "Releasing while sneaking must suppress fire and preserve charge");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void beamReloadResetsFiveTickOwnerCollisionGrace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player owner = player(helper);
        B92BeamEntity beam = new B92BeamEntity(level, owner);
        beam.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 6, 5))));
        beam.setDeltaMovement(Vec3.ZERO);
        beam.tick();
        beam.tick();
        beam.tick();
        helper.assertTrue(beam.ticksInAir() == 3, "The beam must count its first three airborne updates");

        CompoundTag saved = beam.saveWithoutId(new CompoundTag());
        B92BeamEntity reloaded = new B92BeamEntity(ModEntities.B92_BEAM.get(), level);
        reloaded.load(saved);
        helper.assertTrue(reloaded.ticksInAir() == 0,
                "EntityB92Beam intentionally did not persist ticksInAir across reloads");
        beam.discard();
        reloaded.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rainbowCloudReloadUsesLoadingDimensionsAndExpiresNextTick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 5, 5)));
        FleijaRainbowCloudEntity cloud = FleijaRainbowCloudEntity.create(
                level, center.x, center.y, center.z, 10);
        cloud.tick();
        helper.assertTrue(cloud.age() == 1 && cloud.scale() == 1.0F && cloud.getBbWidth() == 20.0F,
                "A fresh timed rainbow cloud must use its 20x40 constructor state");

        CompoundTag saved = cloud.saveWithoutId(new CompoundTag());
        cloud.discard();
        FleijaRainbowCloudEntity reloaded = new FleijaRainbowCloudEntity(
                ModEntities.FLEIJA_RAINBOW_CLOUD.get(), level);
        reloaded.load(saved);
        helper.assertTrue(reloaded.maxAge() == 0 && reloaded.getBbWidth() == 1.0F
                        && reloaded.getBbHeight() == 4.0F,
                "Reload must lose watched max age and restore the source 1x4 loading dimensions");
        reloaded.tick();
        helper.assertTrue(!reloaded.isAlive() && reloaded.age() == 0 && reloaded.scale() == 2.0F,
                "A reloaded cloud must expire, reset age, then still increment scale on its next update");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void riggedCellDetonatesOnlyAfterSourceFiftySecondFuse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 5, 5)));
        ItemStack stack = new ItemStack(ModItems.WEAPONIZED_STARBLASTER_CELL.get());
        ItemEntity dropped = new ItemEntity(level, center.x, center.y, center.z, stack);
        level.addFreshEntity(dropped);
        WeaponizedStarblasterCellItem item = (WeaponizedStarblasterCellItem) stack.getItem();

        dropped.tickCount = WeaponizedStarblasterCellItem.FUSE_TICKS;
        item.onEntityItemUpdate(stack, dropped);
        helper.assertTrue(dropped.isAlive() && entities(helper, FleijaExplosionEntity.class).isEmpty(),
                "The source uses >1000 ticks, so a rigged cell must survive exactly fifty seconds");

        dropped.tickCount++;
        item.onEntityItemUpdate(stack, dropped);
        List<FleijaExplosionEntity> explosions = entities(helper, FleijaExplosionEntity.class);
        helper.assertTrue(!dropped.isAlive() && explosions.size() == 1
                        && explosions.getFirst().radius() == WeaponizedStarblasterCellItem.FLEIJA_RADIUS,
                "The following update must consume the cell and create a range-100 FLEIJA");
        explosions.forEach(Entity::discard);
        entities(helper, FleijaRainbowCloudEntity.class).forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fleijaUsesDimensionBuildHeight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(5, 0, 5));
        // ExplosionFleija receives Java int casts, so negative fractional centers
        // Truncate toward zero instead of flooring the block coordinate.
        int centerX = (int) (base.getX() + 0.5D);
        int centerZ = (int) (base.getZ() + 0.5D);
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        BlockPos bottomLayer = new BlockPos(centerX, minY, centerZ);
        BlockPos aboveBottom = bottomLayer.above();
        BlockPos topLayer = new BlockPos(centerX, maxY, centerZ);
        level.setBlockAndUpdate(bottomLayer, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(aboveBottom, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(topLayer, Blocks.STONE.defaultBlockState());

        FleijaExplosionEntity atFloor = FleijaExplosionEntity.create(
                level, centerX, minY, centerZ, 1);
        atFloor.tick();
        helper.assertTrue(level.getBlockState(aboveBottom).isAir(),
                "FLEIJA must delete blocks below absolute Y=1 down to the dimension floor");
        helper.assertTrue(level.getBlockState(bottomLayer).is(Blocks.STONE),
                "FLEIJA must preserve the bottom-most layer like the source preserved Y=0");

        FleijaExplosionEntity atCeiling = FleijaExplosionEntity.create(
                level, centerX, maxY, centerZ, 1);
        atCeiling.tick();
        helper.assertTrue(level.getBlockState(topLayer).isAir(),
                "FLEIJA must use the dimension ceiling instead of the legacy Y=255 ceiling");
        atFloor.discard();
        atCeiling.discard();
        helper.succeed();
    }

    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        player.setYRot(-90.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickGun(GameTestHelper helper, Player player, ItemStack gun, int ticks) {
        B92Item item = (B92Item) gun.getItem();
        for (int i = 0; i < ticks; i++) item.inventoryTick(gun, helper.getLevel(), player, 0, true);
    }

    private static <T extends Entity> List<T> entities(GameTestHelper helper, Class<T> type) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntitiesOfClass(type, new AABB(origin).inflate(96.0D));
    }
}
