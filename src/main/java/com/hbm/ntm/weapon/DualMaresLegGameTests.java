package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.DualMaresLegItem;
import com.hbm.ntm.item.SednaGunItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
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
public final class DualMaresLegGameTests {
    private DualMaresLegGameTests() { }

    @GameTest(template = "empty")
    public static void sourceConfigurationAndSeparateDefaultAmmoContainerArePreserved(
            GameTestHelper helper) {
        DualMaresLegItem gun = ModItems.GUN_MARESLEG_AKIMBO.get();
        ItemStack fresh = new ItemStack(gun);
        helper.assertTrue(DualMaresLegItem.DURABILITY == 600
                        && DualMaresLegItem.DRAW_TICKS == 5
                        && DualMaresLegItem.FIRE_DELAY == 20
                        && DualMaresLegItem.RELOAD_BEGIN_TICKS == 22
                        && DualMaresLegItem.RELOAD_CYCLE_TICKS == 10
                        && DualMaresLegItem.RELOAD_END_TICKS == 13
                        && DualMaresLegItem.JAM_TICKS == 24,
                "Dual Maresleg timing and durability must match both XFactory12ga configs");
        helper.assertTrue(DualMaresLegItem.CAPACITY == 6
                        && DualMaresLegItem.BASE_DAMAGE == 16.0F
                        && DualMaresLegItem.AMMO_SPREAD_MULTIPLIER == 1.35F
                        && DualMaresLegItem.HIP_SPREAD == 0.0F,
                "Each receiver must retain the source six-shell 16-damage 1.35-spread values");
        helper.assertTrue(!gun.gunAutomatic() && !gun.gunSecondaryAutomatic()
                        && gun.gunHasMirroredHud()
                        && !gun.gunAiming(fresh)
                        && gun.gunCrosshair() == SednaCrosshair.L_CIRCLE,
                "Both receivers are semi-automatic, have no aim toggle, and use L_CIRCLE");
        helper.assertTrue(DualMaresLegItem.rounds(fresh, 0) == 0
                        && DualMaresLegItem.rounds(fresh, 1) == 0
                        && DualMaresLegItem.loadedAmmo(fresh, 0) == Shotgun12GaugeAmmoType.BUCKSHOT
                        && DualMaresLegItem.loadedAmmo(fresh, 1) == Shotgun12GaugeAmmoType.BUCKSHOT,
                "Fresh weapon tubes must stay empty while the separate default container remains 24 Buckshot");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void primaryAndSecondaryFireIndependentReceiversAndOffsets(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.BUCKSHOT, 6, 6);
        ItemStack gun = player.getMainHandItem();
        double playerX = player.getX();
        int pellets = Shotgun12GaugeAmmoType.BUCKSHOT.projectiles();

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(DualMaresLegItem.rounds(gun, 0) == 5
                        && DualMaresLegItem.rounds(gun, 1) == 6
                        && DualMaresLegItem.state(gun, 0) == DualMaresLegItem.GunState.COOLDOWN
                        && DualMaresLegItem.timer(gun, 0) == 20
                        && DualMaresLegItem.state(gun, 1) == DualMaresLegItem.GunState.IDLE,
                "Primary must fire only source config zero with its 20-tick cycle");

        SednaGunItem.handleInput(player, GunInput.SECONDARY);
        helper.assertTrue(DualMaresLegItem.rounds(gun, 0) == 5
                        && DualMaresLegItem.rounds(gun, 1) == 5
                        && DualMaresLegItem.state(gun, 1) == DualMaresLegItem.GunState.COOLDOWN,
                "Secondary must fire only source config one");

        List<BulletEntity> bullets = bullets(helper, player).stream()
                .sorted(Comparator.comparingDouble(BulletEntity::getX)).toList();
        helper.assertTrue(bullets.size() == pellets * 2,
                "One press on each trigger must spawn one complete pellet group per receiver");
        for (int i = 0; i < pellets; i++) {
            helper.assertTrue(Math.abs(bullets.get(i).getX() - (playerX - 0.1875D)) < 0.0001D,
                    "Config one must use the source -0.1875 side offset");
            helper.assertTrue(Math.abs(bullets.get(pellets + i).getX() - (playerX + 0.1875D)) < 0.0001D,
                    "Config zero must use the source +0.1875 side offset");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void concurrentSequentialReloadTransfersOneShellPerBoundary(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.SLUG, 0, 0);
        ItemStack gun = player.getMainHandItem();
        DualMaresLegItem.setTestState(gun, 0, DualMaresLegItem.GunState.IDLE, 0, 2,
                Shotgun12GaugeAmmoType.SLUG, 0.0F);
        player.getInventory().add(Shotgun12GaugeAmmoType.BUCKSHOT.createStack(
                ModItems.AMMO_STANDARD.get(), 12));
        player.getInventory().add(Shotgun12GaugeAmmoType.SLUG.createStack(
                ModItems.AMMO_STANDARD.get(), 8));

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(DualMaresLegItem.state(gun, 0) == DualMaresLegItem.GunState.RELOADING
                        && DualMaresLegItem.state(gun, 1) == DualMaresLegItem.GunState.RELOADING
                        && DualMaresLegItem.timer(gun, 0) == 22
                        && DualMaresLegItem.timer(gun, 1) == 22,
                "One reload press must start both independent 22-tick tube openings");
        tickHeld(helper, player, 21);
        helper.assertTrue(DualMaresLegItem.rounds(gun, 0) == 2
                        && DualMaresLegItem.rounds(gun, 1) == 0,
                "No shell may transfer before the source 22-tick opening boundary");
        tickHeld(helper, player, 1);
        helper.assertTrue(DualMaresLegItem.rounds(gun, 0) == 3
                        && DualMaresLegItem.rounds(gun, 1) == 1
                        && DualMaresLegItem.timer(gun, 0) == 10
                        && DualMaresLegItem.timer(gun, 1) == 10
                        && DualMaresLegItem.animation(gun, 0) == DualMaresLegItem.GunAnimation.RELOAD_CYCLE,
                "Each boundary must insert exactly one shell per receiver and continue the 10-tick cycle");
        helper.assertTrue(DualMaresLegItem.loadedAmmo(gun, 0) == Shotgun12GaugeAmmoType.SLUG
                        && DualMaresLegItem.loadedAmmo(gun, 1) == Shotgun12GaugeAmmoType.BUCKSHOT,
                "A partial tube keeps its identity while an empty tube adopts the first compatible stack");
        tickHeld(helper, player, 10 * 5);
        helper.assertTrue(DualMaresLegItem.rounds(gun, 0) == 6
                        && DualMaresLegItem.rounds(gun, 1) == 6,
                "Both tubes must fill to six through repeated single-shell cycles");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void scarceAmmoPrefersConfigZeroAtTheSharedBoundary(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.BUCKSHOT, 0, 0);
        ItemStack gun = player.getMainHandItem();
        player.getInventory().add(Shotgun12GaugeAmmoType.BUCKSHOT.createStack(
                ModItems.AMMO_STANDARD.get(), 1));

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        tickHeld(helper, player, 22);
        helper.assertTrue(DualMaresLegItem.rounds(gun, 0) == 1
                        && DualMaresLegItem.rounds(gun, 1) == 0,
                "When ammo is scarce, config zero must consume first just like the source update order");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sharedCancelStopsConfigZeroWhileConfigOneKeepsCycling(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.BUCKSHOT, 0, 0);
        ItemStack gun = player.getMainHandItem();
        player.getInventory().add(Shotgun12GaugeAmmoType.BUCKSHOT.createStack(
                ModItems.AMMO_STANDARD.get(), 12));

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        tickHeld(helper, player, 5);
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        tickHeld(helper, player, 17);
        helper.assertTrue(DualMaresLegItem.rounds(gun, 0) == 1
                        && DualMaresLegItem.state(gun, 0) == DualMaresLegItem.GunState.DRAWING
                        && DualMaresLegItem.timer(gun, 0) == 13
                        && DualMaresLegItem.animation(gun, 0) == DualMaresLegItem.GunAnimation.RELOAD_END,
                "Cancel must let config zero's in-progress shell finish before closing its tube");
        helper.assertTrue(DualMaresLegItem.rounds(gun, 1) == 1
                        && DualMaresLegItem.state(gun, 1) == DualMaresLegItem.GunState.RELOADING
                        && DualMaresLegItem.timer(gun, 1) == 10,
                "Config zero terminates first and clears the stack-shared source cancel flag,"
                        + " so config one deliberately keeps cycling");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dryFireStaysOnItsOwnReceiver(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.BUCKSHOT, 0, 0);
        ItemStack gun = player.getMainHandItem();
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(DualMaresLegItem.animation(gun, 0) == DualMaresLegItem.GunAnimation.CYCLE_DRY
                        && DualMaresLegItem.state(gun, 0) == DualMaresLegItem.GunState.DRAWING
                        && DualMaresLegItem.timer(gun, 0) == 20
                        && DualMaresLegItem.state(gun, 1) == DualMaresLegItem.GunState.IDLE,
                "An empty primary click must dry-cycle only config zero");
        SednaGunItem.handleInput(player, GunInput.SECONDARY);
        helper.assertTrue(DualMaresLegItem.animation(gun, 1) == DualMaresLegItem.GunAnimation.CYCLE_DRY
                        && DualMaresLegItem.state(gun, 1) == DualMaresLegItem.GunState.DRAWING
                        && DualMaresLegItem.timer(gun, 1) == 20,
                "An empty secondary click must dry-cycle only config one");
        tickHeld(helper, player, 20);
        helper.assertTrue(DualMaresLegItem.state(gun, 0) == DualMaresLegItem.GunState.IDLE
                        && DualMaresLegItem.state(gun, 1) == DualMaresLegItem.GunState.IDLE,
                "Both receivers must return to idle after the 20-tick dry cycle");
        helper.succeed();
    }

    private static Player armedPlayer(GameTestHelper helper, Shotgun12GaugeAmmoType ammo,
                                      int leftRounds, int rightRounds) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_MARESLEG_AKIMBO.get());
        DualMaresLegItem.setTestState(gun, 0, DualMaresLegItem.GunState.IDLE, 0, leftRounds,
                ammo, 0.0F);
        DualMaresLegItem.setTestState(gun, 1, DualMaresLegItem.GunState.IDLE, 0, rightRounds,
                ammo, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(GameTestHelper helper, Player player, int ticks) {
        DualMaresLegItem gun = (DualMaresLegItem) player.getMainHandItem().getItem();
        for (int i = 0; i < ticks; i++) {
            gun.inventoryTick(player.getMainHandItem(), helper.getLevel(), player,
                    player.getInventory().selected, true);
        }
    }

    private static List<BulletEntity> bullets(GameTestHelper helper, Player owner) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntitiesOfClass(BulletEntity.class,
                new AABB(origin).inflate(64.0D), bullet -> bullet.getOwner() == owner);
    }
}
