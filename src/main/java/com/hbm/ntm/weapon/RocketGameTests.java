package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.RocketProjectileEntity;
import com.hbm.ntm.item.RocketLauncherItem;
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
public final class RocketGameTests {
    private RocketGameTests() { }

    @GameTest(template = "empty")
    public static void sourceRocketIdentitiesAndProfilesRemainStable(GameTestHelper helper) {
        for (RocketAmmoType ammo : RocketAmmoType.values()) {
            ItemStack stack = ammo.createStack(ModItems.AMMO_STANDARD.get(), 2);
            helper.assertTrue(RocketAmmoType.fromStack(stack) == ammo
                            && StandardAmmoTypes.fromStack(stack) == ammo
                            && StandardAmmoTypes.fromLegacyMetadata(ammo.legacyMetadata()) == ammo,
                    ammo + " must preserve its source EnumAmmo identity");
        }
        float terminalSpeed = 0.0F;
        for (int tick = 0; tick < 30; tick++) {
            terminalSpeed = RocketAmmoType.HIGH_EXPLOSIVE.accelerate(terminalSpeed);
        }
        helper.assertTrue(RocketAmmoType.HIGH_EXPLOSIVE.legacyMetadata() == 58
                        && RocketAmmoType.WHITE_PHOSPHORUS.legacyMetadata() == 62
                        && RocketAmmoType.LIFE_TICKS == 300
                        && RocketAmmoType.SELF_DAMAGE_DELAY == 10
                        && RocketAmmoType.ACCELERATION_PER_TICK == 0.4F
                        && RocketAmmoType.ACCELERATION_THRESHOLD == 7.0F
                        && RocketAmmoType.MAX_ACCELERATION == 7.2F
                        && Math.abs(terminalSpeed - 7.2F) < 1.0E-4F,
                "rocket metadata and the MK4 flight constants must match XFactoryRocket");
        helper.assertTrue(RocketAmmoType.HIGH_EXPLOSIVE.damageMultiplier() == 1.0F
                        && RocketAmmoType.HIGH_EXPLOSIVE.impactExplosionRadius() == 5.0F
                        && RocketAmmoType.SHAPED_CHARGE.damageMultiplier() == 0.5F
                        && RocketAmmoType.SHAPED_CHARGE.impactExplosionRadius() == 3.5F
                        && RocketAmmoType.DEMOLITION.destroysBlocks()
                        && !RocketAmmoType.HIGH_EXPLOSIVE.destroysBlocks()
                        && RocketAmmoType.INCENDIARY.lingeringTicks() == 300
                        && RocketAmmoType.WHITE_PHOSPHORUS.lingeringTicks() == 600,
                "HE, HEAT, demolition, incendiary, and WP impact profiles must remain distinct");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void panzerschreckKeepsSourceReceiverAndStartsEmpty(GameTestHelper helper) {
        RocketLauncherItem gun = ModItems.GUN_PANZERSCHRECK.get();
        ItemStack stack = new ItemStack(gun);
        helper.assertTrue(gun.gunDurability() == 300.0F && gun.gunCapacity() == 1
                        && RocketLauncherItem.BASE_DAMAGE == 25.0F
                        && RocketLauncherItem.DRAW_TICKS == 7
                        && RocketLauncherItem.FIRE_DELAY == 5
                        && RocketLauncherItem.RELOAD_TICKS == 50
                        && RocketLauncherItem.JAM_TICKS == 40
                        && !gun.gunAutomatic()
                        && gun.gunCrosshair() == SednaCrosshair.L_CIRCUMFLEX,
                "Panzerschreck must retain the source 300/1/25/7/5/50/40 receiver");
        helper.assertTrue(RocketLauncherItem.rounds(stack) == 0
                        && RocketLauncherItem.loadedAmmo(stack) == RocketAmmoType.HIGH_EXPLOSIVE,
                "source default ammo selects Rocket HE without preloading the single-round magazine");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void panzerschreckLaunchesTheAcceleratingSourceRocket(GameTestHelper helper) {
        Player player = armed(helper, RocketAmmoType.SHAPED_CHARGE, 1);
        ItemStack gun = player.getMainHandItem();
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        List<RocketProjectileEntity> shots = projectiles(helper, player);
        helper.assertTrue(shots.size() == 1, "one trigger press must launch one physical rocket");
        RocketProjectileEntity shot = shots.getFirst();
        helper.assertTrue(shot.getOwner() == player
                        && shot.ammoType() == RocketAmmoType.SHAPED_CHARGE
                        && shot.damage() == 12.5F
                        && shot.speed() == 0.0F
                        && RocketLauncherItem.rounds(gun) == 0
                        && RocketLauncherItem.timer(gun) == 5,
                "HEAT must launch at 25 x .5 damage, consume the round, and enter five-tick cooldown");
        Vec3 launchPosition = shot.position();
        shot.tick();
        helper.assertTrue(shot.position().distanceToSqr(launchPosition) < 1.0E-8D
                        && shot.speed() == 0.4F,
                "MK4 rockets must begin stationary and gain .4 velocity after their first movement step");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void singleReloadUsesTheFirstRocketIdentityAndConsumesOne(GameTestHelper helper) {
        Player player = armed(helper, RocketAmmoType.HIGH_EXPLOSIVE, 0);
        ItemStack demolition = RocketAmmoType.DEMOLITION.createStack(ModItems.AMMO_STANDARD.get(), 2);
        ItemStack highExplosive = RocketAmmoType.HIGH_EXPLOSIVE.createStack(ModItems.AMMO_STANDARD.get(), 2);
        player.getInventory().add(demolition);
        player.getInventory().add(highExplosive);

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(RocketLauncherItem.state(player.getMainHandItem())
                        == RocketLauncherItem.GunState.RELOADING
                        && RocketLauncherItem.timer(player.getMainHandItem()) == 50,
                "an empty launcher with rocket ammunition must enter its fifty-tick reload");
        tickHeld(player, 50);
        helper.assertTrue(RocketLauncherItem.rounds(player.getMainHandItem()) == 1
                        && RocketLauncherItem.loadedAmmo(player.getMainHandItem()) == RocketAmmoType.DEMOLITION
                        && countAmmo(player, RocketAmmoType.DEMOLITION) == 1
                        && countAmmo(player, RocketAmmoType.HIGH_EXPLOSIVE) == 2,
                "MagazineSingleReload must select inventory-first ammo and consume exactly one matching rocket");
        helper.succeed();
    }

    private static Player armed(GameTestHelper helper, RocketAmmoType ammo, int rounds) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_PANZERSCHRECK.get());
        RocketLauncherItem.setTestState(gun, RocketLauncherItem.GunState.IDLE,
                0, rounds, ammo, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(Player player, int ticks) {
        RocketLauncherItem item = (RocketLauncherItem) player.getMainHandItem().getItem();
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
