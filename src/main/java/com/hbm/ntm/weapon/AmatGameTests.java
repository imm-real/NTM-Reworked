package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.AmatItem;
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
public final class AmatGameTests {
    private AmatGameTests() { }

    @GameTest(template = "empty")
    public static void sourceFiftyCalLoadsRemainDistinct(GameTestHelper helper) {
        FiftyCalAmmoType[] standard = {
                FiftyCalAmmoType.SOFT_POINT, FiftyCalAmmoType.FULL_METAL_JACKET,
                FiftyCalAmmoType.HOLLOW_POINT, FiftyCalAmmoType.ARMOR_PIERCING,
                FiftyCalAmmoType.DEPLETED_URANIUM, FiftyCalAmmoType.STARMETAL,
                FiftyCalAmmoType.HIGH_EXPLOSIVE
        };
        for (FiftyCalAmmoType type : standard) {
            ItemStack stack = type.createStack(ModItems.AMMO_STANDARD.get(), 4);
            helper.assertTrue(FiftyCalAmmoType.fromStack(stack) == type,
                    type + " must survive component serialization");
            helper.assertTrue(StandardAmmoTypes.fromStack(stack) == type,
                    type + " must remain distinct in ammo_standard");
        }
        helper.assertTrue(FiftyCalAmmoType.SOFT_POINT.legacyMetadata() == 33
                        && FiftyCalAmmoType.DEPLETED_URANIUM.legacyMetadata() == 37
                        && FiftyCalAmmoType.HIGH_EXPLOSIVE.legacyMetadata() == 83
                        && FiftyCalAmmoType.STARMETAL.legacyMetadata() == 94,
                ".50 BMG metadata must retain the source EnumAmmo ordinals");
        helper.assertTrue(FiftyCalAmmoType.FULL_METAL_JACKET.damageMultiplier() == 0.8F
                        && FiftyCalAmmoType.FULL_METAL_JACKET.armorThresholdNegation() == 7.0F
                        && FiftyCalAmmoType.HOLLOW_POINT.headshotMultiplier() == 1.5F
                        && FiftyCalAmmoType.HOLLOW_POINT.armorPiercing() == -0.25F,
                ".50 BMG FMJ and JHP must retain source damage and armor behavior");
        helper.assertTrue(FiftyCalAmmoType.ARMOR_PIERCING.penetrates()
                        && !FiftyCalAmmoType.ARMOR_PIERCING.penetrationDamageFalloff()
                        && FiftyCalAmmoType.DEPLETED_URANIUM.armorThresholdNegation() == 21.0F
                        && FiftyCalAmmoType.STARMETAL.wear() == 10.0F
                        && FiftyCalAmmoType.HIGH_EXPLOSIVE.impactExplosionRadius() == 2.0F,
                "AP, DU, Starmetal, and HE must retain source penetration, wear, and blast values");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void receiverKeepsSourceConfigurationAndSpawnsEmpty(GameTestHelper helper) {
        AmatItem gun = ModItems.GUN_AMAT.get();
        ItemStack stack = new ItemStack(gun);
        helper.assertTrue(gun.gunDurability() == 350.0F && gun.gunCapacity() == 7
                        && !gun.gunAutomatic() && gun.gunAimFovMultiplier() == 0.2F
                        && gun.recoilVertical() == 12.5F && gun.recoilHorizontalSigma() == 1.0F,
                "AMAT must retain source durability, capacity, scope, and recoil");
        helper.assertTrue(AmatItem.DRAW_TICKS == 20 && AmatItem.INSPECT_TICKS == 50
                        && AmatItem.FIRE_DELAY == 25 && AmatItem.DRY_TICKS == 25
                        && AmatItem.RELOAD_TICKS == 51 && AmatItem.JAM_TICKS == 43
                        && AmatItem.BASE_DAMAGE == 30.0F && AmatItem.HIP_SPREAD == 0.05F,
                "AMAT must retain source draw/inspect/fire/dry/reload/jam timing and damage");
        helper.assertTrue(AmatItem.rounds(stack) == 0
                        && AmatItem.loadedAmmo(stack) == FiftyCalAmmoType.SOFT_POINT,
                "Fresh AMAT must spawn empty with Soft Point selected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void starmetalShotUsesSourceDamageWearAndDelay(GameTestHelper helper) {
        Player player = armed(helper, FiftyCalAmmoType.STARMETAL, 2, 2);
        ItemStack gun = player.getMainHandItem();
        SednaGunItem.handleInput(player, GunInput.PRIMARY);

        BulletEntity shot = bullets(helper).stream().filter(b -> b.getOwner() == player).findFirst()
                .orElseThrow(() -> new AssertionError("Expected an AMAT bullet owned by test player"));
        helper.assertTrue(shot.ammoType() == FiftyCalAmmoType.STARMETAL && shot.damage() == 75.0F,
                "AMAT Starmetal must deal 30 x 2.5 damage");
        helper.assertTrue(AmatItem.rounds(gun) == 1 && AmatItem.wear(gun) == 10.0F
                        && AmatItem.state(gun) == AmatItem.GunState.COOLDOWN
                        && AmatItem.timer(gun) == 25,
                "A Starmetal shot must consume one round, add ten wear, and start the 25-tick cycle");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullReloadLocksPartialMagazineIdentity(GameTestHelper helper) {
        Player player = armed(helper, FiftyCalAmmoType.FULL_METAL_JACKET, 2, 4);
        player.getInventory().add(FiftyCalAmmoType.SOFT_POINT.createStack(
                ModItems.AMMO_STANDARD.get(), 7));
        player.getInventory().add(FiftyCalAmmoType.FULL_METAL_JACKET.createStack(
                ModItems.AMMO_STANDARD.get(), 5));
        ItemStack gun = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(AmatItem.amountBeforeReload(gun) == 2 && AmatItem.timer(gun) == 51,
                "AMAT must begin its source 51-tick full reload from two rounds");
        tickHeld(player, AmatItem.RELOAD_TICKS);
        helper.assertTrue(AmatItem.rounds(gun) == 7
                        && AmatItem.loadedAmmo(gun) == FiftyCalAmmoType.FULL_METAL_JACKET
                        && countAmmo(player, FiftyCalAmmoType.SOFT_POINT) == 7
                        && countAmmo(player, FiftyCalAmmoType.FULL_METAL_JACKET) == 0,
                "Partial AMAT reload must consume only its existing FMJ identity up to seven rounds");
        helper.succeed();
    }

    private static Player armed(GameTestHelper helper, FiftyCalAmmoType ammo, int rounds, int x) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_AMAT.get());
        AmatItem.setTestState(gun, AmatItem.GunState.IDLE, 0, rounds, ammo, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(x, 3, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(Player player, int ticks) {
        AmatItem gun = (AmatItem) player.getMainHandItem().getItem();
        for (int i = 0; i < ticks; i++) {
            gun.inventoryTick(player.getMainHandItem(), player.level(), player,
                    player.getInventory().selected, true);
        }
    }

    private static List<BulletEntity> bullets(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntitiesOfClass(BulletEntity.class,
                new AABB(origin).inflate(64.0D));
    }

    private static int countAmmo(Player player, FiftyCalAmmoType type) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.AMMO_STANDARD.get()) && StandardAmmoTypes.fromStack(stack) == type) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
