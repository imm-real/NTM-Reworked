package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.DoubleBarrelItem;
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
public final class DoubleBarrelGameTests {
    private DoubleBarrelGameTests() { }

    @GameTest(template = "empty")
    public static void tenGaugeLoadsKeepTheirSourceProfiles(GameTestHelper helper) {
        for (Shotgun10GaugeAmmoType type : Shotgun10GaugeAmmoType.values()) {
            ItemStack stack = type.createStack(ModItems.AMMO_STANDARD.get(), 4);
            helper.assertTrue(StandardAmmoTypes.fromStack(stack) == type
                            && StandardAmmoTypes.fromLegacyMetadata(type.legacyMetadata()) == type,
                    type + " must survive components and its old EnumAmmo ordinal");
        }
        helper.assertTrue(Shotgun10GaugeAmmoType.BUCKSHOT.legacyMetadata() == 78
                        && Shotgun10GaugeAmmoType.SHRAPNEL.legacyMetadata() == 79
                        && Shotgun10GaugeAmmoType.DEPLETED_URANIUM.legacyMetadata() == 80
                        && Shotgun10GaugeAmmoType.SLUG.legacyMetadata() == 81
                        && Shotgun10GaugeAmmoType.EXPLOSIVE.legacyMetadata() == 84,
                "10 gauge shells must keep source metadata 78-81 and 84");
        helper.assertTrue(Shotgun10GaugeAmmoType.SHRAPNEL.maxRicochets() == 15
                        && Shotgun10GaugeAmmoType.SHRAPNEL.ricochetAngle() == 90.0F
                        && Shotgun10GaugeAmmoType.DEPLETED_URANIUM.projectiles() == 10
                        && Shotgun10GaugeAmmoType.DEPLETED_URANIUM.damageMultiplier() == 0.25F
                        && Shotgun10GaugeAmmoType.DEPLETED_URANIUM.penetrates()
                        && !Shotgun10GaugeAmmoType.DEPLETED_URANIUM.penetrationDamageFalloff()
                        && Shotgun10GaugeAmmoType.DEPLETED_URANIUM.armorPiercing() == 0.2F,
                "Shrapnel and uranium buckshot must keep their ricochet and penetration profiles");
        helper.assertTrue(Shotgun10GaugeAmmoType.EXPLOSIVE.projectiles() == 10
                        && Shotgun10GaugeAmmoType.EXPLOSIVE.wear() == 3.0F
                        && Shotgun10GaugeAmmoType.EXPLOSIVE.tinyImpactExplosion()
                        && Shotgun10GaugeAmmoType.EXPLOSIVE.impactExplosionRange() == 1.5D
                        && Shotgun10GaugeAmmoType.EXPLOSIVE.tracerFullbright(),
                "Explosive buckshot must keep ten pellets, triple wear and tiny impact blasts");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void receiverKeepsTheOldClassicConfiguration(GameTestHelper helper) {
        DoubleBarrelItem gun = ModItems.GUN_DOUBLE_BARREL.get();
        ItemStack stack = new ItemStack(gun);
        helper.assertTrue(gun.gunDurability() == 1_000.0F && gun.gunCapacity() == 2
                        && gun.gunCrosshair() == SednaCrosshair.L_CIRCLE
                        && gun.gunAimFovMultiplier() == 0.67F
                        && gun.recoilVertical() == 10.0F
                        && gun.recoilHorizontalSigma() == 1.5F,
                "An Old Classic must keep its condition, two shells, aim and recoil");
        helper.assertTrue(DoubleBarrelItem.DRAW_TICKS == 10
                        && DoubleBarrelItem.INSPECT_TICKS == 39
                        && DoubleBarrelItem.FIRE_DELAY == 10
                        && DoubleBarrelItem.RELOAD_TICKS == 41
                        && DoubleBarrelItem.BASE_DAMAGE == 30.0F,
                "Draw, inspect, fire, reload and damage must match XFactory10ga");
        helper.assertTrue(DoubleBarrelItem.rounds(stack) == 0
                        && DoubleBarrelItem.loadedAmmo(stack) == Shotgun10GaugeAmmoType.BUCKSHOT,
                "A fresh double barrel must be empty with 10 gauge buckshot selected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eitherTriggerFiresOneShellAndEmptyStartsReload(GameTestHelper helper) {
        Player player = armed(helper, Shotgun10GaugeAmmoType.BUCKSHOT, 2, 0.0F);
        ItemStack gun = player.getMainHandItem();
        player.getInventory().add(Shotgun10GaugeAmmoType.BUCKSHOT.createStack(
                ModItems.AMMO_STANDARD.get(), 2));

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(bullets(helper, player).size() == 10
                        && DoubleBarrelItem.rounds(gun) == 1
                        && DoubleBarrelItem.timer(gun) == 10,
                "Primary must fire one ten-pellet shell and leave the other barrel loaded");
        tickHeld(player, DoubleBarrelItem.FIRE_DELAY);
        SednaGunItem.handleInput(player, GunInput.SECONDARY);
        helper.assertTrue(bullets(helper, player).size() == 20
                        && DoubleBarrelItem.rounds(gun) == 0,
                "Secondary must fire the second shell through the same receiver");
        tickHeld(player, DoubleBarrelItem.FIRE_DELAY);
        helper.assertTrue(DoubleBarrelItem.state(gun) == DoubleBarrelItem.GunState.RELOADING
                        && DoubleBarrelItem.timer(gun) == DoubleBarrelItem.RELOAD_TICKS
                        && DoubleBarrelItem.amountBeforeReload(gun) == 0,
                "The empty receiver must open itself for the source 41-tick reload");
        tickHeld(player, DoubleBarrelItem.RELOAD_TICKS);
        helper.assertTrue(DoubleBarrelItem.rounds(gun) == 2
                        && countAmmo(player, Shotgun10GaugeAmmoType.BUCKSHOT) == 0,
                "The break action must load both shells together");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void partialReloadStaysWithTheLoadedShellType(GameTestHelper helper) {
        Player player = armed(helper, Shotgun10GaugeAmmoType.DEPLETED_URANIUM, 1, 0.0F);
        ItemStack gun = player.getMainHandItem();
        player.getInventory().add(Shotgun10GaugeAmmoType.BUCKSHOT.createStack(
                ModItems.AMMO_STANDARD.get(), 4));

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(DoubleBarrelItem.state(gun) == DoubleBarrelItem.GunState.IDLE
                        && DoubleBarrelItem.animation(gun) == DoubleBarrelItem.GunAnimation.INSPECT
                        && countAmmo(player, Shotgun10GaugeAmmoType.BUCKSHOT) == 4,
                "A uranium barrel must reject ordinary buckshot and inspect instead");

        player.getInventory().add(Shotgun10GaugeAmmoType.DEPLETED_URANIUM.createStack(
                ModItems.AMMO_STANDARD.get(), 2));
        SednaGunItem.handleInput(player, GunInput.RELOAD);
        tickHeld(player, DoubleBarrelItem.RELOAD_TICKS);
        helper.assertTrue(DoubleBarrelItem.rounds(gun) == 2
                        && DoubleBarrelItem.loadedAmmo(gun) == Shotgun10GaugeAmmoType.DEPLETED_URANIUM
                        && countAmmo(player, Shotgun10GaugeAmmoType.DEPLETED_URANIUM) == 1
                        && countAmmo(player, Shotgun10GaugeAmmoType.BUCKSHOT) == 4,
                "A partial reload must consume only one matching uranium shell");
        helper.succeed();
    }

    private static Player armed(GameTestHelper helper, Shotgun10GaugeAmmoType ammo,
                                int rounds, float wear) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_DOUBLE_BARREL.get());
        DoubleBarrelItem.setTestState(gun, DoubleBarrelItem.GunState.IDLE, 0, rounds,
                ammo, wear, false, DoubleBarrelItem.GunAnimation.CYCLE);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(Player player, int ticks) {
        DoubleBarrelItem gun = (DoubleBarrelItem) player.getMainHandItem().getItem();
        for (int i = 0; i < ticks; i++) {
            gun.inventoryTick(player.getMainHandItem(), player.level(), player,
                    player.getInventory().selected, true);
        }
    }

    private static List<BulletEntity> bullets(GameTestHelper helper, Player owner) {
        return helper.getLevel().getEntitiesOfClass(BulletEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(64.0D),
                bullet -> bullet.getOwner() == owner);
    }

    private static int countAmmo(Player player, Shotgun10GaugeAmmoType ammo) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.AMMO_STANDARD.get())
                    && StandardAmmoTypes.fromStack(stack) == ammo) count += stack.getCount();
        }
        return count;
    }
}
