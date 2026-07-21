package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.FollyBeamEntity;
import com.hbm.ntm.entity.FollyNukeProjectileEntity;
import com.hbm.ntm.item.FollyItem;
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

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FollyGameTests {
    private FollyGameTests() { }

    @GameTest(template = "empty")
    public static void secretRoundsKeepTheirSourceOrdinals(GameTestHelper helper) {
        for (FollyAmmoType type : FollyAmmoType.values()) {
            ItemStack stack = type.createStack(ModItems.AMMO_SECRET.get(), 4);
            helper.assertTrue(SecretAmmoTypes.fromStack(stack) == type,
                    type + " must survive the shared secret ammunition container");
        }
        helper.assertTrue(FollyAmmoType.SILVER_BULLET.legacyMetadata() == 0
                        && FollyAmmoType.NUCLEAR_SILVER_BULLET.legacyMetadata() == 1
                        && FollyAmmoType.SILVER_BULLET.spectral()
                        && FollyAmmoType.NUCLEAR_SILVER_BULLET.projectileSpeed() == 4.0D
                        && FollyAmmoType.NUCLEAR_SILVER_BULLET.projectileLifetime() == 600,
                "Folly rounds must keep source secret ordinals and projectile profiles");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void receiverKeepsTheFiveSecondSafetyCatch(GameTestHelper helper) {
        FollyItem gun = ModItems.GUN_FOLLY.get();
        ItemStack stack = new ItemStack(gun);
        helper.assertTrue(gun.gunDurability() == 0.0F && !gun.gunShowDurability()
                        && gun.gunCapacity() == 1 && gun.gunCrosshair() == SednaCrosshair.NONE
                        && gun.gunAimFovMultiplier() == 0.67F
                        && gun.recoilVertical() == 25.0F && gun.recoilHorizontalSigma() == 1.5F,
                "Folly must keep its infinite condition, hidden crosshair, zoom and recoil");
        helper.assertTrue(FollyItem.DRAW_TICKS == 40 && FollyItem.FIRE_DELAY == 26
                        && FollyItem.RELOAD_TICKS == 160 && FollyItem.SPINUP_TICKS == 100
                        && FollyItem.BASE_DAMAGE == 1_000.0F
                        && FollyItem.rounds(stack) == 0
                        && FollyItem.loadedAmmo(stack) == FollyAmmoType.SILVER_BULLET,
                "Folly must spawn empty with its source timings and Silver Bullet selected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void primaryRequiresAimAndOneHundredSpinupTicks(GameTestHelper helper) {
        Player player = armed(helper, FollyAmmoType.SILVER_BULLET, false, 100);
        ItemStack gun = player.getMainHandItem();
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(FollyItem.rounds(gun) == 1 && beams(helper) == 0,
                "Folly must refuse a fully spun shot while it is not aimed");

        FollyItem.setTestState(gun, FollyItem.GunState.IDLE, 0, 1,
                FollyAmmoType.SILVER_BULLET, true, FollyItem.GunAnimation.SPINUP, 99);
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(FollyItem.rounds(gun) == 1 && beams(helper) == 0,
                "Folly must refuse the shot on spinup tick ninety-nine");

        FollyItem.setTestState(gun, FollyItem.GunState.IDLE, 0, 1,
                FollyAmmoType.SILVER_BULLET, true, FollyItem.GunAnimation.SPINUP, 100);
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(FollyItem.rounds(gun) == 0 && beams(helper) == 1
                        && FollyItem.state(gun) == FollyItem.GunState.COOLDOWN,
                "Aimed Folly must fire exactly after one hundred spinup ticks");
        helper.getLevel().getEntitiesOfClass(FollyBeamEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(64.0D)).forEach(FollyBeamEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nuclearRoundReloadsAndLaunchesItsOwnProjectile(GameTestHelper helper) {
        Player player = armed(helper, FollyAmmoType.NUCLEAR_SILVER_BULLET, true, 100);
        ItemStack gun = player.getMainHandItem();
        FollyItem.setTestState(gun, FollyItem.GunState.IDLE, 0, 0,
                FollyAmmoType.SILVER_BULLET, false, FollyItem.GunAnimation.CYCLE, 0);
        player.getInventory().add(FollyAmmoType.NUCLEAR_SILVER_BULLET.createStack(
                ModItems.AMMO_SECRET.get(), 1));
        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(FollyItem.timer(gun) == FollyItem.RELOAD_TICKS,
                "Folly must begin the source eight-second single-round reload");
        tickHeld(player, FollyItem.RELOAD_TICKS);
        helper.assertTrue(FollyItem.rounds(gun) == 1
                        && FollyItem.loadedAmmo(gun) == FollyAmmoType.NUCLEAR_SILVER_BULLET,
                "Reload must preserve the nuclear Silver Bullet identity");

        FollyItem.setTestState(gun, FollyItem.GunState.IDLE, 0, 1,
                FollyAmmoType.NUCLEAR_SILVER_BULLET, true, FollyItem.GunAnimation.SPINUP, 100);
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(64.0D);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(FollyNukeProjectileEntity.class, area).size() == 1,
                "Nuclear Silver Bullet must launch the chunk-loading MIRV projectile");
        helper.getLevel().getEntitiesOfClass(FollyNukeProjectileEntity.class, area)
                .forEach(FollyNukeProjectileEntity::discard);
        helper.succeed();
    }

    private static Player armed(GameTestHelper helper, FollyAmmoType ammo,
                                boolean aiming, int animationTimer) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_FOLLY.get());
        FollyItem.setTestState(gun, FollyItem.GunState.IDLE, 0, 1, ammo,
                aiming, FollyItem.GunAnimation.SPINUP, animationTimer);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(Player player, int ticks) {
        FollyItem gun = (FollyItem) player.getMainHandItem().getItem();
        for (int i = 0; i < ticks; i++) {
            gun.inventoryTick(player.getMainHandItem(), player.level(), player,
                    player.getInventory().selected, true);
        }
    }

    private static int beams(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(FollyBeamEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(64.0D)).size();
    }
}
