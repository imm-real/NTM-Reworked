package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.SednaGunItem;
import com.hbm.ntm.item.Stg77Item;
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
public final class Stg77GameTests {
    private Stg77GameTests() { }

    @GameTest(template = "empty")
    public static void receiverKeepsSourceConfigurationAndSpawnsEmpty(GameTestHelper helper) {
        Stg77Item gun = ModItems.GUN_STG77.get();
        ItemStack stack = new ItemStack(gun);
        helper.assertTrue(gun.gunDurability() == 3_000.0F && gun.gunCapacity() == 30
                        && !gun.gunAutomatic() && gun.gunSecondaryAutomatic()
                        && gun.gunAimFovMultiplier() == 0.34F
                        && gun.gunScopeTexture() != null
                        && gun.gunScopeTexture().getPath().equals("textures/misc/scope_bolt.png"),
                "StG 77 must retain its source durability, magazine, split triggers, and bolt scope");
        helper.assertTrue(gun.recoilVertical() == 0.0F && gun.recoilVerticalSigma() == 0.0F
                        && gun.recoilHorizontalSigma() == 0.0F,
                "StG 77's source recoil callback must remain empty");
        helper.assertTrue(Stg77Item.DRAW_TICKS == 10 && Stg77Item.INSPECT_TICKS == 125
                        && Stg77Item.FIRE_DELAY == 2 && Stg77Item.DRY_TICKS == 15
                        && Stg77Item.RELOAD_TICKS == 46 && Stg77Item.JAM_TICKS == 0
                        && Stg77Item.BASE_DAMAGE == 10.0F,
                "StG 77 must retain source draw/inspect/fire/dry/reload/jam timing and damage");
        helper.assertTrue(Stg77Item.rounds(stack) == 0
                        && Stg77Item.loadedAmmo(stack) == FiveFiveSixAmmoType.FULL_METAL_JACKET,
                "Fresh StG 77 must spawn empty with FMJ selected as the source default ammo identity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void primaryIsSemiAutomaticAndSecondaryRefiresWhileHeld(GameTestHelper helper) {
        Player primary = armed(helper, FiveFiveSixAmmoType.ARMOR_PIERCING, 3, 2);
        SednaGunItem.handleInput(primary, GunInput.PRIMARY);
        tickHeld(primary, Stg77Item.FIRE_DELAY);
        BulletEntity primaryShot = ownerShot(bullets(helper), primary);
        helper.assertTrue(ownerShots(bullets(helper), primary) == 1
                        && primaryShot.ammoType() == FiveFiveSixAmmoType.ARMOR_PIERCING
                        && primaryShot.damage() == 12.5F
                        && Stg77Item.rounds(primary.getMainHandItem()) == 2
                        && Stg77Item.state(primary.getMainHandItem()) == Stg77Item.GunState.IDLE,
                "Primary must fire one 10 x 1.25 AP shot without held-trigger refire");

        Player secondary = armed(helper, FiveFiveSixAmmoType.HOLLOW_POINT, 3, 7);
        SednaGunItem.handleInput(secondary, GunInput.SECONDARY);
        tickHeld(secondary, Stg77Item.FIRE_DELAY);
        helper.assertTrue(ownerShots(bullets(helper), secondary) == 2
                        && Stg77Item.rounds(secondary.getMainHandItem()) == 1
                        && Stg77Item.secondaryHeld(secondary.getMainHandItem()),
                "Secondary must fire immediately and refire every two ticks while held");
        SednaGunItem.handleInput(secondary, GunInput.SECONDARY_RELEASE);
        tickHeld(secondary, Stg77Item.FIRE_DELAY);
        helper.assertTrue(ownerShots(bullets(helper), secondary) == 2
                        && !Stg77Item.secondaryHeld(secondary.getMainHandItem())
                        && Stg77Item.state(secondary.getMainHandItem()) == Stg77Item.GunState.IDLE,
                "Secondary release must stop the automatic refire loop");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void scopeToggleAndFullReloadKeepSourceMagazineIdentity(GameTestHelper helper) {
        Player player = armed(helper, FiveFiveSixAmmoType.FULL_METAL_JACKET, 2, 2);
        player.getInventory().add(FiveFiveSixAmmoType.SOFT_POINT.createStack(ModItems.AMMO_STANDARD.get(), 30));
        player.getInventory().add(FiveFiveSixAmmoType.FULL_METAL_JACKET.createStack(
                ModItems.AMMO_STANDARD.get(), 28));
        ItemStack gun = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.TOGGLE_AIM);
        helper.assertTrue(Stg77Item.aiming(gun), "Middle-mouse aim input must toggle the StG 77 scope");
        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(!Stg77Item.aiming(gun)
                        && Stg77Item.amountBeforeReload(gun) == 2
                        && Stg77Item.timer(gun) == Stg77Item.RELOAD_TICKS,
                "Reload must lower the scope and start the source forty-six-tick full reload");
        tickHeld(player, Stg77Item.RELOAD_TICKS);
        helper.assertTrue(Stg77Item.rounds(gun) == 30
                        && Stg77Item.loadedAmmo(gun) == FiveFiveSixAmmoType.FULL_METAL_JACKET
                        && countAmmo(player, FiveFiveSixAmmoType.SOFT_POINT) == 30
                        && countAmmo(player, FiveFiveSixAmmoType.FULL_METAL_JACKET) == 0,
                "Partial StG 77 reload must consume only its existing FMJ identity up to thirty rounds");
        helper.succeed();
    }

    private static Player armed(GameTestHelper helper, FiveFiveSixAmmoType ammo, int rounds, int x) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_STG77.get());
        Stg77Item.setTestState(gun, Stg77Item.GunState.IDLE, 0, rounds, ammo, 0.0F, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(x, 3, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(Player player, int ticks) {
        Stg77Item gun = (Stg77Item) player.getMainHandItem().getItem();
        for (int i = 0; i < ticks; i++) {
            gun.inventoryTick(player.getMainHandItem(), player.level(), player,
                    player.getInventory().selected, true);
        }
    }

    private static List<BulletEntity> bullets(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntitiesOfClass(BulletEntity.class, new AABB(origin).inflate(64.0D));
    }

    private static int ownerShots(List<BulletEntity> bullets, Player owner) {
        int count = 0;
        for (BulletEntity bullet : bullets) if (bullet.getOwner() == owner) count++;
        return count;
    }

    private static BulletEntity ownerShot(List<BulletEntity> bullets, Player owner) {
        return bullets.stream().filter(bullet -> bullet.getOwner() == owner).findFirst()
                .orElseThrow(() -> new AssertionError("Expected a StG 77 bullet owned by test player"));
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
