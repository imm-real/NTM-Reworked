package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.SednaGunItem;
import com.hbm.ntm.item.SpasItem;
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
public final class SpasGameTests {
    private SpasGameTests() { }

    @GameTest(template = "empty")
    public static void spasUsesSourceConfigurationAndDefaultIdentity(GameTestHelper helper) {
        SpasItem item = ModItems.GUN_SPAS12.get();
        ItemStack fresh = new ItemStack(item);
        helper.assertTrue(SpasItem.DURABILITY == 600
                        && SpasItem.DRAW_TICKS == 20
                        && SpasItem.INSPECT_TICKS == 39
                        && SpasItem.FIRE_DELAY == 20
                        && SpasItem.JAM_TICKS == 36
                        && SpasItem.CAPACITY == 8
                        && SpasItem.ROUNDS_PER_CYCLE == 1,
                "SPAS durability, action timing, eight-shell tube, and single roundsPerCycle must match XFactory12ga");
        helper.assertTrue(SpasItem.RELOAD_COCK_PRE == 5
                        && SpasItem.RELOAD_BEGIN_TICKS == 10
                        && SpasItem.RELOAD_CYCLE_TICKS == 10
                        && SpasItem.RELOAD_END_TICKS == 10
                        && SpasItem.RELOAD_COCK_POST == 0,
                "reload(5,10,10,10,0) maps to cockPre 5, begin 10, cycle 10, end 10, cockPost 0");
        helper.assertTrue(item.baseDamage() == 32.0F && item.gunCapacity() == 8
                        && SpasItem.DEFAULT_HIP_SPREAD == 0.0F
                        && item.recoilVertical() == 10.0F
                        && item.recoilHorizontalSigma() == 1.5F
                        && item.gunCrosshair() == SednaCrosshair.L_CIRCLE,
                "SPAS damage, zero hipfire penalty, 10/1.5 recoil, and large-circle reticle must match the source");
        // setDefaultAmmo(G12, 16) sets only the BUCKSHOT identity: the fresh magazine stays empty.
        helper.assertTrue(SpasItem.rounds(fresh) == 0
                        && SpasItem.loadedAmmo(fresh) == Shotgun12GaugeAmmoType.BUCKSHOT,
                "A fresh gun must be empty with the source default buckshot identity (no preloaded shells)");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void primaryFiresExactlyOneBuckshotShell(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.BUCKSHOT, 8, 0.0F);
        ItemStack gun = player.getMainHandItem();
        double playerX = player.getX();

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        List<BulletEntity> pellets = bullets(helper, player);
        helper.assertTrue(pellets.size() == 8,
                "One primary press must fire exactly one buckshot shell (8 pellets)");
        helper.assertTrue(pellets.stream().allMatch(pellet -> pellet.damage() == 4.0F),
                "Every pellet must deal 4.0 damage (32 base x 0.125 buckshot multiplier)");
        helper.assertTrue(pellets.stream().allMatch(pellet -> Math.abs(pellet.getX() - (playerX - 0.1875D)) < 0.0001D),
                "Every hip-fired pellet must originate at the exact side offset (-0.1875)");
        helper.assertTrue(SpasItem.rounds(gun) == 7
                        && SpasItem.wear(gun) == 1.0F
                        && SpasItem.state(gun) == SpasItem.GunState.COOLDOWN
                        && SpasItem.timer(gun) == 20,
                "A single shot must consume one shell, add one wear step, and enter the 20-tick cooldown");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void secondaryDoubleBlastFiresTwoShells(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.BUCKSHOT, 8, 0.0F);
        ItemStack gun = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.SECONDARY);
        helper.assertTrue(bullets(helper, player).size() == 16,
                "A secondary double-blast must fire two buckshot shells in one press (2 x 8 = 16 pellets)");
        helper.assertTrue(SpasItem.rounds(gun) == 6
                        && SpasItem.wear(gun) == 2.0F
                        && SpasItem.state(gun) == SpasItem.GunState.COOLDOWN
                        && SpasItem.timer(gun) == 20,
                "The double-blast must consume two shells, add two wear steps, and enter the 20-tick cooldown");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void secondaryWithOneShellFiresSingleVolley(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.BUCKSHOT, 1, 0.0F);
        ItemStack gun = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.SECONDARY);
        helper.assertTrue(bullets(helper, player).size() == 8,
                "With one shell loaded the double-blast must fire exactly one volley (8 pellets), not crash");
        helper.assertTrue(SpasItem.rounds(gun) == 0
                        && SpasItem.wear(gun) == 1.0F
                        && SpasItem.state(gun) == SpasItem.GunState.COOLDOWN
                        && SpasItem.timer(gun) == 20,
                "A one-shell double-blast must consume exactly one shell and enter cooldown");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyPrimaryAndSecondaryDryFire(GameTestHelper helper) {
        Player primary = armedPlayer(helper, Shotgun12GaugeAmmoType.BUCKSHOT, 0, 0.0F);
        ItemStack primaryGun = primary.getMainHandItem();
        SednaGunItem.handleInput(primary, GunInput.PRIMARY);
        helper.assertTrue(bullets(helper, primary).isEmpty()
                        && SpasItem.state(primaryGun) == SpasItem.GunState.DRAWING
                        && SpasItem.timer(primaryGun) == 20
                        && SpasItem.animation(primaryGun) == SpasItem.GunAnimation.CYCLE_DRY,
                "An empty primary press must dry-fire: no bullets, DRAWING for 20 ticks, CYCLE_DRY animation");

        Player secondary = armedPlayer(helper, Shotgun12GaugeAmmoType.BUCKSHOT, 0, 0.0F);
        ItemStack secondaryGun = secondary.getMainHandItem();
        SednaGunItem.handleInput(secondary, GunInput.SECONDARY);
        helper.assertTrue(bullets(helper, secondary).isEmpty()
                        && SpasItem.state(secondaryGun) == SpasItem.GunState.DRAWING
                        && SpasItem.timer(secondaryGun) == 20
                        && SpasItem.animation(secondaryGun) == SpasItem.GunAnimation.CYCLE_DRY,
                "An empty secondary press must also dry-fire without firing shells");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spasCannotAim(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.BUCKSHOT, 8, 0.0F);
        ItemStack gun = player.getMainHandItem();
        double playerX = player.getX();

        // TOGGLE_AIM is routed but the SPAS omits the handler (.pt(null)); aiming must stay false.
        SednaGunItem.handleInput(player, GunInput.TOGGLE_AIM);
        helper.assertTrue(!SpasItem.aiming(gun),
                "The SPAS must never enter the aiming state");

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(bullets(helper, player).stream()
                        .allMatch(pellet -> Math.abs(pellet.getX() - (playerX - 0.1875D)) < 0.0001D),
                "With no aim the projectile must always use the hip side offset");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyTubeReloadsOneShellPerCycleAndLocksType(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.BUCKSHOT, 0, 0.0F);
        ItemStack gun = player.getMainHandItem();
        player.getInventory().add(Shotgun12GaugeAmmoType.SLUG.createStack(ModItems.AMMO_STANDARD.get(), 16));

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(SpasItem.state(gun) == SpasItem.GunState.RELOADING
                        && SpasItem.timer(gun) == 15
                        && SpasItem.amountBeforeReload(gun) == 0
                        && SpasItem.loadedAmmo(gun) == Shotgun12GaugeAmmoType.SLUG,
                "An empty reload must begin at 15 ticks (begin 10 + cockPre 5) with reloadChangeType locking SLUG");

        tickHeld(helper, player, 15);
        helper.assertTrue(SpasItem.rounds(gun) == 1
                        && SpasItem.timer(gun) == 10
                        && SpasItem.loadedAmmo(gun) == Shotgun12GaugeAmmoType.SLUG
                        && SpasItem.animation(gun) == SpasItem.GunAnimation.RELOAD_CYCLE,
                "The first transfer must load one SLUG and enter a 10-tick cycle");

        for (int shell = 2; shell <= 8; shell++) {
            tickHeld(helper, player, 10);
            helper.assertTrue(SpasItem.rounds(gun) == shell,
                    "Each 10-tick cycle must load exactly one more shell");
        }
        helper.assertTrue(SpasItem.state(gun) == SpasItem.GunState.DRAWING
                        && SpasItem.timer(gun) == 10
                        && SpasItem.animation(gun) == SpasItem.GunAnimation.RELOAD_END,
                "Loading the eighth shell must fill the tube and enter the 10-tick close");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void partialReloadBeginsWithoutCockAndPrimaryCancels(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.SLUG, 3, 0.0F);
        ItemStack gun = player.getMainHandItem();
        player.getInventory().add(Shotgun12GaugeAmmoType.SLUG.createStack(ModItems.AMMO_STANDARD.get(), 16));

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(SpasItem.state(gun) == SpasItem.GunState.RELOADING
                        && SpasItem.timer(gun) == 10
                        && SpasItem.amountBeforeReload(gun) == 3,
                "A partial reload must begin at 10 ticks (no empty cock) and remember the pre-reload count");

        tickHeld(helper, player, 10);
        helper.assertTrue(SpasItem.rounds(gun) == 4 && SpasItem.timer(gun) == 10,
                "The first cycle of a partial reload must load the fourth shell");

        // A primary press during RELOADING sets cancel; the in-progress shell still finishes.
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        tickHeld(helper, player, 10);
        helper.assertTrue(SpasItem.rounds(gun) == 5
                        && SpasItem.state(gun) == SpasItem.GunState.DRAWING
                        && SpasItem.animation(gun) == SpasItem.GunAnimation.RELOAD_END,
                "Canceling must finish the current shell then transition to the close phase");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyReloadPicksFirstInventoryOrderType(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.BUCKSHOT, 0, 0.0F);
        ItemStack gun = player.getMainHandItem();
        // FLECHETTE occupies the earlier inventory slot; EXPLOSIVE is added afterward.
        player.getInventory().add(Shotgun12GaugeAmmoType.FLECHETTE.createStack(ModItems.AMMO_STANDARD.get(), 8));
        player.getInventory().add(Shotgun12GaugeAmmoType.EXPLOSIVE.createStack(ModItems.AMMO_STANDARD.get(), 8));

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(SpasItem.loadedAmmo(gun) == Shotgun12GaugeAmmoType.FLECHETTE,
                "The empty tube must lock to the first inventory-order accepted load (FLECHETTE)");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void highWearReloadReachesJammed(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.SLUG, 0, 600.0F);
        ItemStack gun = player.getMainHandItem();
        // Exactly one shell available: after it loads no further reload is possible, so the jam roll runs.
        player.getInventory().add(Shotgun12GaugeAmmoType.SLUG.createStack(ModItems.AMMO_STANDARD.get(), 1));

        helper.assertTrue(SpasItem.jamChance(600.0F) == 1.0F,
                "At full wear the jam chance must saturate to 1.0");
        SednaGunItem.handleInput(player, GunInput.RELOAD);
        tickHeld(helper, player, 15);
        helper.assertTrue(SpasItem.rounds(gun) == 1
                        && SpasItem.state(gun) == SpasItem.GunState.JAMMED
                        && SpasItem.timer(gun) == 36,
                "A full-wear reload with no more ammo must jam for 36 ticks after loading its last shell");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void damageWithAmmoMathMatchesSource(GameTestHelper helper) {
        helper.assertTrue(Shotgun12GaugeAmmoType.values().length == 9,
                "The SPAS magazine family is exactly the nine ordinary 12-gauge loads");
        helper.assertTrue(pelletTotal(Shotgun12GaugeAmmoType.SLUG) == 32.0F,
                "Slug damage-with-ammo must be 32 x 1.0 x 1 = 32");
        helper.assertTrue(pelletTotal(Shotgun12GaugeAmmoType.BUCKSHOT) == 32.0F,
                "Buckshot damage-with-ammo must be 32 x 0.125 x 8 = 32");
        helper.assertTrue(pelletTotal(Shotgun12GaugeAmmoType.EXPLOSIVE) == 80.0F,
                "Explosive damage-with-ammo must be 32 x 2.5 x 1 = 80");
        helper.succeed();
    }

    private static float pelletTotal(Shotgun12GaugeAmmoType ammo) {
        return SpasItem.BASE_DAMAGE * ammo.damageMultiplier() * ammo.projectiles();
    }

    private static Player armedPlayer(GameTestHelper helper, Shotgun12GaugeAmmoType ammo,
                                      int rounds, float wear) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_SPAS12.get());
        SpasItem.setTestState(gun, SpasItem.GunState.IDLE, 0, rounds, ammo, wear);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(GameTestHelper helper, Player player, int ticks) {
        SpasItem gun = (SpasItem) player.getMainHandItem().getItem();
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
