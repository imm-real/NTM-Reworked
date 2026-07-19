package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.SednaGunItem;
import com.hbm.ntm.item.SexyItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
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
public final class SexyGameTests {
    private SexyGameTests() { }

    @GameTest(template = "empty")
    public static void metadataMatchesSourceAndRecoilIsGaussianBothAxes(GameTestHelper helper) {
        SexyItem item = ModItems.GUN_AUTOSHOTGUN_SEXY.get();
        ItemStack fresh = new ItemStack(item);

        helper.assertTrue(SexyItem.DURABILITY == 5_000
                        && SexyItem.DRAW_TICKS == 20
                        && SexyItem.INSPECT_TICKS == 65
                        && SexyItem.FIRE_DELAY == 4
                        && SexyItem.DRY_DELAY == 4
                        && SexyItem.RELOAD_TICKS == 110
                        && SexyItem.RELOAD_END_TICKS == 0
                        && SexyItem.JAM_TICKS == 19
                        && SexyItem.CAPACITY == 100,
                "Sexy durability and action timing must match XFactory12ga line 378-387");
        helper.assertTrue(item.baseDamage() == 64.0F && item.gunAutomatic()
                        && item.gunCrosshair() == SednaCrosshair.L_CIRCLE
                        && !item.gunHideCrosshairWhenAimed(),
                "Sexy must be full-auto, 64 base damage, large-circle reticle, crosshair visible while aimed");
        // LAMBDA_RECOIL_SEXY: setupRecoil(gaussian*0.5, gaussian*0.5) -> zero fixed vertical, gaussian both axes.
        helper.assertTrue(item.recoilVertical() == 0.0F
                        && item.recoilVerticalSigma() == 0.5F
                        && item.recoilHorizontalSigma() == 0.5F,
                "Recoil must be a zero-mean gaussian on BOTH axes (sigma 0.5), so the view can kick down");
        helper.assertTrue(SexyItem.rounds(fresh) == 0
                        && SexyItem.loadedAmmo(fresh) == Shotgun12GaugeAmmoType.MAGNUM,
                "A fresh Sexy must be empty with the setDefaultAmmo MAGNUM identity (not buckshot, not equestrian)");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void singleFireFromIdleSpawnsPelletsAndCyclesInFourTicks(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.MAGNUM, 10, 0.0F, 2);
        ItemStack stack = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(SexyItem.rounds(stack) == 9
                        && SexyItem.state(stack) == SexyItem.GunState.COOLDOWN
                        && SexyItem.timer(stack) == 4
                        && SexyItem.primaryHeld(stack)
                        && SexyItem.animation(stack) == SexyItem.GunAnimation.CYCLE
                        && SexyItem.wear(stack) == 1.0F
                        && bullets(helper, player).size() == 4,
                "The first magnum shot fires four pellets, adds one wear, and starts the four-tick cycle");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void heldTriggerFiresEveryFourTicksUntilEmpty(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.SLUG, 20, 0.0F, 2);
        ItemStack stack = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(SexyItem.rounds(stack) == 19 && bullets(helper, player).size() == 1,
                "The first slug fires on press");
        tickHeld(helper, player, 3);
        helper.assertTrue(SexyItem.rounds(stack) == 19 && bullets(helper, player).size() == 1,
                "No refire may occur before the four-tick delay elapses");
        tickHeld(helper, player, 1);
        helper.assertTrue(SexyItem.rounds(stack) == 18 && bullets(helper, player).size() == 2,
                "Holding the trigger refires exactly once every four ticks");
        // 18 more shots at four ticks each empties the mag; rounds/wear are the reliable per-shot invariants.
        tickHeld(helper, player, 72);
        helper.assertTrue(SexyItem.rounds(stack) == 0 && SexyItem.wear(stack) == 20.0F,
                "Twenty slugs fire, consuming the magazine and adding twenty wear");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dryFiresExactlyOnceThenIdlesWithoutLooping(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.MAGNUM, 1, 0.0F, 2);
        ItemStack stack = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(SexyItem.rounds(stack) == 0 && bullets(helper, player).size() == 4,
                "The single loaded shot fires, leaving the magazine empty");
        // Mag empties during held auto: at cooldown expiry it must play ONE CYCLE_DRY then go to DRAWING.
        tickHeld(helper, player, 4);
        helper.assertTrue(SexyItem.animation(stack) == SexyItem.GunAnimation.CYCLE_DRY
                        && SexyItem.state(stack) == SexyItem.GunState.DRAWING
                        && SexyItem.timer(stack) == 4,
                "dryfireAfterAuto without autoAfterDry: empty auto plays CYCLE_DRY then DRAWING (not a loop)");
        tickHeld(helper, player, 4);
        helper.assertTrue(SexyItem.state(stack) == SexyItem.GunState.IDLE,
                "After the dry click the DRAWING clears back to IDLE");
        // Trigger still held, but IDLE never auto-refires: it must NOT machine-gun the dry click.
        tickHeld(helper, player, 30);
        helper.assertTrue(SexyItem.state(stack) == SexyItem.GunState.IDLE
                        && SexyItem.rounds(stack) == 0
                        && bullets(helper, player).size() == 4,
                "The empty held trigger dry-fires exactly once, unlike the base gun_autoshotgun dry loop");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyClickFromIdleDrawsInsteadOfCooldownLoop(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.MAGNUM, 0, 0.0F, 2);
        ItemStack stack = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(SexyItem.animation(stack) == SexyItem.GunAnimation.CYCLE_DRY
                        && SexyItem.state(stack) == SexyItem.GunState.DRAWING
                        && SexyItem.timer(stack) == 4
                        && bullets(helper, player).isEmpty(),
                "An empty IDLE click plays CYCLE_DRY and enters DRAWING (refireAfterDry false), not COOLDOWN");
        tickHeld(helper, player, 4);
        helper.assertTrue(SexyItem.state(stack) == SexyItem.GunState.IDLE,
                "The dry DRAWING clears to IDLE without a loop");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reloadIsAtomicAndFillsMidAnimationAtTickFiftyFive(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.MAGNUM, 0, 0.0F, 2);
        player.getInventory().add(Shotgun12GaugeAmmoType.BUCKSHOT.createStack(ModItems.AMMO_STANDARD.get(), 60));
        ItemStack stack = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(SexyItem.state(stack) == SexyItem.GunState.RELOADING
                        && SexyItem.timer(stack) == 110
                        && SexyItem.amountBeforeReload(stack) == 0
                        && SexyItem.animation(stack) == SexyItem.GunAnimation.RELOAD,
                "Reload begins with the source 110-tick RELOAD phase");
        // The orchestra sees animTimer 0..54 over the first 55 held ticks: no round transfers yet.
        tickHeld(helper, player, 55);
        helper.assertTrue(SexyItem.rounds(stack) == 0
                        && SexyItem.animation(stack) == SexyItem.GunAnimation.RELOAD,
                "No round may transfer before the orchestra fill at animTimer 55, and it never plays RELOAD_CYCLE");
        tickHeld(helper, player, 1);
        helper.assertTrue(SexyItem.rounds(stack) == 60
                        && SexyItem.loadedAmmo(stack) == Shotgun12GaugeAmmoType.BUCKSHOT
                        && countAmmo(player, Shotgun12GaugeAmmoType.BUCKSHOT) == 0,
                "The mag fills to sixty of the first accepted load exactly at animTimer 55 (mid-reload)");
        // Finish the 110-tick reload: reloadEnd is 0, so tick 110 lands in DRAWING and tick 111 idles.
        tickHeld(helper, player, 55);
        helper.assertTrue(SexyItem.state(stack) == SexyItem.GunState.IDLE
                        && SexyItem.rounds(stack) == 60,
                "The reload completes to IDLE with the mag filled (single atomic pass, no RELOAD_CYCLE)");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reloadCapsAtCapacityAndConsumesMatchingTypeOnly(GameTestHelper helper) {
        // Full-capacity fill from a large stock: caps at 100 and leaves the remainder.
        Player full = armedPlayer(helper, Shotgun12GaugeAmmoType.MAGNUM, 0, 0.0F, 2);
        full.getInventory().add(Shotgun12GaugeAmmoType.BUCKSHOT.createStack(ModItems.AMMO_STANDARD.get(), 150));
        ItemStack fullGun = full.getMainHandItem();
        SednaGunItem.handleInput(full, GunInput.RELOAD);
        tickHeld(helper, full, 56);
        helper.assertTrue(SexyItem.rounds(fullGun) == 100
                        && SexyItem.loadedAmmo(fullGun) == Shotgun12GaugeAmmoType.BUCKSHOT
                        && countAmmo(full, Shotgun12GaugeAmmoType.BUCKSHOT) == 50,
                "An empty reload fills to the 100 capacity and leaves the remaining buckshot");

        // A partially-loaded mag tops up only from its already-loaded identity.
        Player partial = armedPlayer(helper, Shotgun12GaugeAmmoType.FLECHETTE, 5, 0.0F, 6);
        partial.getInventory().add(Shotgun12GaugeAmmoType.SLUG.createStack(ModItems.AMMO_STANDARD.get(), 8));
        partial.getInventory().add(Shotgun12GaugeAmmoType.FLECHETTE.createStack(ModItems.AMMO_STANDARD.get(), 30));
        ItemStack partialGun = partial.getMainHandItem();
        SednaGunItem.handleInput(partial, GunInput.RELOAD);
        tickHeld(helper, partial, 56);
        helper.assertTrue(SexyItem.rounds(partialGun) == 35
                        && SexyItem.loadedAmmo(partialGun) == Shotgun12GaugeAmmoType.FLECHETTE
                        && countAmmo(partial, Shotgun12GaugeAmmoType.SLUG) == 8
                        && countAmmo(partial, Shotgun12GaugeAmmoType.FLECHETTE) == 0,
                "A partial magazine tops up from its loaded flechette only, never touching the slugs");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inspectIsLockedDrinkingAndGrantsTheWhiskeyBuff(GameTestHelper helper) {
        // A full magazine cannot reload, so reload plays the non-cancelable drinking INSPECT.
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.MAGNUM, 100, 0.0F, 2);
        ItemStack stack = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(SexyItem.animation(stack) == SexyItem.GunAnimation.INSPECT
                        && SexyItem.state(stack) == SexyItem.GunState.DRAWING
                        && SexyItem.timer(stack) == 65,
                "inspectCancel(false): reloading a full mag locks the gun in a 65-tick drinking INSPECT");

        // Fire/reload during the drinking animation must be ignored (locked, non-cancelable).
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(SexyItem.state(stack) == SexyItem.GunState.DRAWING
                        && SexyItem.rounds(stack) == 100
                        && bullets(helper, player).isEmpty(),
                "The locked INSPECT cannot be interrupted by fire or reload");

        // The whiskey buff lands at INSPECT tick 60 (Strength III, Resistance III, Nausea I).
        tickHeld(helper, player, 60);
        helper.assertTrue(!player.hasEffect(MobEffects.DAMAGE_BOOST),
                "The buff must not apply before inspect tick 60");
        tickHeld(helper, player, 1);
        helper.assertTrue(hasLevel(player, MobEffects.DAMAGE_BOOST, 2)
                        && hasLevel(player, MobEffects.DAMAGE_RESISTANCE, 2)
                        && hasLevel(player, MobEffects.CONFUSION, 0),
                "At inspect tick 60 the holder gains Strength III, Resistance III and Nausea I");
        tickHeld(helper, player, 4);
        helper.assertTrue(SexyItem.state(stack) == SexyItem.GunState.IDLE,
                "After the 65-tick drinking animation the gun returns to IDLE");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wearGatesJamAtReloadEnd(GameTestHelper helper) {
        helper.assertTrue(SexyItem.jamChance(1_000.0F) == 0.0F
                        && SexyItem.jamChance(4_900.0F) >= 1.0F,
                "Jam chance stays zero below 66% wear and caps to one at high wear");

        Player jammer = armedPlayer(helper, Shotgun12GaugeAmmoType.BUCKSHOT, 5, 4_900.0F, 2);
        jammer.getInventory().add(Shotgun12GaugeAmmoType.BUCKSHOT.createStack(ModItems.AMMO_STANDARD.get(), 20));
        ItemStack jamGun = jammer.getMainHandItem();
        SednaGunItem.handleInput(jammer, GunInput.RELOAD);
        tickHeld(helper, jammer, 110);
        helper.assertTrue(SexyItem.state(jamGun) == SexyItem.GunState.JAMMED
                        && SexyItem.timer(jamGun) == 19,
                "A worn reload jams for nineteen ticks at the reload end");
        tickHeld(helper, jammer, 19);
        helper.assertTrue(SexyItem.state(jamGun) == SexyItem.GunState.IDLE,
                "The jam clears back to IDLE after nineteen ticks");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pelletCountsAndAmmoDamageMatchSource(GameTestHelper helper) {
        Player magnum = armedPlayer(helper, Shotgun12GaugeAmmoType.MAGNUM, 4, 0.0F, 2);
        SednaGunItem.handleInput(magnum, GunInput.PRIMARY);
        List<BulletEntity> magnumPellets = bullets(helper, magnum);
        helper.assertTrue(magnumPellets.size() == 4
                        && magnumPellets.stream().allMatch(pellet -> pellet.damage() == 32.0F),
                "Magnum spawns four pellets at 64 x 0.5 = 32 damage each (128 total)");

        Player explosive = armedPlayer(helper, Shotgun12GaugeAmmoType.EXPLOSIVE, 4, 0.0F, 6);
        SednaGunItem.handleInput(explosive, GunInput.PRIMARY);
        List<BulletEntity> explosiveShots = bullets(helper, explosive);
        helper.assertTrue(explosiveShots.size() == 1 && explosiveShots.get(0).damage() == 160.0F,
                "An explosive shell spawns one bullet at 64 x 2.5 = 160 damage");

        helper.assertTrue(totalDamage(Shotgun12GaugeAmmoType.MAGNUM) == 128.0F
                        && totalDamage(Shotgun12GaugeAmmoType.BUCKSHOT) == 64.0F
                        && totalDamage(Shotgun12GaugeAmmoType.EXPLOSIVE) == 160.0F,
                "damageWithAmmo must be 64 x mult x projectiles: magnum 128, buckshot 64, explosive 160");
        helper.succeed();
    }

    private static float totalDamage(Shotgun12GaugeAmmoType ammo) {
        return SexyItem.BASE_DAMAGE * ammo.damageMultiplier() * ammo.projectiles();
    }

    private static boolean hasLevel(Player player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
                                    int amplifier) {
        var instance = player.getEffect(effect);
        return instance != null && instance.getAmplifier() == amplifier;
    }

    private static Player armedPlayer(GameTestHelper helper, Shotgun12GaugeAmmoType ammo,
                                      int rounds, float wear, int x) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_AUTOSHOTGUN_SEXY.get());
        SexyItem.setTestState(gun, SexyItem.GunState.IDLE, 0, rounds, ammo, wear, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(x, 2, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(GameTestHelper helper, Player player, int ticks) {
        SexyItem gun = (SexyItem) player.getMainHandItem().getItem();
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
