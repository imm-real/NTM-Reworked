package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.HenryRifleItem;
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
public final class HenryRifleGameTests {
    private HenryRifleGameTests() { }

    @GameTest(template = "empty")
    public static void magnum44VariantsPreserveSourceIdentityAndStats(GameTestHelper helper) {
        for (Magnum44AmmoType type : Magnum44AmmoType.values()) {
            ItemStack stack = type.createStack(ModItems.AMMO_STANDARD.get(), 8);
            helper.assertTrue(Magnum44AmmoType.fromStack(stack) == type,
                    type + " must survive component serialization");
            helper.assertTrue(type.legacyMetadata() >= 10 && type.legacyMetadata() <= 15,
                    type + " must preserve legacy ammo_standard metadata");
            helper.assertTrue(type.legacyBulletConfig() == type.legacyMetadata() + 2,
                    type + " must preserve its source BulletConfig index");
        }
        helper.assertTrue(Magnum44AmmoType.BLACK_POWDER.blackPowder()
                        && Magnum44AmmoType.BLACK_POWDER.damageMultiplier() == 0.75F,
                "The .44 black-powder load must retain its effect and damage multiplier");
        helper.assertTrue(Magnum44AmmoType.FULL_METAL_JACKET.armorThresholdNegation() == 3.0F,
                ".44 FMJ must retain three points of threshold negation");
        helper.assertTrue(Magnum44AmmoType.ARMOR_PIERCING.penetrates()
                        && !Magnum44AmmoType.ARMOR_PIERCING.penetrationDamageFalloff()
                        && Magnum44AmmoType.ARMOR_PIERCING.armorThresholdNegation() == 7.5F,
                ".44 AP must penetrate without falloff and retain 7.5 DT negation");
        helper.assertTrue(Magnum44AmmoType.EXPRESS.penetrates()
                        && Magnum44AmmoType.EXPRESS.wear() == 1.5F,
                ".44 Express must penetrate and apply 1.5 wear per shot");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void henryAndLincolnUseSourceDamageCapacityAndCycle(GameTestHelper helper) {
        Player henryPlayer = armedPlayer(helper, false, Magnum44AmmoType.SOFT_POINT, 14, 0.0F, 2);
        Player lincolnPlayer = armedPlayer(helper, true, Magnum44AmmoType.HOLLOW_POINT, 14, 0.0F, 8);
        HenryRifleItem henry = (HenryRifleItem) henryPlayer.getMainHandItem().getItem();
        HenryRifleItem lincoln = (HenryRifleItem) lincolnPlayer.getMainHandItem().getItem();

        SednaGunItem.handleInput(henryPlayer, GunInput.PRIMARY);
        SednaGunItem.handleInput(lincolnPlayer, GunInput.PRIMARY);
        List<BulletEntity> henryBullets = bullets(helper, henryPlayer);
        List<BulletEntity> lincolnBullets = bullets(helper, lincolnPlayer);
        helper.assertTrue(henry.baseDamage() == 10.0F && henry.gunCapacity() == 14
                        && HenryRifleItem.rounds(henryPlayer.getMainHandItem()) == 13
                        && HenryRifleItem.timer(henryPlayer.getMainHandItem()) == 20,
                "Henry must use 10 damage, a 14-round tube, and a 20-tick cycle");
        helper.assertTrue(henryBullets.size() == 1 && henryBullets.getFirst().damage() == 10.0F,
                "Henry Soft Point must create one ten-damage bullet");
        helper.assertTrue(lincoln.isLincoln() && lincoln.baseDamage() == 20.0F
                        && lincolnBullets.size() == 1 && lincolnBullets.getFirst().damage() == 30.0F,
                "Lincoln's JHP load must apply its 1.5 multiplier to 20 base damage");

        tickHeld(helper, henryPlayer, 19);
        helper.assertTrue(HenryRifleItem.state(henryPlayer.getMainHandItem()) == HenryRifleItem.GunState.COOLDOWN,
                "Henry must remain locked through cycle tick nineteen");
        tickHeld(helper, henryPlayer, 1);
        helper.assertTrue(HenryRifleItem.state(henryPlayer.getMainHandItem()) == HenryRifleItem.GunState.IDLE,
                "Henry must return to idle on cycle tick twenty");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tubeReloadLoadsOneRoundPerSourceCycleAndCanCancel(GameTestHelper helper) {
        Player player = armedPlayer(helper, false, Magnum44AmmoType.FULL_METAL_JACKET, 2, 0.0F, 2);
        player.getInventory().add(Magnum44AmmoType.SOFT_POINT.createStack(ModItems.AMMO_STANDARD.get(), 8));
        player.getInventory().add(Magnum44AmmoType.FULL_METAL_JACKET.createStack(ModItems.AMMO_STANDARD.get(), 8));
        ItemStack gun = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(HenryRifleItem.state(gun) == HenryRifleItem.GunState.RELOADING
                        && HenryRifleItem.timer(gun) == 25 && HenryRifleItem.amountBeforeReload(gun) == 2,
                "Tube reload must begin with the source 25-tick opening phase");
        tickHeld(helper, player, 24);
        helper.assertTrue(HenryRifleItem.rounds(gun) == 2 && HenryRifleItem.timer(gun) == 1,
                "No cartridge may transfer before the opening phase finishes");
        tickHeld(helper, player, 1);
        helper.assertTrue(HenryRifleItem.rounds(gun) == 3
                        && HenryRifleItem.timer(gun) == 11
                        && HenryRifleItem.animation(gun) == HenryRifleItem.GunAnimation.RELOAD_CYCLE,
                "The opening phase must insert one round and enter the 11-tick repeat cycle");
        helper.assertTrue(HenryRifleItem.loadedAmmo(gun) == Magnum44AmmoType.FULL_METAL_JACKET,
                "A partially loaded tube must reject other .44 identities");

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        tickHeld(helper, player, 11);
        helper.assertTrue(HenryRifleItem.rounds(gun) == 4
                        && HenryRifleItem.state(gun) == HenryRifleItem.GunState.DRAWING
                        && HenryRifleItem.timer(gun) == 14,
                "Canceling must finish the current cartridge and then play the 14-tick closing phase");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reloadFromEmptyAddsTheEightTickLeverCock(GameTestHelper helper) {
        Player player = armedPlayer(helper, false, Magnum44AmmoType.SOFT_POINT, 0, 0.0F, 2);
        player.getInventory().add(PepperboxAmmoType.STONE.createStack(ModItems.AMMO_STANDARD.get(), 2));
        player.getInventory().add(Magnum357AmmoType.SOFT_POINT.createStack(ModItems.AMMO_STANDARD.get(), 2));
        player.getInventory().add(Magnum44AmmoType.SOFT_POINT.createStack(ModItems.AMMO_STANDARD.get(), 1));
        ItemStack gun = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        tickHeld(helper, player, 25);
        helper.assertTrue(HenryRifleItem.rounds(gun) == 1
                        && HenryRifleItem.loadedAmmo(gun) == Magnum44AmmoType.SOFT_POINT
                        && HenryRifleItem.state(gun) == HenryRifleItem.GunState.DRAWING
                        && HenryRifleItem.timer(gun) == 22,
                "An empty tube must add eight cocking ticks to the 14-tick reload end");
        helper.assertTrue(countAmmo(player, PepperboxAmmoType.STONE) == 2
                        && countAmmo(player, Magnum357AmmoType.SOFT_POINT) == 2,
                "Henry must not consume identically-fallbacked ammo_standard rounds from other calibers");
        helper.succeed();
    }

    private static Player armedPlayer(GameTestHelper helper, boolean lincoln, Magnum44AmmoType ammo,
                                      int rounds, float wear, int x) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(lincoln ? ModItems.GUN_HENRY_LINCOLN.get() : ModItems.GUN_HENRY.get());
        HenryRifleItem.setTestState(gun, HenryRifleItem.GunState.IDLE, 0, rounds, ammo, wear);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(x, 2, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(GameTestHelper helper, Player player, int ticks) {
        HenryRifleItem gun = (HenryRifleItem) player.getMainHandItem().getItem();
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
