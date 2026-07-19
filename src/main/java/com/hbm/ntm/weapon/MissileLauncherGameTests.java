package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.RocketProjectileEntity;
import com.hbm.ntm.item.MissileLauncherItem;
import com.hbm.ntm.item.RocketLauncherItem;
import com.hbm.ntm.item.SednaGunItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
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
public final class MissileLauncherGameTests {
    private MissileLauncherGameTests() { }

    @GameTest(template = "empty")
    public static void missileLauncherKeepsItsSourceReceiverContract(GameTestHelper helper) {
        MissileLauncherItem gun = ModItems.GUN_MISSILE_LAUNCHER.get();
        ItemStack stack = new ItemStack(gun);
        helper.assertTrue(gun.gunDurability() == 500.0F
                        && gun.gunCapacity() == 1
                        && MissileLauncherItem.BASE_DAMAGE == 50.0F
                        && MissileLauncherItem.DRAW_TICKS == 20
                        && MissileLauncherItem.INSPECT_TICKS == 40
                        && MissileLauncherItem.FIRE_DELAY == 5
                        && MissileLauncherItem.RELOAD_TICKS == 48
                        && MissileLauncherItem.JAM_TICKS == 33,
                "Missile Launcher must retain the source 500/1/50/20/40/5/48/33 receiver");
        helper.assertTrue(gun.gunCrosshair() == SednaCrosshair.L_CIRCUMFLEX
                        && !gun.gunHideCrosshairWhenAimed()
                        && gun.gunAimFovMultiplier() == 1.0F
                        && gun.recoilVertical() == 0.0F
                        && gun.recoilHorizontalSigma() == 0.0F
                        && MissileLauncherItem.LOCK_DISTANCE == 150.0D
                        && MissileLauncherItem.LOCK_ANGLE == 20.0D,
                "Missile Launcher must retain its visible sight, no zoom, no recoil, and lock envelope");
        helper.assertTrue(MissileLauncherItem.rounds(stack) == 0
                        && MissileLauncherItem.loadedAmmo(stack) == RocketAmmoType.SHAPED_CHARGE,
                "source default ammunition must select HEAT without preloading the launcher");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aimedShotInstantlyAcquiresTheSmallestAngleTarget(GameTestHelper helper) {
        Player player = armed(helper, true);
        Zombie outer = target(helper, player, 3.0D, 10.0D);
        Zombie center = target(helper, player, 1.0D, 12.0D);

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        List<RocketProjectileEntity> rockets = projectiles(helper, player);
        helper.assertTrue(rockets.size() == 1, "one trigger pull must launch one physical missile");
        RocketProjectileEntity rocket = rockets.getFirst();
        helper.assertTrue(rocket.lockTargetId() == center.getId()
                        && rocket.lockTargetId() != outer.getId()
                        && rocket.flightMode() == RocketProjectileEntity.FlightMode.MISSILE_LAUNCHER
                        && rocket.ammoType() == RocketAmmoType.SHAPED_CHARGE
                        && rocket.damage() == 25.0F
                        && rocket.speed() == 0.0F
                        && rocket.lifeTicks() == 300
                        && MissileLauncherItem.rounds(player.getMainHandItem()) == 0,
                "an aimed HEAT shot must instant-lock the smallest-angle target and use the ML projectile");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hipShotRemainsUnguidedAndUsesStandardAcceleration(GameTestHelper helper) {
        Player player = armed(helper, false);
        target(helper, player, 0.0D, 10.0D);

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        RocketProjectileEntity rocket = projectiles(helper, player).getFirst();
        rocket.tickCount++;
        rocket.tick();
        helper.assertTrue(rocket.lockTargetId() == -1
                        && rocket.flightMode() == RocketProjectileEntity.FlightMode.MISSILE_LAUNCHER
                        && rocket.speed() == 0.4F,
                "without aiming, the missile must stay unguided and retain standard .4 acceleration");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missileLauncherUsesItsFortyEightTickSingleReload(GameTestHelper helper) {
        Player player = armed(helper, false);
        RocketLauncherItem.setTestState(player.getMainHandItem(),
                RocketLauncherItem.GunState.IDLE, 0, 0,
                RocketAmmoType.SHAPED_CHARGE, 0.0F, false);
        player.getInventory().add(RocketAmmoType.WHITE_PHOSPHORUS
                .createStack(ModItems.AMMO_STANDARD.get(), 2));
        player.getInventory().add(RocketAmmoType.SHAPED_CHARGE
                .createStack(ModItems.AMMO_STANDARD.get(), 2));

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(MissileLauncherItem.state(player.getMainHandItem())
                        == RocketLauncherItem.GunState.RELOADING
                        && MissileLauncherItem.timer(player.getMainHandItem()) == 48,
                "an empty Missile Launcher must begin its source forty-eight-tick reload");
        tickHeld(player, 47);
        helper.assertTrue(MissileLauncherItem.rounds(player.getMainHandItem()) == 0,
                "the missile must not enter the launcher before reload completion");
        tickHeld(player, 1);
        helper.assertTrue(MissileLauncherItem.rounds(player.getMainHandItem()) == 1
                        && MissileLauncherItem.loadedAmmo(player.getMainHandItem())
                        == RocketAmmoType.WHITE_PHOSPHORUS
                        && countAmmo(player, RocketAmmoType.WHITE_PHOSPHORUS) == 1
                        && countAmmo(player, RocketAmmoType.SHAPED_CHARGE) == 2,
                "single reload must choose inventory-first rocket identity and consume exactly one");
        helper.succeed();
    }

    private static Player armed(GameTestHelper helper, boolean aiming) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_MISSILE_LAUNCHER.get());
        RocketLauncherItem.setTestState(gun, RocketLauncherItem.GunState.IDLE,
                0, 1, RocketAmmoType.SHAPED_CHARGE, 0.0F, aiming);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static Zombie target(GameTestHelper helper, Player player,
                                 double side, double forward) {
        Zombie target = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(target != null, "test target must be constructible");
        target.setNoAi(true);
        target.setPos(player.getX() + side, player.getY(), player.getZ() + forward);
        helper.getLevel().addFreshEntity(target);
        return target;
    }

    private static void tickHeld(Player player, int ticks) {
        MissileLauncherItem item = (MissileLauncherItem) player.getMainHandItem().getItem();
        for (int i = 0; i < ticks; i++) {
            item.inventoryTick(player.getMainHandItem(), player.level(), player,
                    player.getInventory().selected, true);
        }
    }

    private static List<RocketProjectileEntity> projectiles(GameTestHelper helper, Player owner) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntitiesOfClass(RocketProjectileEntity.class,
                new AABB(origin).inflate(256.0D), rocket -> rocket.getOwner() == owner);
    }

    private static int countAmmo(Player player, RocketAmmoType ammo) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.AMMO_STANDARD.get())
                    && StandardAmmoTypes.fromStack(stack) == ammo) count += stack.getCount();
        }
        return count;
    }
}
