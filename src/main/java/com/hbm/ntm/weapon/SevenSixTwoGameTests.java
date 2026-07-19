package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.SednaGunItem;
import com.hbm.ntm.item.SevenSixTwoGunItem;
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
public final class SevenSixTwoGameTests {
    private SevenSixTwoGameTests() { }

    @GameTest(template = "empty")
    public static void sourceAmmoIdentitiesAndBallisticsAreStable(GameTestHelper helper) {
        for (SevenSixTwoAmmoType type : SevenSixTwoAmmoType.values()) {
            ItemStack stack = type.createStack(ModItems.AMMO_STANDARD.get(), 8);
            helper.assertTrue(SevenSixTwoAmmoType.fromStack(stack) == type,
                    type + " must survive component serialization");
            helper.assertTrue(StandardAmmoTypes.fromStack(stack) == type,
                    type + " must remain distinct in the shared ammo carrier");
            helper.assertTrue(StandardAmmoTypes.fromLegacyMetadata(type.legacyMetadata()) == type,
                    type + " must retain its source ammo_standard metadata");
        }
        helper.assertTrue(SevenSixTwoAmmoType.SOFT_POINT.legacyMetadata() == 28
                        && SevenSixTwoAmmoType.DEPLETED_URANIUM.legacyMetadata() == 32
                        && SevenSixTwoAmmoType.HIGH_EXPLOSIVE.legacyMetadata() == 82,
                "7.62 mm metadata must retain the source EnumAmmo ordinals, including HE at 82");
        helper.assertTrue(SevenSixTwoAmmoType.FULL_METAL_JACKET.damageMultiplier() == 0.8F
                        && SevenSixTwoAmmoType.FULL_METAL_JACKET.armorThresholdNegation() == 5.0F
                        && SevenSixTwoAmmoType.FULL_METAL_JACKET.armorPiercing() == 0.1F,
                "7.62 FMJ must retain source damage and armor behavior");
        helper.assertTrue(SevenSixTwoAmmoType.ARMOR_PIERCING.penetrates()
                        && !SevenSixTwoAmmoType.ARMOR_PIERCING.penetrationDamageFalloff()
                        && SevenSixTwoAmmoType.DEPLETED_URANIUM.armorThresholdNegation() == 15.0F
                        && SevenSixTwoAmmoType.HIGH_EXPLOSIVE.impactExplosionRadius() == 1.5F
                        && SevenSixTwoAmmoType.HIGH_EXPLOSIVE.wear() == 3.0F,
                "AP, DU, and HE must retain source penetration, explosion, and wear values");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void receiversKeepSourceConfigurationAndSpawnEmpty(GameTestHelper helper) {
        SevenSixTwoGunItem carbine = ModItems.GUN_CARBINE.get();
        SevenSixTwoGunItem minigun = ModItems.GUN_MINIGUN.get();
        SevenSixTwoGunItem mas36 = ModItems.GUN_MAS36.get();
        helper.assertTrue(carbine.gunDurability() == 3_000.0F && carbine.gunCapacity() == 14
                        && carbine.baseDamage() == 15.0F && carbine.variant().drawTicks() == 10
                        && carbine.variant().fireDelay() == 5 && carbine.variant().reloadTicks() == 30
                        && carbine.variant().reloadEndTicks() == 15 && carbine.variant().jamTicks() == 60,
                "Carbine must retain the source 3000/14/15 and 10/5/30/15/60 receiver values");
        helper.assertTrue(minigun.gunDurability() == 50_000.0F && minigun.gunBeltFed()
                        && minigun.gunAutomatic() && minigun.baseDamage() == 6.0F
                        && minigun.variant().drawTicks() == 20 && minigun.variant().fireDelay() == 1
                        && minigun.variant().innateSpread() == 0.01F,
                "Minigun must retain its source inventory belt and one-tick automatic receiver");
        helper.assertTrue(mas36.gunDurability() == 5_000.0F && mas36.gunCapacity() == 7
                        && mas36.baseDamage() == 30.0F && mas36.variant().drawTicks() == 20
                        && mas36.variant().fireDelay() == 25 && mas36.variant().reloadTicks() == 43
                        && mas36.variant().jamTicks() == 43,
                "MAS-36 must retain the source 5000/7/30 and 20/25/43/43 receiver values");

        ItemStack carbineStack = new ItemStack(carbine);
        ItemStack minigunStack = new ItemStack(minigun);
        ItemStack masStack = new ItemStack(mas36);
        helper.assertTrue(SevenSixTwoGunItem.rounds(carbineStack) == 0
                        && SevenSixTwoGunItem.loadedAmmo(carbineStack) == SevenSixTwoAmmoType.SOFT_POINT,
                "Carbine must spawn empty with Soft Point selected");
        helper.assertTrue(SevenSixTwoGunItem.beltCount(minigunStack) == 0
                        && SevenSixTwoGunItem.loadedAmmo(minigunStack) == SevenSixTwoAmmoType.FULL_METAL_JACKET,
                "Minigun must spawn with an empty inventory belt and FMJ identity");
        helper.assertTrue(SevenSixTwoGunItem.rounds(masStack) == 0
                        && SevenSixTwoGunItem.loadedAmmo(masStack) == SevenSixTwoAmmoType.ARMOR_PIERCING,
                "MAS-36 must spawn empty with AP selected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void carbineAndMas36FireSourceDamage(GameTestHelper helper) {
        Player carbine = armed(helper, SevenSixTwoGunItem.Variant.CARBINE,
                SevenSixTwoAmmoType.HOLLOW_POINT, 1, 2);
        Player mas36 = armed(helper, SevenSixTwoGunItem.Variant.MAS36,
                SevenSixTwoAmmoType.ARMOR_PIERCING, 1, 7);
        SednaGunItem.handleInput(carbine, GunInput.PRIMARY);
        SednaGunItem.handleInput(mas36, GunInput.PRIMARY);

        BulletEntity carbineShot = ownerShot(bullets(helper), carbine);
        BulletEntity masShot = ownerShot(bullets(helper), mas36);
        helper.assertTrue(carbineShot.ammoType() == SevenSixTwoAmmoType.HOLLOW_POINT
                        && carbineShot.damage() == 22.5F
                        && SevenSixTwoGunItem.rounds(carbine.getMainHandItem()) == 0
                        && SevenSixTwoGunItem.timer(carbine.getMainHandItem()) == 5,
                "Carbine JHP must fire 15 x 1.5 damage and enter its five-tick cycle");
        helper.assertTrue(masShot.ammoType() == SevenSixTwoAmmoType.ARMOR_PIERCING
                        && masShot.damage() == 37.5F
                        && SevenSixTwoGunItem.rounds(mas36.getMainHandItem()) == 0
                        && SevenSixTwoGunItem.timer(mas36.getMainHandItem()) == 25,
                "MAS-36 AP must fire 30 x 1.25 damage and enter its twenty-five-tick cycle");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void minigunFeedsFirstLooseAmmoStackAndRefiresEachTick(GameTestHelper helper) {
        Player player = armed(helper, SevenSixTwoGunItem.Variant.MINIGUN,
                SevenSixTwoAmmoType.FULL_METAL_JACKET, 0, 2);
        player.getInventory().add(Magnum357AmmoType.SOFT_POINT.createStack(ModItems.AMMO_STANDARD.get(), 3));
        player.getInventory().add(SevenSixTwoAmmoType.HOLLOW_POINT.createStack(ModItems.AMMO_STANDARD.get(), 2));
        player.getInventory().add(SevenSixTwoAmmoType.FULL_METAL_JACKET.createStack(ModItems.AMMO_STANDARD.get(), 4));
        ItemStack minigun = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(countAmmo(player, SevenSixTwoAmmoType.HOLLOW_POINT) == 1
                        && countAmmo(player, SevenSixTwoAmmoType.FULL_METAL_JACKET) == 4
                        && SevenSixTwoGunItem.loadedAmmo(minigun) == SevenSixTwoAmmoType.HOLLOW_POINT
                        && SevenSixTwoGunItem.beltCount(minigun) == 1
                        && SevenSixTwoGunItem.timer(minigun) == 1,
                "Minigun must feed and consume the first compatible loose 7.62 stack");
        tickHeld(player, 1);
        helper.assertTrue(countAmmo(player, SevenSixTwoAmmoType.HOLLOW_POINT) == 0
                        && countAmmo(player, SevenSixTwoAmmoType.FULL_METAL_JACKET) == 4
                        && bullets(helper).size() == 2,
                "Held Minigun must refire its selected loose-ammo identity at the one-tick boundary");
        tickHeld(player, 1);
        helper.assertTrue(countAmmo(player, SevenSixTwoAmmoType.FULL_METAL_JACKET) == 3
                        && SevenSixTwoGunItem.loadedAmmo(minigun) == SevenSixTwoAmmoType.FULL_METAL_JACKET
                        && bullets(helper).size() == 3,
                "Once the first stack empties, MagazineBelt must continue with the next accepted identity");
        helper.assertTrue(countAmmo(player, Magnum357AmmoType.SOFT_POINT) == 3,
                "Minigun must ignore other ammo_standard calibers");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullReloadsLockPartialMagazineIdentity(GameTestHelper helper) {
        Player carbine = armed(helper, SevenSixTwoGunItem.Variant.CARBINE,
                SevenSixTwoAmmoType.FULL_METAL_JACKET, 2, 2);
        carbine.getInventory().add(SevenSixTwoAmmoType.SOFT_POINT.createStack(ModItems.AMMO_STANDARD.get(), 14));
        carbine.getInventory().add(SevenSixTwoAmmoType.FULL_METAL_JACKET.createStack(ModItems.AMMO_STANDARD.get(), 12));
        ItemStack carbineStack = carbine.getMainHandItem();
        SednaGunItem.handleInput(carbine, GunInput.RELOAD);
        helper.assertTrue(SevenSixTwoGunItem.amountBeforeReload(carbineStack) == 2
                        && SevenSixTwoGunItem.timer(carbineStack) == 30,
                "Carbine must begin its source thirty-tick full reload from two rounds");
        tickHeld(carbine, 30);
        helper.assertTrue(SevenSixTwoGunItem.rounds(carbineStack) == 14
                        && SevenSixTwoGunItem.loadedAmmo(carbineStack) == SevenSixTwoAmmoType.FULL_METAL_JACKET
                        && countAmmo(carbine, SevenSixTwoAmmoType.SOFT_POINT) == 14
                        && SevenSixTwoGunItem.state(carbineStack) == SevenSixTwoGunItem.GunState.DRAWING
                        && SevenSixTwoGunItem.timer(carbineStack) == 15,
                "Carbine partial reload must consume only FMJ before its fifteen-tick reload end");

        Player mas36 = armed(helper, SevenSixTwoGunItem.Variant.MAS36,
                SevenSixTwoAmmoType.ARMOR_PIERCING, 0, 7);
        mas36.getInventory().add(SevenSixTwoAmmoType.DEPLETED_URANIUM.createStack(ModItems.AMMO_STANDARD.get(), 7));
        ItemStack masStack = mas36.getMainHandItem();
        SednaGunItem.handleInput(mas36, GunInput.RELOAD);
        tickHeld(mas36, 43);
        helper.assertTrue(SevenSixTwoGunItem.rounds(masStack) == 7
                        && SevenSixTwoGunItem.loadedAmmo(masStack) == SevenSixTwoAmmoType.DEPLETED_URANIUM,
                "An empty MAS-36 must select and fill from the first compatible 7.62 identity");
        helper.succeed();
    }

    private static Player armed(GameTestHelper helper, SevenSixTwoGunItem.Variant variant,
                                SevenSixTwoAmmoType ammo, int rounds, int x) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(switch (variant) {
            case CARBINE -> ModItems.GUN_CARBINE.get();
            case MINIGUN -> ModItems.GUN_MINIGUN.get();
            case MAS36 -> ModItems.GUN_MAS36.get();
        });
        SevenSixTwoGunItem.setTestState(gun, SevenSixTwoGunItem.GunState.IDLE,
                0, rounds, ammo, 0.0F, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(x, 3, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(Player player, int ticks) {
        SevenSixTwoGunItem gun = (SevenSixTwoGunItem) player.getMainHandItem().getItem();
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

    private static BulletEntity ownerShot(List<BulletEntity> shots, Player player) {
        return shots.stream().filter(shot -> shot.getOwner() == player).findFirst().orElseThrow();
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
