package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.MaresLegItem;
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
public final class MaresLegGameTests {
    private MaresLegGameTests() { }

    @GameTest(template = "empty")
    public static void twelveGaugeVariantsPreserveSourceIdentityAndEffects(GameTestHelper helper) {
        Shotgun12GaugeAmmoType[] types = Shotgun12GaugeAmmoType.values();
        helper.assertTrue(types.length == 9, "The ordinary 12-gauge family must contain nine source loads");
        for (int index = 0; index < types.length; index++) {
            Shotgun12GaugeAmmoType type = types[index];
            ItemStack stack = type.createStack(ModItems.AMMO_STANDARD.get(), 4);
            helper.assertTrue(StandardAmmoTypes.fromStack(stack) == type,
                    type + " must survive component serialization");
            helper.assertTrue(type.legacyMetadata() == 41 + index
                            && type.legacyBulletConfig() == 43 + index,
                    type + " must retain its source metadata and BulletConfig index");
            helper.assertTrue(!type.penetrates() && type.maxRicochets() == 2,
                    type + " must retain the standard non-penetrating projectile baseline");
        }

        helper.assertTrue(Shotgun12GaugeAmmoType.BLACK_POWDER_BUCKSHOT.projectiles() == 8
                        && Shotgun12GaugeAmmoType.BLACK_POWDER_BUCKSHOT.damageMultiplier() == 0.75F / 8.0F
                        && Shotgun12GaugeAmmoType.BLACK_POWDER_BUCKSHOT.blackPowder(),
                "Black-powder buckshot must retain its eight pellets and 0.75 total multiplier");
        helper.assertTrue(Shotgun12GaugeAmmoType.SLUG.projectiles() == 1
                        && Shotgun12GaugeAmmoType.SLUG.headshotMultiplier() == 1.5F
                        && Shotgun12GaugeAmmoType.SLUG.armorThresholdNegation() == 4.0F
                        && Shotgun12GaugeAmmoType.SLUG.armorPiercing() == 0.15F,
                "The slug must retain its headshot, threshold, and armor-piercing values");
        helper.assertTrue(Shotgun12GaugeAmmoType.EXPLOSIVE.impactExplosionRadius() == 2.0F
                        && Shotgun12GaugeAmmoType.PHOSPHORUS.phosphorusTicks() == 300,
                "The two special shells must retain their source impact effects");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mareslegUsesSourceConfigurationAndBuckshotPelletCount(GameTestHelper helper) {
        MaresLegItem item = ModItems.GUN_MARESLEG.get();
        ItemStack fresh = new ItemStack(item);
        helper.assertTrue(MaresLegItem.DURABILITY == 600
                        && MaresLegItem.DRAW_TICKS == 10
                        && MaresLegItem.FIRE_DELAY == 20
                        && MaresLegItem.RELOAD_BEGIN_TICKS == 22
                        && MaresLegItem.RELOAD_CYCLE_TICKS == 10
                        && MaresLegItem.RELOAD_END_TICKS == 13
                        && MaresLegItem.JAM_TICKS == 24,
                "Maresleg durability and action timing must match XFactory12ga");
        helper.assertTrue(item.baseDamage() == 16.0F && item.gunCapacity() == 6
                        && item.recoilVertical() == 10.0F
                        && item.recoilHorizontalSigma() == 1.5F
                        && item.gunCrosshair() == SednaCrosshair.L_CIRCLE,
                "Maresleg damage, tube, recoil, and large-circle reticle must match the source");
        helper.assertTrue(MaresLegItem.rounds(fresh) == 0
                        && MaresLegItem.loadedAmmo(fresh) == Shotgun12GaugeAmmoType.BUCKSHOT,
                "A fresh gun must be empty with the source default buckshot identity");

        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.BUCKSHOT, 2, 0.0F);
        double playerX = player.getX();
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        List<BulletEntity> pellets = bullets(helper, player);
        helper.assertTrue(pellets.size() == 8 && pellets.stream().allMatch(pellet -> pellet.damage() == 2.0F),
                "Buckshot must spawn eight two-damage pellets from sixteen base damage");
        helper.assertTrue(pellets.stream().allMatch(pellet -> Math.abs(pellet.getX() - (playerX - 0.1875D)) < 0.0001D),
                "Every hip-fired pellet must originate at the exact Maresleg side offset");
        helper.assertTrue(MaresLegItem.rounds(player.getMainHandItem()) == 1
                        && MaresLegItem.state(player.getMainHandItem()) == MaresLegItem.GunState.COOLDOWN
                        && MaresLegItem.timer(player.getMainHandItem()) == 20,
                "One shell and one wear step must be consumed per source cycle");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tubeReloadLoadsOneShellPerCycleLocksTypeAndCanCancel(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.FLECHETTE, 2, 0.0F);
        ItemStack gun = player.getMainHandItem();
        player.getInventory().add(Shotgun12GaugeAmmoType.SLUG.createStack(ModItems.AMMO_STANDARD.get(), 8));
        player.getInventory().add(Shotgun12GaugeAmmoType.FLECHETTE.createStack(ModItems.AMMO_STANDARD.get(), 8));

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(MaresLegItem.state(gun) == MaresLegItem.GunState.RELOADING
                        && MaresLegItem.timer(gun) == 22 && MaresLegItem.amountBeforeReload(gun) == 2,
                "Tube reload must begin with the source 22-tick opening phase");
        tickHeld(helper, player, 21);
        helper.assertTrue(MaresLegItem.rounds(gun) == 2 && MaresLegItem.timer(gun) == 1,
                "No shell may transfer before the opening phase ends");
        tickHeld(helper, player, 1);
        helper.assertTrue(MaresLegItem.rounds(gun) == 3
                        && MaresLegItem.timer(gun) == 10
                        && MaresLegItem.loadedAmmo(gun) == Shotgun12GaugeAmmoType.FLECHETTE,
                "The first transfer must retain the partially loaded tube's shell identity");

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        tickHeld(helper, player, 10);
        helper.assertTrue(MaresLegItem.rounds(gun) == 4
                        && MaresLegItem.state(gun) == MaresLegItem.GunState.DRAWING
                        && MaresLegItem.timer(gun) == 13
                        && countAmmo(player, Shotgun12GaugeAmmoType.SLUG) == 8,
                "Canceling must finish the current shell, preserve other loads, and enter the 13-tick close");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void phosphorusStatusKeepsTheLongerSourceTimer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        WeaponStatusEvents.applyPhosphorus(player, 300);
        WeaponStatusEvents.applyPhosphorus(player, 200);
        helper.assertTrue(WeaponStatusEvents.phosphorusTicks(player) == 300,
                "A shorter phosphorus hit may not truncate the active source timer");
        WeaponStatusEvents.applyPhosphorus(player, 400);
        helper.assertTrue(WeaponStatusEvents.phosphorusTicks(player) == 400,
                "A longer phosphorus hit must extend the active timer");
        helper.succeed();
    }

    private static Player armedPlayer(GameTestHelper helper, Shotgun12GaugeAmmoType ammo,
                                      int rounds, float wear) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_MARESLEG.get());
        MaresLegItem.setTestState(gun, MaresLegItem.GunState.IDLE, 0, rounds, ammo, wear);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(GameTestHelper helper, Player player, int ticks) {
        MaresLegItem gun = (MaresLegItem) player.getMainHandItem().getItem();
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
