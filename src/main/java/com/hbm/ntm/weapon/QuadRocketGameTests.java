package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.RocketProjectileEntity;
import com.hbm.ntm.item.QuadRocketLauncherItem;
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
public final class QuadRocketGameTests {
    private QuadRocketGameTests() { }

    @GameTest(template = "empty")
    public static void quadroKeepsItsSourceReceiverContract(GameTestHelper helper) {
        QuadRocketLauncherItem gun = ModItems.GUN_QUADRO.get();
        ItemStack stack = new ItemStack(gun);
        helper.assertTrue(gun.gunDurability() == 400.0F
                        && gun.gunCapacity() == 4
                        && QuadRocketLauncherItem.BASE_DAMAGE == 40.0F
                        && QuadRocketLauncherItem.DRAW_TICKS == 7
                        && QuadRocketLauncherItem.INSPECT_TICKS == 40
                        && QuadRocketLauncherItem.FIRE_DELAY == 10
                        && QuadRocketLauncherItem.RELOAD_TICKS == 55
                        && QuadRocketLauncherItem.JAM_TICKS == 40,
                "Quadro must retain the source 400/4/40/7/40/10/55/40 receiver");
        helper.assertTrue(gun.gunCrosshair() == SednaCrosshair.L_CIRCUMFLEX
                        && !gun.gunHideCrosshairWhenAimed()
                        && gun.recoilVertical() == 0.0F
                        && gun.recoilHorizontalSigma() == 0.0F,
                "Quadro must retain the visible circumflex sight and no-op recoil");
        helper.assertTrue(QuadRocketLauncherItem.rounds(stack) == 0
                        && QuadRocketLauncherItem.loadedAmmo(stack)
                        == RocketAmmoType.HIGH_EXPLOSIVE,
                "source default ammunition must select HE without preloading the four-tube rack");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void partialQuadroReloadIsAtomicAndKeepsItsRocketIdentity(GameTestHelper helper) {
        Player player = armed(helper, RocketAmmoType.SHAPED_CHARGE, 1, false);
        player.getInventory().add(RocketAmmoType.SHAPED_CHARGE
                .createStack(ModItems.AMMO_STANDARD.get(), 4));
        player.getInventory().add(RocketAmmoType.HIGH_EXPLOSIVE
                .createStack(ModItems.AMMO_STANDARD.get(), 2));

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(QuadRocketLauncherItem.state(player.getMainHandItem())
                        == QuadRocketLauncherItem.GunState.RELOADING
                        && QuadRocketLauncherItem.timer(player.getMainHandItem()) == 55,
                "Quadro must begin its source fifty-five-tick full reload");
        tickHeld(player, 54);
        helper.assertTrue(QuadRocketLauncherItem.rounds(player.getMainHandItem()) == 1,
                "MagazineFullReload must not insert any rocket before the atomic completion tick");
        tickHeld(player, 1);
        helper.assertTrue(QuadRocketLauncherItem.rounds(player.getMainHandItem()) == 4
                        && QuadRocketLauncherItem.loadedAmmo(player.getMainHandItem())
                        == RocketAmmoType.SHAPED_CHARGE
                        && countAmmo(player, RocketAmmoType.SHAPED_CHARGE) == 1
                        && countAmmo(player, RocketAmmoType.HIGH_EXPLOSIVE) == 2,
                "a partial rack must consume three matching rockets and reject mixed identities");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyQuadroReloadSelectsInventoryFirstRocketIdentity(GameTestHelper helper) {
        Player player = armed(helper, RocketAmmoType.HIGH_EXPLOSIVE, 0, false);
        player.getInventory().add(RocketAmmoType.WHITE_PHOSPHORUS
                .createStack(ModItems.AMMO_STANDARD.get(), 2));
        player.getInventory().add(RocketAmmoType.DEMOLITION
                .createStack(ModItems.AMMO_STANDARD.get(), 4));

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        tickHeld(player, 55);
        helper.assertTrue(QuadRocketLauncherItem.rounds(player.getMainHandItem()) == 2
                        && QuadRocketLauncherItem.loadedAmmo(player.getMainHandItem())
                        == RocketAmmoType.WHITE_PHOSPHORUS
                        && countAmmo(player, RocketAmmoType.WHITE_PHOSPHORUS) == 0
                        && countAmmo(player, RocketAmmoType.DEMOLITION) == 4,
                "an empty rack must choose inventory-first ammo and fill only from that identity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void quadroLaunchesAPlayerGuidedFourHundredTickRocket(GameTestHelper helper) {
        Player player = armed(helper, RocketAmmoType.SHAPED_CHARGE, 4, true);
        ItemStack gun = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        List<RocketProjectileEntity> rockets = projectiles(helper, player);
        helper.assertTrue(rockets.size() == 1, "one trigger pull must launch one physical rocket");
        RocketProjectileEntity rocket = rockets.getFirst();
        helper.assertTrue(rocket.getOwner() == player
                        && rocket.ammoType() == RocketAmmoType.SHAPED_CHARGE
                        && rocket.damage() == 20.0F
                        && rocket.speed() == 0.0F
                        && rocket.flightMode() == RocketProjectileEntity.FlightMode.PLAYER_GUIDED
                        && rocket.lifeTicks() == 400
                        && QuadRocketLauncherItem.rounds(gun) == 3
                        && QuadRocketLauncherItem.timer(gun) == 10,
                "Quadro HEAT must launch at 40 x .5 damage with its 400-tick steering profile");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aimedQuadroRocketSnapsToTheViewRayAndStopsAtSourceSpeed(GameTestHelper helper) {
        Player player = armed(helper, RocketAmmoType.HIGH_EXPLOSIVE, 1, true);
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        RocketProjectileEntity rocket = projectiles(helper, player).getFirst();
        Vec3 initial = rocket.direction();

        player.setYRot(-90.0F);
        player.yRotO = -90.0F;
        rocket.tickCount++;
        rocket.tick();
        helper.assertTrue(rocket.speed() == 0.4F
                        && rocket.direction().x > 0.99D
                        && rocket.direction().z < initial.z,
                "while aimed inside 100 blocks, Quadro must snap its heading to the 200-block view ray");

        for (int i = 0; i < 15 && rocket.isAlive(); i++) {
            rocket.tickCount++;
            rocket.tick();
        }
        helper.assertTrue(rocket.isAlive() && Math.abs(rocket.speed() - 4.4F) < 0.0001F,
                "player-guided rockets must retain the source discrete 4.4 terminal acceleration");
        helper.succeed();
    }

    private static Player armed(GameTestHelper helper, RocketAmmoType ammo, int rounds,
                                boolean aiming) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_QUADRO.get());
        QuadRocketLauncherItem.setTestState(gun, QuadRocketLauncherItem.GunState.IDLE,
                0, rounds, ammo, 0.0F, aiming);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(Player player, int ticks) {
        QuadRocketLauncherItem item = (QuadRocketLauncherItem) player.getMainHandItem().getItem();
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
