package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.LagPistolItem;
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
public final class LagPistolGameTests {
    private LagPistolGameTests() { }

    @GameTest(template = "empty")
    public static void sourceConfigurationAndDefaultAmmoContainerArePreserved(GameTestHelper helper) {
        LagPistolItem gun = ModItems.GUN_LAG.get();
        ItemStack fresh = new ItemStack(gun);
        helper.assertTrue(LagPistolItem.DURABILITY == 1_700
                        && LagPistolItem.DRAW_TICKS == 7
                        && LagPistolItem.FIRE_DELAY == 4
                        && LagPistolItem.DRY_TICKS == 10
                        && LagPistolItem.RELOAD_TICKS == 53
                        && LagPistolItem.JAM_TICKS == 44
                        && LagPistolItem.INSPECT_TICKS == 31,
                "LAG timing and durability must match XFactory9mm");
        helper.assertTrue(LagPistolItem.CAPACITY == 17 && LagPistolItem.BASE_DAMAGE == 25.0F
                        && LagPistolItem.INNATE_SPREAD == 0.005F,
                "LAG must retain its seventeen-round receiver and source damage/spread");
        helper.assertTrue(gun.gunCrosshair() == SednaCrosshair.CIRCLE
                        && gun.recoilVertical() == 5.0F && gun.recoilHorizontalSigma() == 1.5F,
                "LAG must retain its circle reticle and source recoil");
        helper.assertTrue(LagPistolItem.rounds(fresh) == 0
                        && LagPistolItem.loadedAmmo(fresh) == NineMillimeterAmmoType.HOLLOW_POINT,
                "Fresh LAG identity must match the separate source 17-round JHP default ammo container");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void normalFireUsesNineMillimeterAndFourTickCycle(GameTestHelper helper) {
        Player player = armedPlayer(helper, NineMillimeterAmmoType.HOLLOW_POINT, 2, 0.0F);
        ItemStack stack = player.getMainHandItem();
        SednaGunItem.handleInput(player, GunInput.PRIMARY);

        helper.assertTrue(LagPistolItem.rounds(stack) == 1
                        && LagPistolItem.timer(stack) == 4
                        && LagPistolItem.animation(stack) == LagPistolItem.GunAnimation.CYCLE,
                "A normal LAG shot must consume one round and start its four-tick cycle");
        List<BulletEntity> bullets = bullets(helper, player);
        helper.assertTrue(bullets.size() == 1 && bullets.getFirst().damage() == 37.5F,
                "9mm JHP must apply its 1.5 multiplier to the 25-damage receiver");
        tickHeld(helper, player, 4);
        helper.assertTrue(LagPistolItem.state(stack) == LagPistolItem.GunState.IDLE,
                "LAG must return to idle after four held ticks");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullReloadWaitsAndKeepsMagazineHomogeneous(GameTestHelper helper) {
        Player player = armedPlayer(helper, NineMillimeterAmmoType.FULL_METAL_JACKET, 2, 0.0F);
        player.getInventory().add(NineMillimeterAmmoType.SOFT_POINT.createStack(ModItems.AMMO_STANDARD.get(), 17));
        player.getInventory().add(NineMillimeterAmmoType.FULL_METAL_JACKET.createStack(
                ModItems.AMMO_STANDARD.get(), 17));
        ItemStack gun = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(LagPistolItem.state(gun) == LagPistolItem.GunState.RELOADING
                        && LagPistolItem.timer(gun) == 53,
                "LAG reload must begin the source 53-tick full-magazine action");
        tickHeld(helper, player, 52);
        helper.assertTrue(LagPistolItem.rounds(gun) == 2 && LagPistolItem.timer(gun) == 1,
                "LAG reload must not transfer ammunition before its final tick");
        tickHeld(helper, player, 1);
        helper.assertTrue(LagPistolItem.rounds(gun) == 17
                        && LagPistolItem.loadedAmmo(gun) == NineMillimeterAmmoType.FULL_METAL_JACKET,
                "A partial LAG magazine must accept only its existing 9mm identity");
        helper.assertTrue(countAmmo(player, NineMillimeterAmmoType.FULL_METAL_JACKET) == 2
                        && countAmmo(player, NineMillimeterAmmoType.SOFT_POINT) == 17,
                "Reload must consume fifteen compatible rounds and leave other 9mm types alone");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lateInspectShotKillsHolderWithoutSpawningBullet(GameTestHelper helper) {
        Player player = armedPlayer(helper, NineMillimeterAmmoType.HOLLOW_POINT, 17, 0.0F);
        ItemStack gun = player.getMainHandItem();

        // Warm the held marker so inventoryTick does not replace the requested inspect with EQUIP.
        tickHeld(helper, player, 1);
        LagPistolItem.setTestState(gun, LagPistolItem.GunState.IDLE, 0, 17,
                NineMillimeterAmmoType.HOLLOW_POINT, 0.0F);
        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(LagPistolItem.state(gun) == LagPistolItem.GunState.IDLE
                        && LagPistolItem.animation(gun) == LagPistolItem.GunAnimation.INSPECT,
                "A full LAG must use its source cancellable inspect while remaining fire-ready");

        tickHeld(helper, player, 21);
        helper.assertTrue(LagPistolItem.animationTimer(gun) == 21 && player.isAlive(),
                "The holder must remain alive until the source inspect firing window opens");
        SednaGunItem.handleInput(player, GunInput.PRIMARY);

        helper.assertTrue(LagPistolItem.rounds(gun) == 16
                        && LagPistolItem.state(gun) == LagPistolItem.GunState.COOLDOWN
                        && LagPistolItem.timer(gun) == 4
                        && LagPistolItem.animation(gun) == LagPistolItem.GunAnimation.INSPECT,
                "Late inspect fire must consume one round without replacing the inspect animation");
        helper.assertTrue(!player.isAlive() && bullets(helper, player).isEmpty(),
                "The source joke must deal 1000 damage to the holder and spawn no projectile");
        helper.succeed();
    }

    private static Player armedPlayer(GameTestHelper helper, NineMillimeterAmmoType ammo,
                                      int rounds, float wear) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_LAG.get());
        LagPistolItem.setTestState(gun, LagPistolItem.GunState.IDLE, 0, rounds, ammo, wear);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(GameTestHelper helper, Player player, int ticks) {
        LagPistolItem gun = (LagPistolItem) player.getMainHandItem().getItem();
        for (int i = 0; i < ticks; i++) {
            gun.inventoryTick(player.getMainHandItem(), helper.getLevel(), player,
                    player.getInventory().selected, true);
        }
    }

    private static int countAmmo(Player player, NineMillimeterAmmoType type) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.AMMO_STANDARD.get()) && StandardAmmoTypes.fromStack(stack) == type) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static List<BulletEntity> bullets(GameTestHelper helper, Player owner) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntitiesOfClass(BulletEntity.class,
                new AABB(origin).inflate(64.0D), bullet -> bullet.getOwner() == owner);
    }
}
