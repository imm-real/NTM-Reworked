package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.RocketProjectileEntity;
import com.hbm.ntm.item.SednaGunItem;
import com.hbm.ntm.item.StingerLauncherItem;
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
public final class StingerGameTests {
    private StingerGameTests() { }

    @GameTest(template = "empty")
    public static void stingerKeepsItsSourceReceiverAndSightContract(GameTestHelper helper) {
        StingerLauncherItem gun = ModItems.GUN_STINGER.get();
        ItemStack stack = new ItemStack(gun);
        helper.assertTrue(gun.gunDurability() == 300.0F
                        && gun.gunCapacity() == 1
                        && StingerLauncherItem.BASE_DAMAGE == 35.0F
                        && StingerLauncherItem.DRAW_TICKS == 7
                        && StingerLauncherItem.INSPECT_TICKS == 40
                        && StingerLauncherItem.FIRE_DELAY == 5
                        && StingerLauncherItem.RELOAD_TICKS == 50
                        && StingerLauncherItem.JAM_TICKS == 40,
                "Stinger must retain the source 300/1/35/7/40/5/50/40 receiver");
        helper.assertTrue(gun.gunCrosshair() == SednaCrosshair.L_BOX_OUTLINE
                        && gun.gunCrosshairOnlyWhenAimed()
                        && !gun.gunHideCrosshairWhenAimed()
                        && gun.gunSecondaryAutomatic()
                        && gun.gunAimFovMultiplier() == 0.5F
                        && StingerLauncherItem.LOCK_TICKS == 60
                        && StingerLauncherItem.LOCK_DISTANCE == 150.0D
                        && StingerLauncherItem.LOCK_ANGLE == 10.0D,
                "Stinger must retain its box sight, held secondary lock, 50% zoom, and lock envelope");
        helper.assertTrue(StingerLauncherItem.rounds(stack) == 0
                        && StingerLauncherItem.loadedAmmo(stack) == RocketAmmoType.SHAPED_CHARGE,
                "source default ammunition must select HEAT without preloading the one-round tube");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void heldSecondaryAcquiresAndReleasingResetsTheSourceLock(GameTestHelper helper) {
        Player player = armed(helper, RocketAmmoType.SHAPED_CHARGE, 1);
        Zombie target = target(helper, player, 1.0D, 8.0D);

        SednaGunItem.handleInput(player, GunInput.TOGGLE_AIM);
        SednaGunItem.handleInput(player, GunInput.SECONDARY);
        tickHeld(player, StingerLauncherItem.LOCK_TICKS - 1);
        ItemStack gun = player.getMainHandItem();
        helper.assertTrue(StingerLauncherItem.aiming(gun)
                        && StingerLauncherItem.lockingOn(gun)
                        && !StingerLauncherItem.lockedOn(gun)
                        && StingerLauncherItem.lockProgress(gun) == 59
                        && StingerLauncherItem.lockTarget(gun) == target.getId(),
                "a valid target inside the ten-degree cone must accumulate exactly one lock tick per tick");

        tickHeld(player, 1);
        helper.assertTrue(StingerLauncherItem.lockedOn(gun)
                        && StingerLauncherItem.lockProgress(gun) == 60,
                "the source lock must complete and bleep on tick sixty");

        SednaGunItem.handleInput(player, GunInput.SECONDARY_RELEASE);
        tickHeld(player, 1);
        helper.assertTrue(!StingerLauncherItem.lockingOn(gun)
                        && !StingerLauncherItem.lockedOn(gun)
                        && StingerLauncherItem.lockProgress(gun) == 0,
                "releasing secondary must stop acquisition and reset the completed lock");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lockedShotCarriesTargetAndUsesTheMk4TurnCurve(GameTestHelper helper) {
        Player player = armed(helper, RocketAmmoType.SHAPED_CHARGE, 1);
        Zombie target = target(helper, player, 1.0D, 8.0D);
        ItemStack gun = player.getMainHandItem();
        StingerLauncherItem.setTestState(gun, StingerLauncherItem.GunState.IDLE,
                0, 1, RocketAmmoType.SHAPED_CHARGE, 0.0F,
                true, true, 60, true, target.getId());

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        List<RocketProjectileEntity> rockets = projectiles(helper, player);
        helper.assertTrue(rockets.size() == 1, "one completed lock must launch one physical rocket"
                + " (rockets=" + rockets.size()
                + ", state=" + StingerLauncherItem.state(gun)
                + ", rounds=" + StingerLauncherItem.rounds(gun)
                + ", locked=" + StingerLauncherItem.lockedOn(gun)
                + ", target=" + StingerLauncherItem.lockTarget(gun) + ")");
        RocketProjectileEntity rocket = rockets.getFirst();
        helper.assertTrue(rocket.getOwner() == player
                        && rocket.lockTargetId() == target.getId()
                        && rocket.ammoType() == RocketAmmoType.SHAPED_CHARGE
                        && rocket.damage() == 17.5F
                        && rocket.speed() == 0.0F
                        && StingerLauncherItem.rounds(gun) == 0
                        && StingerLauncherItem.timer(gun) == 5,
                "Stinger HEAT must launch at 35 x .5 damage and preserve the acquired entity ID");

        Vec3 initialDirection = rocket.direction();
        Vec3 launchPosition = rocket.position();
        // ServerLevel increments tickCount before invoking an entity tick.
        rocket.tickCount++;
        rocket.tick();
        double firstStep = rocket.position().distanceToSqr(launchPosition);
        Vec3 guidedDirection = rocket.direction();
        helper.assertTrue(firstStep < 1.0E-8D
                        && rocket.speed() == 0.4F
                        && guidedDirection.x > initialDirection.x,
                "the first rocket tick must move at zero speed, then home with .005 turn and accelerate .4"
                        + " (step=" + firstStep
                        + ", speed=" + rocket.speed()
                        + ", initial=" + initialDirection
                        + ", guided=" + guidedDirection + ")");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stingerUsesTheSharedInventoryFirstSingleRocketReload(GameTestHelper helper) {
        Player player = armed(helper, RocketAmmoType.SHAPED_CHARGE, 0);
        ItemStack phosphorus = RocketAmmoType.WHITE_PHOSPHORUS
                .createStack(ModItems.AMMO_STANDARD.get(), 2);
        ItemStack heat = RocketAmmoType.SHAPED_CHARGE.createStack(ModItems.AMMO_STANDARD.get(), 2);
        player.getInventory().add(phosphorus);
        player.getInventory().add(heat);

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(StingerLauncherItem.state(player.getMainHandItem())
                        == StingerLauncherItem.GunState.RELOADING
                        && StingerLauncherItem.timer(player.getMainHandItem()) == 50,
                "an empty Stinger must enter the source fifty-tick single reload");
        tickHeld(player, 50);
        helper.assertTrue(StingerLauncherItem.rounds(player.getMainHandItem()) == 1
                        && StingerLauncherItem.loadedAmmo(player.getMainHandItem())
                        == RocketAmmoType.WHITE_PHOSPHORUS
                        && countAmmo(player, RocketAmmoType.WHITE_PHOSPHORUS) == 1
                        && countAmmo(player, RocketAmmoType.SHAPED_CHARGE) == 2,
                "Stinger reload must select inventory-first rocket identity and consume exactly one");
        helper.succeed();
    }

    private static Player armed(GameTestHelper helper, RocketAmmoType ammo, int rounds) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_STINGER.get());
        StingerLauncherItem.setTestState(gun, StingerLauncherItem.GunState.IDLE,
                0, rounds, ammo, 0.0F, false, false, 0, false, -1);
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
        StingerLauncherItem item = (StingerLauncherItem) player.getMainHandItem().getItem();
        for (int i = 0; i < ticks; i++) {
            item.inventoryTick(player.getMainHandItem(), player.level(), player,
                    player.getInventory().selected, true);
        }
    }

    private static List<RocketProjectileEntity> projectiles(GameTestHelper helper, Player owner) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntitiesOfClass(RocketProjectileEntity.class,
                new AABB(origin).inflate(64.0D), rocket -> rocket.getOwner() == owner);
    }

    private static int countAmmo(Player player, RocketAmmoType ammo) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.AMMO_STANDARD.get()) && StandardAmmoTypes.fromStack(stack) == ammo) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
