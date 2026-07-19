package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.M2Item;
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
public final class M2GameTests {
    private M2GameTests() { }

    @GameTest(template = "empty")
    public static void fiftyCalIdentitiesRetainSourceProfiles(GameTestHelper helper) {
        for (FiftyCalAmmoType type : FiftyCalAmmoType.values()) {
            ItemStack carrier = type.createStack(type.secret()
                    ? ModItems.AMMO_SECRET.get() : ModItems.AMMO_STANDARD.get(), 1);
            helper.assertTrue(FiftyCalAmmoType.fromStack(carrier) == type,
                    type + " must survive its source ammo carrier components");
            helper.assertTrue(StandardAmmoTypes.fromLegacyMetadata(type.legacyBulletConfig()) == type,
                    type + " must retain a distinct projectile configuration");
        }
        helper.assertTrue(FiftyCalAmmoType.SOFT_POINT.legacyMetadata() == 33
                        && FiftyCalAmmoType.DEPLETED_URANIUM.legacyMetadata() == 37
                        && FiftyCalAmmoType.HIGH_EXPLOSIVE.legacyMetadata() == 83
                        && FiftyCalAmmoType.STARMETAL.legacyMetadata() == 94,
                "standard .50 BMG loads must retain their source EnumAmmo ordinals");
        helper.assertTrue(FiftyCalAmmoType.ARMOR_PIERCING.penetrates()
                        && FiftyCalAmmoType.ARMOR_PIERCING.armorThresholdNegation() == 17.5F
                        && FiftyCalAmmoType.DEPLETED_URANIUM.armorPiercing() == 0.25F
                        && FiftyCalAmmoType.HIGH_EXPLOSIVE.impactExplosionRadius() == 2.0F
                        && FiftyCalAmmoType.STARMETAL.wear() == 10.0F,
                "AP, DU, HE, and Starmetal must retain source penetration, blast, and wear values");
        helper.assertTrue(FiftyCalAmmoType.BLACK.spectral()
                        && FiftyCalAmmoType.BLACK.headshotMultiplier() == 3.0F
                        && FiftyCalAmmoType.EQUESTRIAN.spawnsBuildingOnImpact(),
                "the two hidden .50 BMG rounds must retain bypass and building impact hooks");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void m2RetainsSourceReceiverAndEmptyBelt(GameTestHelper helper) {
        M2Item gun = ModItems.GUN_M2.get();
        ItemStack stack = new ItemStack(gun);
        helper.assertTrue(gun.gunDurability() == 3_000.0F && gun.gunBeltFed() && gun.gunAutomatic()
                        && gun.gunCrosshair() == SednaCrosshair.L_CIRCLE
                        && M2Item.DRAW_TICKS == 10 && M2Item.FIRE_DELAY == 2
                        && M2Item.DRY_TICKS == 10 && M2Item.BASE_DAMAGE == 7.5F
                        && M2Item.INNATE_SPREAD == 0.005F,
                "M2 must retain the source 3000 durability, belt, timing, damage, and spread contract");
        helper.assertTrue(M2Item.beltCount(stack) == 0
                        && M2Item.loadedAmmo(stack) == FiftyCalAmmoType.FULL_METAL_JACKET,
                "M2 must spawn with an empty inventory belt and FMJ default identity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void m2FeedsFirstCompatibleStackAndRefires(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_M2.get());
        M2Item.setTestState(gun, M2Item.GunState.IDLE, 0,
                FiftyCalAmmoType.FULL_METAL_JACKET, 0.0F, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.getInventory().add(FiveFiveSixAmmoType.SOFT_POINT.createStack(ModItems.AMMO_STANDARD.get(), 3));
        player.getInventory().add(FiftyCalAmmoType.HOLLOW_POINT.createStack(ModItems.AMMO_STANDARD.get(), 2));
        player.getInventory().add(FiftyCalAmmoType.FULL_METAL_JACKET.createStack(ModItems.AMMO_STANDARD.get(), 4));

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        List<BulletEntity> first = bullets(helper);
        helper.assertTrue(first.size() == 1 && first.getFirst().ammoType() == FiftyCalAmmoType.HOLLOW_POINT
                        && first.getFirst().damage() == 11.25F
                        && countAmmo(player, FiftyCalAmmoType.HOLLOW_POINT) == 1
                        && countAmmo(player, FiftyCalAmmoType.FULL_METAL_JACKET) == 4
                        && M2Item.loadedAmmo(gun) == FiftyCalAmmoType.HOLLOW_POINT
                        && M2Item.beltCount(gun) == 1,
                "M2 must feed the first compatible .50 stack and fire 7.5 x 1.5 JHP damage");

        M2Item item = (M2Item) gun.getItem();
        item.inventoryTick(gun, player.level(), player, player.getInventory().selected, true);
        item.inventoryTick(gun, player.level(), player, player.getInventory().selected, true);
        helper.assertTrue(bullets(helper).size() == 2
                        && countAmmo(player, FiftyCalAmmoType.HOLLOW_POINT) == 0
                        && countAmmo(player, FiveFiveSixAmmoType.SOFT_POINT) == 3,
                "held M2 must refire after two ticks while ignoring other calibers");
        helper.succeed();
    }

    private static int countAmmo(Player player, SednaAmmoType type) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.AMMO_STANDARD.get()) && StandardAmmoTypes.fromStack(stack) == type) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static List<BulletEntity> bullets(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(BulletEntity.class,
                new AABB(helper.absolutePos(new BlockPos(-8, -8, -8))).inflate(128.0D));
    }
}
