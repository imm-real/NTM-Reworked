package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.AutoShotgunItem;
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

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AutoShotgunGameTests {
    private AutoShotgunGameTests() { }

    @GameTest(template = "empty")
    public static void autoShotgunUsesSourceConfigurationAndEmptyMagazine(GameTestHelper helper) {
        AutoShotgunItem item = ModItems.GUN_AUTOSHOTGUN.get();
        ItemStack fresh = new ItemStack(item);

        helper.assertTrue(AutoShotgunItem.DURABILITY == 2_000
                        && AutoShotgunItem.DRAW_TICKS == 10
                        && AutoShotgunItem.INSPECT_TICKS == 33
                        && AutoShotgunItem.FIRE_DELAY == 10
                        && AutoShotgunItem.DRY_DELAY == 10
                        && AutoShotgunItem.RELOAD_TICKS == 44
                        && AutoShotgunItem.JAM_TICKS == 19
                        && AutoShotgunItem.CAPACITY == 20,
                "Auto Shotgun durability and action timing must match XFactory12ga");
        helper.assertTrue(item.baseDamage() == 48.0F && item.gunAutomatic()
                        && item.gunCrosshair() == SednaCrosshair.L_CIRCLE
                        && item.gunHideCrosshairWhenAimed(),
                "Auto Shotgun must be full-auto, 48 base damage, large-circle reticle, hide-when-aimed");
        helper.assertTrue(item.recoilVertical() == 1.5F
                        && item.recoilVerticalSigma() == 1.5F
                        && item.recoilHorizontalSigma() == 0.5F,
                "Recoil must be the source setupRecoil(gaussian*1.5 + 1.5, gaussian*0.5)");
        helper.assertTrue(ModItems.GUN_MARESLEG.get().recoilVerticalSigma() == 0.0F,
                "The new vertical-gaussian extension must leave existing guns at the zero default");
        helper.assertTrue(AutoShotgunItem.rounds(fresh) == 0
                        && AutoShotgunItem.loadedAmmo(fresh) == Shotgun12GaugeAmmoType.BUCKSHOT,
                "A fresh Auto Shotgun must be empty with the source default G12 buckshot identity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullReloadIsAtomicAndLocksLoadedType(GameTestHelper helper) {
        Player empty = armedPlayer(helper, Shotgun12GaugeAmmoType.BUCKSHOT, 0, 0.0F, 2);
        // Buckshot occupies the earlier slot, so the empty magazine must select it as the first accepted load.
        empty.getInventory().add(Shotgun12GaugeAmmoType.BUCKSHOT.createStack(ModItems.AMMO_STANDARD.get(), 30));
        empty.getInventory().add(Shotgun12GaugeAmmoType.SLUG.createStack(ModItems.AMMO_STANDARD.get(), 2));
        ItemStack emptyGun = empty.getMainHandItem();

        SednaGunItem.handleInput(empty, GunInput.RELOAD);
        helper.assertTrue(AutoShotgunItem.state(emptyGun) == AutoShotgunItem.GunState.RELOADING
                        && AutoShotgunItem.timer(emptyGun) == 44
                        && AutoShotgunItem.amountBeforeReload(emptyGun) == 0
                        && AutoShotgunItem.animation(emptyGun) == AutoShotgunItem.GunAnimation.RELOAD,
                "Reload must begin with the source forty-four-tick RELOAD phase");
        tickHeld(helper, empty, 43);
        helper.assertTrue(AutoShotgunItem.rounds(emptyGun) == 0
                        && AutoShotgunItem.animation(emptyGun) == AutoShotgunItem.GunAnimation.RELOAD,
                "No round may transfer before the atomic reload completes, and it never plays RELOAD_CYCLE");
        tickHeld(helper, empty, 1);
        helper.assertTrue(AutoShotgunItem.rounds(emptyGun) == 20
                        && AutoShotgunItem.loadedAmmo(emptyGun) == Shotgun12GaugeAmmoType.BUCKSHOT
                        && countAmmo(empty, Shotgun12GaugeAmmoType.BUCKSHOT) == 10
                        && countAmmo(empty, Shotgun12GaugeAmmoType.SLUG) == 2,
                "The atomic reload must fill to twenty of the first accepted load without touching other calibers");

        Player partial = armedPlayer(helper, Shotgun12GaugeAmmoType.FLECHETTE, 5, 0.0F, 5);
        partial.getInventory().add(Shotgun12GaugeAmmoType.SLUG.createStack(ModItems.AMMO_STANDARD.get(), 8));
        partial.getInventory().add(Shotgun12GaugeAmmoType.FLECHETTE.createStack(ModItems.AMMO_STANDARD.get(), 30));
        ItemStack partialGun = partial.getMainHandItem();
        SednaGunItem.handleInput(partial, GunInput.RELOAD);
        tickHeld(helper, partial, 44);
        helper.assertTrue(AutoShotgunItem.rounds(partialGun) == 20
                        && AutoShotgunItem.loadedAmmo(partialGun) == Shotgun12GaugeAmmoType.FLECHETTE
                        && countAmmo(partial, Shotgun12GaugeAmmoType.SLUG) == 8
                        && countAmmo(partial, Shotgun12GaugeAmmoType.FLECHETTE) == 15,
                "A partial magazine must top up to twenty from its already-loaded identity only");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void heldTriggerFiresEveryTenTicksUntilEmpty(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.SLUG, 20, 0.0F, 2);
        ItemStack stack = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(AutoShotgunItem.rounds(stack) == 19
                        && AutoShotgunItem.state(stack) == AutoShotgunItem.GunState.COOLDOWN
                        && AutoShotgunItem.timer(stack) == 10
                        && AutoShotgunItem.primaryHeld(stack)
                        && bullets(helper, player).size() == 1,
                "The first shot fires on press and starts the ten-tick automatic cycle");
        tickHeld(helper, player, 9);
        helper.assertTrue(AutoShotgunItem.rounds(stack) == 19 && bullets(helper, player).size() == 1,
                "No refire may occur before the ten-tick delay elapses");
        tickHeld(helper, player, 1);
        helper.assertTrue(AutoShotgunItem.rounds(stack) == 18 && bullets(helper, player).size() == 2,
                "Holding the trigger refires exactly once every ten ticks");

        // 190 ticks after the press fires shots 2..20; rounds/wear are the reliable per-shot invariants
        // (one round and one wear per fire), so twenty shots leave an empty magazine at exactly 20 wear.
        tickHeld(helper, player, 180);
        helper.assertTrue(AutoShotgunItem.rounds(stack) == 0
                        && AutoShotgunItem.wear(stack) == 20.0F,
                "Exactly twenty slugs fire at five rounds per second, consuming the mag and adding twenty wear");
        // With the mag empty the held trigger drops into the dry-fire loop: no further ammo or wear.
        tickHeld(helper, player, 30);
        helper.assertTrue(AutoShotgunItem.rounds(stack) == 0
                        && AutoShotgunItem.wear(stack) == 20.0F
                        && AutoShotgunItem.state(stack) == AutoShotgunItem.GunState.COOLDOWN,
                "An emptied automatic must stop firing and only dry-cycle while still held");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyHeldTriggerLoopsDryFireAndReleaseGoesIdle(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.BUCKSHOT, 0, 0.0F, 2);
        ItemStack stack = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(AutoShotgunItem.animation(stack) == AutoShotgunItem.GunAnimation.CYCLE_DRY
                        && AutoShotgunItem.state(stack) == AutoShotgunItem.GunState.COOLDOWN
                        && AutoShotgunItem.timer(stack) == 10,
                "An empty click enters the COOLDOWN dry-fire state (not DRAWING), unlike the Uzi");
        tickHeld(helper, player, 10);
        helper.assertTrue(AutoShotgunItem.animation(stack) == AutoShotgunItem.GunAnimation.CYCLE_DRY
                        && AutoShotgunItem.state(stack) == AutoShotgunItem.GunState.COOLDOWN
                        && bullets(helper, player).isEmpty(),
                "Holding an empty trigger replays CYCLE_DRY every ten ticks without spawning bullets");
        tickHeld(helper, player, 20);
        helper.assertTrue(AutoShotgunItem.state(stack) == AutoShotgunItem.GunState.COOLDOWN
                        && bullets(helper, player).isEmpty(),
                "The dry-fire loop must not go idle or auto-reload while the trigger is held");

        SednaGunItem.handleInput(player, GunInput.PRIMARY_RELEASE);
        tickHeld(helper, player, 10);
        helper.assertTrue(AutoShotgunItem.state(stack) == AutoShotgunItem.GunState.IDLE
                        && AutoShotgunItem.rounds(stack) == 0
                        && !AutoShotgunItem.primaryHeld(stack),
                "Releasing the trigger breaks the dry loop back to IDLE with no auto-reload (reloadOnEmpty false)");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pelletCountsAndAmmoDamageMatchSource(GameTestHelper helper) {
        Player buck = armedPlayer(helper, Shotgun12GaugeAmmoType.BUCKSHOT, 4, 0.0F, 2);
        SednaGunItem.handleInput(buck, GunInput.PRIMARY);
        List<BulletEntity> buckPellets = bullets(helper, buck);
        helper.assertTrue(buckPellets.size() == 8
                        && buckPellets.stream().allMatch(pellet -> pellet.damage() == 6.0F),
                "Buckshot must spawn eight pellets at 48 x 1/8 = 6 damage each");

        Player slug = armedPlayer(helper, Shotgun12GaugeAmmoType.SLUG, 4, 0.0F, 6);
        SednaGunItem.handleInput(slug, GunInput.PRIMARY);
        List<BulletEntity> slugShots = bullets(helper, slug);
        helper.assertTrue(slugShots.size() == 1 && slugShots.get(0).damage() == 48.0F,
                "A slug must spawn one bullet at the full 48 base damage");

        helper.assertTrue(totalDamage(Shotgun12GaugeAmmoType.BUCKSHOT) == 48.0F
                        && totalDamage(Shotgun12GaugeAmmoType.MAGNUM) == 96.0F
                        && totalDamage(Shotgun12GaugeAmmoType.EXPLOSIVE) == 120.0F,
                "damageWithAmmo must use the ported multipliers: buckshot 48, magnum 96, explosive 120");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void firingDuringReloadCannotCancelTheAtomicReload(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.BUCKSHOT, 5, 0.0F, 2);
        player.getInventory().add(Shotgun12GaugeAmmoType.BUCKSHOT.createStack(ModItems.AMMO_STANDARD.get(), 20));
        ItemStack gun = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        tickHeld(helper, player, 20);
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(AutoShotgunItem.state(gun) == AutoShotgunItem.GunState.RELOADING
                        && AutoShotgunItem.rounds(gun) == 5
                        && bullets(helper, player).isEmpty(),
                "Pressing fire mid-reload must not fire or interrupt the ongoing reload");
        tickHeld(helper, player, 24);
        helper.assertTrue(AutoShotgunItem.rounds(gun) == 20
                        && countAmmo(player, Shotgun12GaugeAmmoType.BUCKSHOT) == 5,
                "The cancel flag is a no-op for a full reload: it still completes to twenty at tick forty-four");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wearGatesJamAndInspectDoesNotLockTheGun(GameTestHelper helper) {
        helper.assertTrue(AutoShotgunItem.jamChance(1_000.0F) == 0.0F
                        && AutoShotgunItem.jamChance(1_900.0F) >= 1.0F,
                "Jam chance must stay zero below sixty-six percent and cap above ninety-one percent wear");

        Player jammer = armedPlayer(helper, Shotgun12GaugeAmmoType.BUCKSHOT, 5, 1_900.0F, 2);
        jammer.getInventory().add(Shotgun12GaugeAmmoType.BUCKSHOT.createStack(ModItems.AMMO_STANDARD.get(), 20));
        ItemStack jamGun = jammer.getMainHandItem();
        SednaGunItem.handleInput(jammer, GunInput.RELOAD);
        tickHeld(helper, jammer, 44);
        helper.assertTrue(AutoShotgunItem.state(jamGun) == AutoShotgunItem.GunState.JAMMED
                        && AutoShotgunItem.timer(jamGun) == 19,
                "A worn reload must jam for nineteen ticks");
        tickHeld(helper, jammer, 19);
        helper.assertTrue(AutoShotgunItem.state(jamGun) == AutoShotgunItem.GunState.IDLE,
                "The jam must clear back to IDLE after nineteen ticks");

        Player full = armedPlayer(helper, Shotgun12GaugeAmmoType.BUCKSHOT, 20, 0.0F, 6);
        ItemStack fullGun = full.getMainHandItem();
        SednaGunItem.handleInput(full, GunInput.RELOAD);
        helper.assertTrue(AutoShotgunItem.animation(fullGun) == AutoShotgunItem.GunAnimation.INSPECT
                        && AutoShotgunItem.state(fullGun) == AutoShotgunItem.GunState.IDLE,
                "Reloading a full magazine plays a cancelable INSPECT without locking the state");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unequipResetsToDrawUnlessJammed(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.BUCKSHOT, 20, 0.0F, 2);
        AutoShotgunItem gun = (AutoShotgunItem) player.getMainHandItem().getItem();
        ItemStack stack = player.getMainHandItem();

        gun.inventoryTick(stack, helper.getLevel(), player, player.getInventory().selected, false);
        helper.assertTrue(AutoShotgunItem.state(stack) == AutoShotgunItem.GunState.DRAWING
                        && AutoShotgunItem.timer(stack) == 10,
                "Switching away must reset to a ten-tick draw");

        AutoShotgunItem.setTestState(stack, AutoShotgunItem.GunState.JAMMED, 5,
                20, Shotgun12GaugeAmmoType.BUCKSHOT, 0.0F, false);
        gun.inventoryTick(stack, helper.getLevel(), player, player.getInventory().selected, false);
        helper.assertTrue(AutoShotgunItem.state(stack) == AutoShotgunItem.GunState.JAMMED,
                "An in-progress jam must survive switching away");
        helper.succeed();
    }

    private static float totalDamage(Shotgun12GaugeAmmoType ammo) {
        return AutoShotgunItem.BASE_DAMAGE * ammo.damageMultiplier() * ammo.projectiles();
    }

    private static Player armedPlayer(GameTestHelper helper, Shotgun12GaugeAmmoType ammo,
                                      int rounds, float wear, int x) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_AUTOSHOTGUN.get());
        AutoShotgunItem.setTestState(gun, AutoShotgunItem.GunState.IDLE, 0, rounds, ammo, wear, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(x, 2, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(GameTestHelper helper, Player player, int ticks) {
        AutoShotgunItem gun = (AutoShotgunItem) player.getMainHandItem().getItem();
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

    private static int countAmmo(Player player, SednaAmmoType type) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.AMMO_STANDARD.get()) && StandardAmmoTypes.fromStack(stack) == type) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
