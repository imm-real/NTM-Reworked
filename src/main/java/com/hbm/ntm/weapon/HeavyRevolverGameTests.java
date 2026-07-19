package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.HeavyRevolverItem;
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
public final class HeavyRevolverGameTests {
    private HeavyRevolverGameTests() { }

    @GameTest(template = "empty")
    public static void sourceConfigurationIsPreserved(GameTestHelper helper) {
        HeavyRevolverItem gun = ModItems.GUN_HEAVY_REVOLVER.get();
        helper.assertTrue(HeavyRevolverItem.DURABILITY == 600
                        && HeavyRevolverItem.DRAW_TICKS == 10
                        && HeavyRevolverItem.FIRE_DELAY == 14
                        && HeavyRevolverItem.RELOAD_TICKS == 46
                        && HeavyRevolverItem.JAM_TICKS == 23,
                "Heavy Revolver timing and durability must match XFactory44");
        helper.assertTrue(HeavyRevolverItem.CAPACITY == 6 && HeavyRevolverItem.BASE_DAMAGE == 15.0F,
                "Heavy Revolver must retain its six-shot cylinder and 15 base damage");
        helper.assertTrue(gun.gunCrosshair() == SednaCrosshair.L_CLASSIC
                        && gun.recoilVertical() == 10.0F && gun.recoilHorizontalSigma() == 1.5F,
                "Heavy Revolver must retain the large classic reticle and NoPip recoil");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fireUsesFortyFourAmmoAndFourteenTickCycle(GameTestHelper helper) {
        Player player = armedPlayer(helper, Magnum44AmmoType.HOLLOW_POINT, 2, 0.0F);
        ItemStack stack = player.getMainHandItem();
        SednaGunItem.handleInput(player, GunInput.PRIMARY);

        helper.assertTrue(HeavyRevolverItem.rounds(stack) == 1
                        && HeavyRevolverItem.timer(stack) == 14
                        && HeavyRevolverItem.animation(stack) == HeavyRevolverItem.GunAnimation.CYCLE,
                "A shot must consume one round and start the 14-tick NoPip cycle");
        List<BulletEntity> bullets = bullets(helper, player);
        helper.assertTrue(bullets.size() == 1 && bullets.getFirst().damage() == 22.5F,
                ".44 JHP must apply its 1.5 multiplier to the 15-damage receiver");

        tickHeld(helper, player, 13);
        helper.assertTrue(HeavyRevolverItem.state(stack) == HeavyRevolverItem.GunState.COOLDOWN,
                "The revolver must remain locked through cycle tick thirteen");
        tickHeld(helper, player, 1);
        helper.assertTrue(HeavyRevolverItem.state(stack) == HeavyRevolverItem.GunState.IDLE,
                "The revolver must return to idle on cycle tick fourteen");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullReloadWaitsAndKeepsCylinderHomogeneous(GameTestHelper helper) {
        Player player = armedPlayer(helper, Magnum44AmmoType.FULL_METAL_JACKET, 2, 0.0F);
        player.getInventory().add(Magnum44AmmoType.SOFT_POINT.createStack(ModItems.AMMO_STANDARD.get(), 8));
        player.getInventory().add(Magnum44AmmoType.FULL_METAL_JACKET.createStack(ModItems.AMMO_STANDARD.get(), 8));
        ItemStack gun = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(HeavyRevolverItem.state(gun) == HeavyRevolverItem.GunState.RELOADING
                        && HeavyRevolverItem.timer(gun) == 46,
                "Reload must start the source 46-tick full-cylinder action");
        tickHeld(helper, player, 45);
        helper.assertTrue(HeavyRevolverItem.rounds(gun) == 2 && HeavyRevolverItem.timer(gun) == 1,
                "Full reload must not transfer ammunition before its final tick");
        tickHeld(helper, player, 1);
        helper.assertTrue(HeavyRevolverItem.rounds(gun) == 6
                        && HeavyRevolverItem.loadedAmmo(gun) == Magnum44AmmoType.FULL_METAL_JACKET,
                "A partial cylinder must accept only its existing .44 identity");
        helper.assertTrue(countAmmo(player, Magnum44AmmoType.FULL_METAL_JACKET) == 4,
                "Reload must consume exactly four compatible rounds");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wearCurvesScaleToSixHundredDurability(GameTestHelper helper) {
        helper.assertTrue(HeavyRevolverItem.jamChance(396.0F) == 0.0F
                        && HeavyRevolverItem.jamChance(546.0F) == 1.0F,
                "Jamming must begin after 66 percent and reach certainty at 91 percent wear");
        helper.assertTrue(HeavyRevolverItem.wearDamageMultiplier(450.0F) == 1.0F
                        && HeavyRevolverItem.wearDamageMultiplier(600.0F) == 0.5F,
                "Damage must remain full through 75 percent wear and fall to half at maximum");
        helper.assertTrue(HeavyRevolverItem.wearSpread(300.0F) == 0.0F
                        && HeavyRevolverItem.wearSpread(600.0F) == 0.125F,
                "Wear spread must rise from zero at half wear to 0.125 at maximum");
        helper.succeed();
    }

    private static Player armedPlayer(GameTestHelper helper, Magnum44AmmoType ammo, int rounds, float wear) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_HEAVY_REVOLVER.get());
        HeavyRevolverItem.setTestState(gun, HeavyRevolverItem.GunState.IDLE, 0, rounds, ammo, wear);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(GameTestHelper helper, Player player, int ticks) {
        HeavyRevolverItem gun = (HeavyRevolverItem) player.getMainHandItem().getItem();
        for (int i = 0; i < ticks; i++) {
            gun.inventoryTick(player.getMainHandItem(), helper.getLevel(), player,
                    player.getInventory().selected, true);
        }
    }

    private static int countAmmo(Player player, Magnum44AmmoType type) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.AMMO_STANDARD.get()) && Magnum44AmmoType.fromStack(stack) == type) {
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
