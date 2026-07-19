package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.BrokenMaresLegItem;
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
public final class BrokenMaresLegGameTests {
    private BrokenMaresLegGameTests() { }

    @GameTest(template = "empty")
    public static void brokenMareslegUsesLegendaryNoWearConfiguration(GameTestHelper helper) {
        BrokenMaresLegItem item = ModItems.GUN_MARESLEG_BROKEN.get();
        ItemStack fresh = new ItemStack(item);
        helper.assertTrue(BrokenMaresLegItem.DURABILITY == 0
                        && BrokenMaresLegItem.DRAW_TICKS == 5
                        && BrokenMaresLegItem.INSPECT_TICKS == 39
                        && BrokenMaresLegItem.FIRE_DELAY == 20
                        && BrokenMaresLegItem.RELOAD_BEGIN_TICKS == 22
                        && BrokenMaresLegItem.RELOAD_CYCLE_TICKS == 10
                        && BrokenMaresLegItem.RELOAD_END_TICKS == 13
                        && BrokenMaresLegItem.JAM_TICKS == 24,
                "Broken Maresleg durability and action timing must match XFactory12ga");
        helper.assertTrue(BrokenMaresLegItem.CAPACITY == 6
                        && item.baseDamage() == 48.0F
                        && BrokenMaresLegItem.AMMO_SPREAD_MULTIPLIER == 1.15F
                        && BrokenMaresLegItem.DEFAULT_HIP_SPREAD == 0.025F
                        && item.recoilVertical() == 10.0F
                        && item.recoilHorizontalSigma() == 1.5F
                        && item.gunCrosshair() == SednaCrosshair.L_CIRCLE,
                "Broken Maresleg damage, tube, spread, recoil, and reticle must match the source");
        helper.assertTrue(!item.gunShowDurability()
                        && item.gunDurability() == 0.0F
                        && item.gunWear(fresh) == 0.0F,
                "The dura(0) NOWEAR gun must suppress the durability HUD component");
        helper.assertTrue(BrokenMaresLegItem.rounds(fresh) == 0
                        && BrokenMaresLegItem.loadedAmmo(fresh) == Shotgun12GaugeAmmoType.MAGNUM,
                "A fresh gun must be empty with the setDefaultAmmo(G12_MAGNUM) identity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void magnumFireIgnoresWearAndKeepsFullDamage(GameTestHelper helper) {
        // A high wear preset must not reduce damage: NOWEAR fire uses no wear multiplier.
        Player highWear = armedPlayer(helper, Shotgun12GaugeAmmoType.MAGNUM, 2, 3000.0F);
        double playerX = highWear.getX();
        SednaGunItem.handleInput(highWear, GunInput.PRIMARY);
        List<BulletEntity> pellets = bullets(helper, highWear);
        helper.assertTrue(pellets.size() == 4
                        && pellets.stream().allMatch(pellet -> pellet.damage() == 24.0F),
                "Magnum must spawn four 24-damage pellets (48 x 0.5) with no wear falloff");
        helper.assertTrue(pellets.stream().allMatch(
                        pellet -> Math.abs(pellet.getX() - (playerX - 0.1875D)) < 0.0001D),
                "Every hip-fired pellet must originate at the exact Maresleg side offset");
        ItemStack gun = highWear.getMainHandItem();
        helper.assertTrue(BrokenMaresLegItem.rounds(gun) == 1
                        && BrokenMaresLegItem.state(gun) == BrokenMaresLegItem.GunState.COOLDOWN
                        && BrokenMaresLegItem.timer(gun) == 20,
                "One shell is consumed per source cycle, entering the 20-tick cooldown");
        BrokenMaresLegItem item = ModItems.GUN_MARESLEG_BROKEN.get();
        helper.assertTrue(item.gunWear(gun) == 0.0F,
                "NOWEAR fire and dura(0) must never write wear onto the gun");

        // A zero-wear preset yields the identical damage, confirming wear is irrelevant.
        Player zeroWear = armedPlayer(helper, Shotgun12GaugeAmmoType.MAGNUM, 2, 0.0F);
        SednaGunItem.handleInput(zeroWear, GunInput.PRIMARY);
        helper.assertTrue(bullets(helper, zeroWear).stream()
                        .allMatch(pellet -> pellet.damage() == 24.0F),
                "Damage must be identical regardless of the wear preset");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tubeReloadLoadsOneShellPerCycleLocksTypeAndCanCancel(GameTestHelper helper) {
        Player player = armedPlayer(helper, Shotgun12GaugeAmmoType.FLECHETTE, 2, 0.0F);
        ItemStack gun = player.getMainHandItem();
        player.getInventory().add(Shotgun12GaugeAmmoType.SLUG.createStack(ModItems.AMMO_STANDARD.get(), 8));
        player.getInventory().add(Shotgun12GaugeAmmoType.FLECHETTE.createStack(ModItems.AMMO_STANDARD.get(), 8));

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(BrokenMaresLegItem.state(gun) == BrokenMaresLegItem.GunState.RELOADING
                        && BrokenMaresLegItem.timer(gun) == 22
                        && BrokenMaresLegItem.amountBeforeReload(gun) == 2,
                "Tube reload must begin with the source 22-tick opening phase");
        tickHeld(helper, player, 21);
        helper.assertTrue(BrokenMaresLegItem.rounds(gun) == 2 && BrokenMaresLegItem.timer(gun) == 1,
                "No shell may transfer before the opening phase ends");
        tickHeld(helper, player, 1);
        helper.assertTrue(BrokenMaresLegItem.rounds(gun) == 3
                        && BrokenMaresLegItem.timer(gun) == 10
                        && BrokenMaresLegItem.loadedAmmo(gun) == Shotgun12GaugeAmmoType.FLECHETTE,
                "The first transfer must retain the partially loaded tube's shell identity");

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        tickHeld(helper, player, 10);
        helper.assertTrue(BrokenMaresLegItem.rounds(gun) == 4
                        && BrokenMaresLegItem.state(gun) == BrokenMaresLegItem.GunState.DRAWING
                        && BrokenMaresLegItem.timer(gun) == 13
                        && countAmmo(player, Shotgun12GaugeAmmoType.SLUG) == 8,
                "Canceling must finish the current shell, preserve other loads, and enter the 13-tick close");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void equestrianTkrLoadIsNotAccepted(GameTestHelper helper) {
        // g12_equestrian_tkr is unavailable; keep the nine ordinary 12-gauge loads only.
        Shotgun12GaugeAmmoType[] types = Shotgun12GaugeAmmoType.values();
        helper.assertTrue(types.length == 9,
                "The accepted set must contain only the nine ordinary loads while TKR is unavailable");
        for (Shotgun12GaugeAmmoType type : types) {
            helper.assertTrue(!type.serializedName().contains("equestrian"),
                    "No Equestrian identity may appear among the accepted loads");
        }
        helper.assertTrue(StandardAmmoTypes.fromStack(
                        Shotgun12GaugeAmmoType.MAGNUM.createStack(ModItems.AMMO_STANDARD.get(), 1))
                        instanceof Shotgun12GaugeAmmoType,
                "Loadable 12-gauge ammo must resolve only to Shotgun12GaugeAmmoType");
        helper.succeed();
    }

    private static Player armedPlayer(GameTestHelper helper, Shotgun12GaugeAmmoType ammo,
                                      int rounds, float wear) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_MARESLEG_BROKEN.get());
        BrokenMaresLegItem.setTestState(gun, BrokenMaresLegItem.GunState.IDLE, 0, rounds, ammo, wear);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(GameTestHelper helper, Player player, int ticks) {
        BrokenMaresLegItem gun = (BrokenMaresLegItem) player.getMainHandItem().getItem();
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
