package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.G3Item;
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
public final class G3GameTests {
    private G3GameTests() { }

    @GameTest(template = "empty")
    public static void sourceAmmoIdentitiesAndBallisticsAreStable(GameTestHelper helper) {
        for (FiveFiveSixAmmoType type : FiveFiveSixAmmoType.values()) {
            ItemStack stack = type.createStack(ModItems.AMMO_STANDARD.get(), 8);
            helper.assertTrue(FiveFiveSixAmmoType.fromStack(stack) == type,
                    type + " must survive component serialization");
            helper.assertTrue(StandardAmmoTypes.fromStack(stack) == type,
                    type + " must remain distinct in the shared ammo carrier");
            helper.assertTrue(StandardAmmoTypes.fromLegacyMetadata(type.legacyMetadata()) == type,
                    type + " must retain its source ammo_standard metadata");
        }
        helper.assertTrue(FiveFiveSixAmmoType.SOFT_POINT.legacyMetadata() == 24
                        && FiveFiveSixAmmoType.ARMOR_PIERCING.legacyMetadata() == 27,
                "5.56 mm metadata must retain source EnumAmmo ordinals 24-27");
        helper.assertTrue(FiveFiveSixAmmoType.FULL_METAL_JACKET.damageMultiplier() == 0.8F
                        && FiveFiveSixAmmoType.FULL_METAL_JACKET.armorThresholdNegation() == 4.0F
                        && FiveFiveSixAmmoType.HOLLOW_POINT.headshotMultiplier() == 1.5F
                        && FiveFiveSixAmmoType.HOLLOW_POINT.armorPiercing() == -0.25F,
                "5.56 FMJ and JHP must retain source damage and armor behavior");
        helper.assertTrue(FiveFiveSixAmmoType.ARMOR_PIERCING.penetrates()
                        && !FiveFiveSixAmmoType.ARMOR_PIERCING.penetrationDamageFalloff()
                        && FiveFiveSixAmmoType.ARMOR_PIERCING.damageMultiplier() == 1.25F
                        && FiveFiveSixAmmoType.ARMOR_PIERCING.armorThresholdNegation() == 10.0F,
                "5.56 AP must retain source penetration, damage, and threshold behavior");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void receiverKeepsSourceConfigurationAndSpawnsEmpty(GameTestHelper helper) {
        G3Item gun = ModItems.GUN_G3.get();
        ItemStack stack = new ItemStack(gun);
        helper.assertTrue(gun.gunDurability() == 3_000.0F && gun.gunCapacity() == 30
                        && gun.gunAutomatic() && gun.recoilVertical() == 0.0F
                        && gun.recoilVerticalSigma() == 0.25F && gun.recoilHorizontalSigma() == 0.25F,
                "G3 must retain source durability, capacity, automatic receiver, and gaussian recoil");
        helper.assertTrue(G3Item.DRAW_TICKS == 10 && G3Item.INSPECT_TICKS == 33
                        && G3Item.FIRE_DELAY == 2 && G3Item.DRY_TICKS == 15
                        && G3Item.RELOAD_TICKS == 50 && G3Item.JAM_TICKS == 47
                        && G3Item.BASE_DAMAGE == 5.0F,
                "G3 must retain source draw/inspect/fire/dry/reload/jam timing and base damage");
        helper.assertTrue(G3Item.rounds(stack) == 0
                        && G3Item.loadedAmmo(stack) == FiveFiveSixAmmoType.SOFT_POINT
                        && G3Item.mode(stack) == 0,
                "Fresh G3 must spawn empty with Soft Point selected and automatic mode active");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void selectorControlsAutomaticRefireWithoutBlockingFirstShot(GameTestHelper helper) {
        Player automatic = armed(helper, FiveFiveSixAmmoType.HOLLOW_POINT, 3, 0, 2);
        SednaGunItem.handleInput(automatic, GunInput.PRIMARY);
        tickHeld(automatic, G3Item.FIRE_DELAY);
        helper.assertTrue(ownerShots(bullets(helper), automatic) == 2
                        && G3Item.rounds(automatic.getMainHandItem()) == 1,
                "Mode zero must refire HOLLOW_POINT every two ticks while primary remains held");

        Player semi = armed(helper, FiveFiveSixAmmoType.ARMOR_PIERCING, 3, 1, 7);
        SednaGunItem.handleInput(semi, GunInput.PRIMARY);
        tickHeld(semi, G3Item.FIRE_DELAY);
        BulletEntity shot = ownerShot(bullets(helper), semi);
        helper.assertTrue(ownerShots(bullets(helper), semi) == 1
                        && shot.ammoType() == FiveFiveSixAmmoType.ARMOR_PIERCING
                        && shot.damage() == 6.25F && G3Item.rounds(semi.getMainHandItem()) == 2
                        && G3Item.state(semi.getMainHandItem()) == G3Item.GunState.IDLE,
                "Mode one must fire the first 5 x 1.25 AP shot but suppress held-trigger refire");
        SednaGunItem.handleInput(semi, GunInput.SECONDARY);
        helper.assertTrue(G3Item.mode(semi.getMainHandItem()) == 0,
                "Secondary press while idle must return the selector to automatic mode");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullReloadLocksPartialMagazineIdentity(GameTestHelper helper) {
        Player player = armed(helper, FiveFiveSixAmmoType.FULL_METAL_JACKET, 2, 0, 2);
        player.getInventory().add(FiveFiveSixAmmoType.SOFT_POINT.createStack(ModItems.AMMO_STANDARD.get(), 30));
        player.getInventory().add(FiveFiveSixAmmoType.FULL_METAL_JACKET.createStack(ModItems.AMMO_STANDARD.get(), 28));
        ItemStack gun = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(G3Item.amountBeforeReload(gun) == 2 && G3Item.timer(gun) == 50,
                "G3 must begin its source fifty-tick full reload from two rounds");
        tickHeld(player, G3Item.RELOAD_TICKS);
        helper.assertTrue(G3Item.rounds(gun) == 30
                        && G3Item.loadedAmmo(gun) == FiveFiveSixAmmoType.FULL_METAL_JACKET
                        && countAmmo(player, FiveFiveSixAmmoType.SOFT_POINT) == 30
                        && countAmmo(player, FiveFiveSixAmmoType.FULL_METAL_JACKET) == 0,
                "Partial G3 reload must consume only its existing FMJ identity up to thirty rounds");
        helper.succeed();
    }

    private static Player armed(GameTestHelper helper, FiveFiveSixAmmoType ammo,
                                int rounds, int mode, int x) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_G3.get());
        G3Item.setTestState(gun, G3Item.GunState.IDLE, 0, rounds, ammo, 0.0F, false, mode);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(x, 3, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(Player player, int ticks) {
        G3Item gun = (G3Item) player.getMainHandItem().getItem();
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

    private static int ownerShots(List<BulletEntity> bullets, Player owner) {
        int count = 0;
        for (BulletEntity bullet : bullets) if (bullet.getOwner() == owner) count++;
        return count;
    }

    private static BulletEntity ownerShot(List<BulletEntity> bullets, Player owner) {
        return bullets.stream().filter(bullet -> bullet.getOwner() == owner).findFirst()
                .orElseThrow(() -> new AssertionError("Expected a G3 bullet owned by test player"));
    }

    private static int countAmmo(Player player, FiveFiveSixAmmoType type) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.AMMO_STANDARD.get()) && StandardAmmoTypes.fromStack(stack) == type) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
