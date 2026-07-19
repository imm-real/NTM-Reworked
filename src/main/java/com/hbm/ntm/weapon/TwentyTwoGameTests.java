package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.DualStarFItem;
import com.hbm.ntm.item.SednaGunItem;
import com.hbm.ntm.item.TwentyTwoGunItem;
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
public final class TwentyTwoGameTests {
    private TwentyTwoGameTests() { }

    @GameTest(template = "empty")
    public static void sourceAmmoIdentitiesAndBallisticsAreStable(GameTestHelper helper) {
        for (TwentyTwoAmmoType type : TwentyTwoAmmoType.values()) {
            ItemStack stack = type.createStack(ModItems.AMMO_STANDARD.get(), 24);
            helper.assertTrue(TwentyTwoAmmoType.fromStack(stack) == type,
                    type + " must survive component serialization");
            helper.assertTrue(StandardAmmoTypes.fromStack(stack) == type,
                    type + " must remain distinct in ammo_standard");
            helper.assertTrue(StandardAmmoTypes.fromLegacyMetadata(type.legacyMetadata()) == type,
                    type + " must retain its EnumAmmo ordinal");
        }
        helper.assertTrue(TwentyTwoAmmoType.SOFT_POINT.legacyMetadata() == 16
                        && TwentyTwoAmmoType.ARMOR_PIERCING.legacyMetadata() == 19
                        && TwentyTwoAmmoType.SOFT_POINT.legacyBulletConfig() == 18
                        && TwentyTwoAmmoType.ARMOR_PIERCING.legacyBulletConfig() == 21,
                ".22 LR metadata and BulletConfig IDs must retain the source numbering");
        helper.assertTrue(TwentyTwoAmmoType.FULL_METAL_JACKET.damageMultiplier() == 0.8F
                        && TwentyTwoAmmoType.FULL_METAL_JACKET.armorThresholdNegation() == 1.0F
                        && TwentyTwoAmmoType.HOLLOW_POINT.headshotMultiplier() == 1.5F
                        && TwentyTwoAmmoType.ARMOR_PIERCING.penetrates()
                        && !TwentyTwoAmmoType.ARMOR_PIERCING.penetrationDamageFalloff()
                        && TwentyTwoAmmoType.ARMOR_PIERCING.armorThresholdNegation() == 2.5F,
                ".22 LR damage and penetration values must match XFactory22lr");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void receiversKeepSourceConfigurationAndSpawnEmpty(GameTestHelper helper) {
        TwentyTwoGunItem am180 = ModItems.GUN_AM180.get();
        TwentyTwoGunItem star = ModItems.GUN_STAR_F.get();
        helper.assertTrue(am180.variant() == TwentyTwoGunItem.Variant.AM180
                        && am180.gunDurability() == 4_425.0F && am180.gunCapacity() == 177
                        && am180.baseDamage() == 2.0F && am180.gunAutomatic()
                        && am180.variant().fireDelay() == 1 && am180.variant().reloadTicks() == 66,
                "American-180 must retain its 4425/177/2 and one-tick receiver");
        helper.assertTrue(star.variant() == TwentyTwoGunItem.Variant.STAR_F
                        && star.gunDurability() == 375.0F && star.gunCapacity() == 15
                        && star.baseDamage() == 12.5F && !star.gunAutomatic()
                        && star.variant().fireDelay() == 5 && star.variant().reloadTicks() == 40,
                "Star F must retain its 375/15/12.5 and five-tick receiver");
        helper.assertTrue(TwentyTwoGunItem.rounds(new ItemStack(am180)) == 0
                        && TwentyTwoGunItem.rounds(new ItemStack(star)) == 0,
                "MagazineFullReload guns must spawn with empty weapon magazines");

        ItemStack dual = new ItemStack(ModItems.GUN_STAR_F_AKIMBO.get());
        helper.assertTrue(DualStarFItem.rounds(dual, 0) == 0
                        && DualStarFItem.rounds(dual, 1) == 0
                        && ModItems.GUN_STAR_F_AKIMBO.get().gunHasMirroredHud(),
                "Akimbo Star Fs must expose two empty independent receiver HUDs");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void am180AndStarFireSourceDamage(GameTestHelper helper) {
        Player amPlayer = armedSingle(helper, TwentyTwoGunItem.Variant.AM180,
                TwentyTwoAmmoType.HOLLOW_POINT, 2, 2);
        Player starPlayer = armedSingle(helper, TwentyTwoGunItem.Variant.STAR_F,
                TwentyTwoAmmoType.ARMOR_PIERCING, 1, 7);
        SednaGunItem.handleInput(amPlayer, GunInput.PRIMARY);
        SednaGunItem.handleInput(starPlayer, GunInput.PRIMARY);

        BulletEntity amShot = ownerShot(bullets(helper), amPlayer);
        BulletEntity starShot = ownerShot(bullets(helper), starPlayer);
        helper.assertTrue(amShot.ammoType() == TwentyTwoAmmoType.HOLLOW_POINT
                        && amShot.damage() == 3.0F
                        && TwentyTwoGunItem.rounds(amPlayer.getMainHandItem()) == 1
                        && TwentyTwoGunItem.timer(amPlayer.getMainHandItem()) == 1,
                "American-180 JHP must fire 2 x 1.5 damage on its one-tick cycle");
        helper.assertTrue(starShot.ammoType() == TwentyTwoAmmoType.ARMOR_PIERCING
                        && starShot.damage() == 15.625F
                        && TwentyTwoGunItem.rounds(starPlayer.getMainHandItem()) == 0
                        && TwentyTwoGunItem.timer(starPlayer.getMainHandItem()) == 5,
                "Star F AP must fire 12.5 x 1.25 damage on its five-tick cycle");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void akimboReceiversFireAndReloadIndependently(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack dual = new ItemStack(ModItems.GUN_STAR_F_AKIMBO.get());
        DualStarFItem.setTestState(dual, 0, DualStarFItem.GunState.IDLE,
                0, 1, TwentyTwoAmmoType.SOFT_POINT, 0.0F);
        DualStarFItem.setTestState(dual, 1, DualStarFItem.GunState.IDLE,
                0, 1, TwentyTwoAmmoType.HOLLOW_POINT, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, dual);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2))));

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        SednaGunItem.handleInput(player, GunInput.SECONDARY);
        helper.assertTrue(DualStarFItem.rounds(dual, 0) == 0
                        && DualStarFItem.rounds(dual, 1) == 0
                        && bullets(helper).size() == 2,
                "Primary and secondary must fire the two Star F receivers independently");
        List<BulletEntity> shots = bullets(helper);
        helper.assertTrue(shots.stream().anyMatch(shot -> shot.ammoType() == TwentyTwoAmmoType.SOFT_POINT)
                        && shots.stream().anyMatch(shot -> shot.ammoType() == TwentyTwoAmmoType.HOLLOW_POINT),
                "Each akimbo receiver must preserve its own loaded ammo identity");

        player.getInventory().add(TwentyTwoAmmoType.FULL_METAL_JACKET
                .createStack(ModItems.AMMO_STANDARD.get(), 20));
        tickDual(player, FIRE_AND_DRAW_TICKS);
        SednaGunItem.handleInput(player, GunInput.RELOAD);
        tickDual(player, DualStarFItem.RELOAD_TICKS);
        helper.assertTrue(DualStarFItem.rounds(dual, 0) == 15
                        && DualStarFItem.rounds(dual, 1) == 5
                        && DualStarFItem.loadedAmmo(dual, 0) == TwentyTwoAmmoType.FULL_METAL_JACKET
                        && DualStarFItem.loadedAmmo(dual, 1) == TwentyTwoAmmoType.FULL_METAL_JACKET,
                "Akimbo full reload must fill each receiver in order from the shared loose stack");
        helper.succeed();
    }

    private static final int FIRE_AND_DRAW_TICKS = DualStarFItem.FIRE_DELAY;

    private static Player armedSingle(GameTestHelper helper, TwentyTwoGunItem.Variant variant,
                                      TwentyTwoAmmoType ammo, int rounds, int x) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(variant == TwentyTwoGunItem.Variant.AM180
                ? ModItems.GUN_AM180.get() : ModItems.GUN_STAR_F.get());
        TwentyTwoGunItem.setTestState(gun, TwentyTwoGunItem.GunState.IDLE,
                0, rounds, ammo, 0.0F, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(x, 3, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickDual(Player player, int ticks) {
        DualStarFItem gun = (DualStarFItem) player.getMainHandItem().getItem();
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
}
