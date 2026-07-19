package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.FortyMillimeterProjectileEntity;
import com.hbm.ntm.item.FortyMillimeterGunItem;
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
public final class FortyMillimeterGameTests {
    private FortyMillimeterGameTests() { }

    @GameTest(template = "empty")
    public static void sourceConfigurationAndStableAmmoMetadata(GameTestHelper helper) {
        FortyMillimeterGunItem flare = ModItems.GUN_FLAREGUN.get();
        FortyMillimeterGunItem congo = ModItems.GUN_CONGOLAKE.get();
        FortyMillimeterGunItem mk108 = ModItems.GUN_MK108.get();
        helper.assertTrue(flare.gunCapacity() == 1 && flare.gunDurability() == 100.0F
                        && flare.variant().baseDamage() == 15.0F && flare.variant().drawTicks() == 7
                        && flare.variant().fireDelay() == 20 && flare.variant().reloadTicks() == 28
                        && flare.variant().jamTicks() == 33,
                "Flare Gun must retain source 100/1/15/7/20/28/33 configuration");
        helper.assertTrue(congo.gunCapacity() == 4 && congo.gunDurability() == 400.0F
                        && congo.variant().baseDamage() == 20.0F && congo.variant().drawTicks() == 7
                        && congo.variant().fireDelay() == 24 && congo.variant().sequentialReload()
                        && congo.variant().reloadBeginTicks() == 16
                        && congo.variant().reloadCycleTicks() == 16
                        && congo.variant().reloadEndTicks() == 16,
                "Congo Lake must retain its four-round 16/16/16 sequential reload");
        helper.assertTrue(mk108.gunCapacity() == 30 && mk108.gunDurability() == 5_000.0F
                        && mk108.variant().baseDamage() == 25.0F && mk108.variant().drawTicks() == 20
                        && mk108.variant().fireDelay() == 10 && mk108.variant().reloadTicks() == 135
                        && mk108.variant().jamTicks() == 25 && mk108.gunAutomatic()
                        && !mk108.gunHideCrosshairWhenAimed(),
                "MK108 must retain source 5000/30/25/20/10/135/25 automatic configuration");
        helper.assertTrue(FortyMillimeterAmmoType.SIGNAL_FLARE.legacyMetadata() == 50
                        && FortyMillimeterAmmoType.HIGH_EXPLOSIVE.legacyMetadata() == 53
                        && FortyMillimeterAmmoType.WHITE_PHOSPHORUS.legacyMetadata() == 57,
                "40 mm identities must preserve source EnumAmmo ordinals while C-130 flares remain gaps 51-52");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void freshGunsAreEmptyWithSourceDefaultIdentity(GameTestHelper helper) {
        ItemStack flare = new ItemStack(ModItems.GUN_FLAREGUN.get());
        ItemStack congo = new ItemStack(ModItems.GUN_CONGOLAKE.get());
        ItemStack mk108 = new ItemStack(ModItems.GUN_MK108.get());
        helper.assertTrue(FortyMillimeterGunItem.rounds(flare) == 0
                        && FortyMillimeterGunItem.loadedAmmo(flare) == FortyMillimeterAmmoType.SIGNAL_FLARE,
                "setDefaultAmmo on the Flare Gun sets identity but does not preload the chamber");
        helper.assertTrue(FortyMillimeterGunItem.rounds(congo) == 0
                        && FortyMillimeterGunItem.loadedAmmo(congo) == FortyMillimeterAmmoType.HIGH_EXPLOSIVE
                        && FortyMillimeterGunItem.rounds(mk108) == 0
                        && FortyMillimeterGunItem.loadedAmmo(mk108) == FortyMillimeterAmmoType.HIGH_EXPLOSIVE,
                "Both grenade launchers must begin empty with G40 HE selected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eachGunLaunchesThePhysicalSourceProjectile(GameTestHelper helper) {
        Player flare = armed(helper, ModItems.GUN_FLAREGUN.get(), FortyMillimeterAmmoType.SIGNAL_FLARE, 1, 2);
        Player congo = armed(helper, ModItems.GUN_CONGOLAKE.get(), FortyMillimeterAmmoType.SHAPED_CHARGE, 1, 6);
        Player mk108 = armed(helper, ModItems.GUN_MK108.get(), FortyMillimeterAmmoType.DEMOLITION, 1, 10);
        SednaGunItem.handleInput(flare, GunInput.PRIMARY);
        SednaGunItem.handleInput(congo, GunInput.PRIMARY);
        SednaGunItem.handleInput(mk108, GunInput.PRIMARY);
        List<FortyMillimeterProjectileEntity> shots = projectiles(helper);
        helper.assertTrue(shots.size() == 3, "Each launcher must create one slow physical projectile");
        FortyMillimeterProjectileEntity flareShot = ownerShot(shots, flare);
        FortyMillimeterProjectileEntity congoShot = ownerShot(shots, congo);
        FortyMillimeterProjectileEntity mkShot = ownerShot(shots, mk108);
        helper.assertTrue(flareShot.ammoType() == FortyMillimeterAmmoType.SIGNAL_FLARE
                        && flareShot.damage() == 15.0F,
                "Flare Gun must launch the 100-tick, gravity .015 signal flare at 15 damage");
        helper.assertTrue(congoShot.ammoType() == FortyMillimeterAmmoType.SHAPED_CHARGE
                        && congoShot.damage() == 10.0F,
                "Congo HEAT projectile must use 20 x .5 = 10 blast damage before its x3 direct hit");
        helper.assertTrue(mkShot.ammoType() == FortyMillimeterAmmoType.DEMOLITION
                        && mkShot.damage() == 18.75F,
                "MK108 demolition projectile must use 25 x .75 = 18.75 damage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullAndSequentialReloadsKeepCaliberAndType(GameTestHelper helper) {
        Player flare = armed(helper, ModItems.GUN_FLAREGUN.get(), FortyMillimeterAmmoType.SIGNAL_FLARE, 0, 2);
        flare.getInventory().add(FortyMillimeterAmmoType.SIGNAL_FLARE.createStack(ModItems.AMMO_STANDARD.get(), 2));
        SednaGunItem.handleInput(flare, GunInput.RELOAD);
        tickHeld(flare, 28);
        helper.assertTrue(FortyMillimeterGunItem.rounds(flare.getMainHandItem()) == 1,
                "Flare Gun must complete its one-round full reload after 28 ticks");

        Player congo = armed(helper, ModItems.GUN_CONGOLAKE.get(), FortyMillimeterAmmoType.INCENDIARY, 0, 6);
        congo.getInventory().add(FortyMillimeterAmmoType.INCENDIARY.createStack(ModItems.AMMO_STANDARD.get(), 4));
        congo.getInventory().add(FortyMillimeterAmmoType.HIGH_EXPLOSIVE.createStack(ModItems.AMMO_STANDARD.get(), 4));
        SednaGunItem.handleInput(congo, GunInput.RELOAD);
        tickHeld(congo, 16);
        helper.assertTrue(FortyMillimeterGunItem.rounds(congo.getMainHandItem()) == 1
                        && FortyMillimeterGunItem.loadedAmmo(congo.getMainHandItem()) == FortyMillimeterAmmoType.INCENDIARY,
                "Congo Lake must load one inventory-first grenade after ReloadEmpty");
        tickHeld(congo, 48);
        helper.assertTrue(FortyMillimeterGunItem.rounds(congo.getMainHandItem()) == 4
                        && FortyMillimeterGunItem.loadedAmmo(congo.getMainHandItem()) == FortyMillimeterAmmoType.INCENDIARY,
                "Congo Lake must fill one matching grenade per 16-tick cycle without mixing HE");

        Player mk108 = armed(helper, ModItems.GUN_MK108.get(), FortyMillimeterAmmoType.HIGH_EXPLOSIVE, 0, 10);
        mk108.getInventory().add(FortyMillimeterAmmoType.HIGH_EXPLOSIVE.createStack(ModItems.AMMO_STANDARD.get(), 40));
        SednaGunItem.handleInput(mk108, GunInput.RELOAD);
        tickHeld(mk108, 135);
        helper.assertTrue(FortyMillimeterGunItem.rounds(mk108.getMainHandItem()) == 30,
                "MK108 must atomically fill its thirty-round belt at tick 135");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mk108RefiresEveryTenTicksAndDryFiresOnce(GameTestHelper helper) {
        Player player = armed(helper, ModItems.GUN_MK108.get(), FortyMillimeterAmmoType.HIGH_EXPLOSIVE, 2, 2);
        ItemStack gun = player.getMainHandItem();
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(FortyMillimeterGunItem.rounds(gun) == 1
                        && FortyMillimeterGunItem.timer(gun) == 10,
                "MK108 must fire immediately and begin its ten-tick automatic cycle");
        tickHeld(player, 10);
        helper.assertTrue(FortyMillimeterGunItem.rounds(gun) == 0 && projectiles(helper).size() == 2,
                "Held MK108 must fire the second grenade exactly at the ten-tick boundary");
        tickHeld(player, 10);
        helper.assertTrue(FortyMillimeterGunItem.animation(gun) == FortyMillimeterGunItem.GunAnimation.CYCLE_DRY
                        && FortyMillimeterGunItem.state(gun) == FortyMillimeterGunItem.GunState.DRAWING,
                "dryfireAfterAuto must produce one dry cycle after the belt empties");
        helper.succeed();
    }

    private static Player armed(GameTestHelper helper, FortyMillimeterGunItem item,
                                FortyMillimeterAmmoType ammo, int rounds, int x) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(item);
        FortyMillimeterGunItem.setTestState(gun, FortyMillimeterGunItem.GunState.IDLE,
                0, rounds, ammo, 0.0F, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(x, 3, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(Player player, int ticks) {
        FortyMillimeterGunItem item = (FortyMillimeterGunItem) player.getMainHandItem().getItem();
        for (int i = 0; i < ticks; i++) item.inventoryTick(player.getMainHandItem(), player.level(), player,
                player.getInventory().selected, true);
    }

    private static List<FortyMillimeterProjectileEntity> projectiles(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntitiesOfClass(FortyMillimeterProjectileEntity.class,
                new AABB(origin).inflate(64.0D));
    }

    private static FortyMillimeterProjectileEntity ownerShot(List<FortyMillimeterProjectileEntity> shots,
                                                               Player player) {
        return shots.stream().filter(shot -> shot.getOwner() == player).findFirst().orElseThrow();
    }
}
