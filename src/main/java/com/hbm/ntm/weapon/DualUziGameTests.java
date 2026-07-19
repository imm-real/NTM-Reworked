package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.DualUziItem;
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

import java.util.Comparator;
import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DualUziGameTests {
    private DualUziGameTests() { }

    @GameTest(template = "empty")
    public static void sourceConfigurationAndSeparateDefaultAmmoContainerArePreserved(
            GameTestHelper helper) {
        DualUziItem gun = ModItems.GUN_UZI_AKIMBO.get();
        ItemStack fresh = new ItemStack(gun);
        helper.assertTrue(DualUziItem.DURABILITY == 3_000
                        && DualUziItem.DRAW_TICKS == 15
                        && DualUziItem.FIRE_DELAY == 2
                        && DualUziItem.DRY_TICKS == 25
                        && DualUziItem.RELOAD_TICKS == 55
                        && DualUziItem.JAM_TICKS == 50,
                "Dual Uzi timing and durability must match both XFactory9mm configs");
        helper.assertTrue(DualUziItem.CAPACITY == 30
                        && DualUziItem.BASE_DAMAGE == 3.0F
                        && DualUziItem.INNATE_SPREAD == 0.005F,
                "Each receiver must retain the source thirty-round damage and spread values");
        helper.assertTrue(gun.gunAutomatic() && gun.gunSecondaryAutomatic()
                        && gun.gunHasMirroredHud()
                        && gun.gunCrosshair() == SednaCrosshair.CIRCLE,
                "Both triggers must be automatic and config zero must expose the mirrored HUD");
        helper.assertTrue(DualUziItem.rounds(fresh, 0) == 0
                        && DualUziItem.rounds(fresh, 1) == 0
                        && DualUziItem.loadedAmmo(fresh, 0) == NineMillimeterAmmoType.SOFT_POINT
                        && DualUziItem.loadedAmmo(fresh, 1) == NineMillimeterAmmoType.SOFT_POINT,
                "Fresh weapon magazines must stay empty while the separate default container remains 60 SP");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void primaryAndSecondaryFireIndependentReceiversAndOffsets(GameTestHelper helper) {
        Player player = armedPlayer(helper, 2, 2);
        ItemStack gun = player.getMainHandItem();
        double playerX = player.getX();

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(DualUziItem.rounds(gun, 0) == 1
                        && DualUziItem.rounds(gun, 1) == 2
                        && DualUziItem.state(gun, 0) == DualUziItem.GunState.COOLDOWN
                        && DualUziItem.state(gun, 1) == DualUziItem.GunState.IDLE,
                "Primary must fire only source config zero");

        SednaGunItem.handleInput(player, GunInput.SECONDARY);
        helper.assertTrue(DualUziItem.rounds(gun, 0) == 1
                        && DualUziItem.rounds(gun, 1) == 1
                        && DualUziItem.state(gun, 1) == DualUziItem.GunState.COOLDOWN,
                "Secondary must fire only source config one");

        List<BulletEntity> bullets = bullets(helper, player).stream()
                .sorted(Comparator.comparingDouble(BulletEntity::getX)).toList();
        helper.assertTrue(bullets.size() == 2,
                "One press on each trigger must spawn exactly two bullets");
        helper.assertTrue(Math.abs(bullets.get(0).getX() - (playerX - 0.375D)) < 0.0001D
                        && Math.abs(bullets.get(1).getX() - (playerX + 0.375D)) < 0.0001D,
                "The two receivers must use their exact opposite 0.375-block source offsets");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void heldTriggersRefireAndReleaseIndependently(GameTestHelper helper) {
        Player player = armedPlayer(helper, 3, 3);
        ItemStack gun = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        SednaGunItem.handleInput(player, GunInput.SECONDARY);
        helper.assertTrue(DualUziItem.rounds(gun, 0) == 2
                        && DualUziItem.rounds(gun, 1) == 2
                        && DualUziItem.triggerHeld(gun, 0)
                        && DualUziItem.triggerHeld(gun, 1),
                "Both initial presses must store their own held state");

        SednaGunItem.handleInput(player, GunInput.PRIMARY_RELEASE);
        tickHeld(helper, player, 2);
        helper.assertTrue(DualUziItem.rounds(gun, 0) == 2
                        && DualUziItem.state(gun, 0) == DualUziItem.GunState.IDLE
                        && !DualUziItem.triggerHeld(gun, 0),
                "Primary release must stop only config zero after its current cycle");
        helper.assertTrue(DualUziItem.rounds(gun, 1) == 1
                        && DualUziItem.state(gun, 1) == DualUziItem.GunState.COOLDOWN
                        && DualUziItem.triggerHeld(gun, 1),
                "Held secondary must independently refire config one on tick two");

        SednaGunItem.handleInput(player, GunInput.SECONDARY_RELEASE);
        tickHeld(helper, player, 2);
        helper.assertTrue(DualUziItem.rounds(gun, 1) == 1
                        && DualUziItem.state(gun, 1) == DualUziItem.GunState.IDLE
                        && !DualUziItem.triggerHeld(gun, 1),
                "Secondary release must stop only config one");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reloadRunsBothHomogeneousMagazinesInSourceOrder(GameTestHelper helper) {
        Player player = armedPlayer(helper, 2, 0);
        ItemStack gun = player.getMainHandItem();
        DualUziItem.setTestState(gun, 0, DualUziItem.GunState.IDLE, 0, 2,
                NineMillimeterAmmoType.FULL_METAL_JACKET, 0.0F, false);
        player.getInventory().add(NineMillimeterAmmoType.SOFT_POINT.createStack(
                ModItems.AMMO_STANDARD.get(), 30));
        player.getInventory().add(NineMillimeterAmmoType.FULL_METAL_JACKET.createStack(
                ModItems.AMMO_STANDARD.get(), 28));

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(DualUziItem.state(gun, 0) == DualUziItem.GunState.RELOADING
                        && DualUziItem.state(gun, 1) == DualUziItem.GunState.RELOADING
                        && DualUziItem.timer(gun, 0) == 55
                        && DualUziItem.timer(gun, 1) == 55,
                "One reload press must start both independent 55-tick full-mag actions");
        tickHeld(helper, player, 54);
        helper.assertTrue(DualUziItem.rounds(gun, 0) == 2
                        && DualUziItem.rounds(gun, 1) == 0,
                "Neither magazine may transfer ammunition before its final tick");
        tickHeld(helper, player, 1);
        helper.assertTrue(DualUziItem.rounds(gun, 0) == 30
                        && DualUziItem.loadedAmmo(gun, 0)
                        == NineMillimeterAmmoType.FULL_METAL_JACKET,
                "A partial config-zero magazine must keep its existing ammunition identity");
        helper.assertTrue(DualUziItem.rounds(gun, 1) == 30
                        && DualUziItem.loadedAmmo(gun, 1) == NineMillimeterAmmoType.SOFT_POINT,
                "An empty config-one magazine must select the first remaining compatible load");

        Player scarce = armedPlayer(helper, 0, 0);
        ItemStack scarceGun = scarce.getMainHandItem();
        scarce.getInventory().add(NineMillimeterAmmoType.HOLLOW_POINT.createStack(
                ModItems.AMMO_STANDARD.get(), 1));
        SednaGunItem.handleInput(scarce, GunInput.RELOAD);
        tickHeld(helper, scarce, 55);
        helper.assertTrue(DualUziItem.rounds(scarceGun, 0) == 1
                        && DualUziItem.rounds(scarceGun, 1) == 0,
                "When ammo is scarce, config zero must consume first just like the source update order");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dryFireDoesNotCrossOrRepeatBetweenReceivers(GameTestHelper helper) {
        Player player = armedPlayer(helper, 0, 0);
        ItemStack gun = player.getMainHandItem();
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(DualUziItem.animation(gun, 0) == DualUziItem.GunAnimation.CYCLE_DRY
                        && DualUziItem.state(gun, 0) == DualUziItem.GunState.DRAWING
                        && DualUziItem.timer(gun, 0) == 25
                        && DualUziItem.state(gun, 1) == DualUziItem.GunState.IDLE,
                "An empty primary click must dry-fire only config zero");
        SednaGunItem.handleInput(player, GunInput.SECONDARY);
        helper.assertTrue(DualUziItem.animation(gun, 1) == DualUziItem.GunAnimation.CYCLE_DRY
                        && DualUziItem.state(gun, 1) == DualUziItem.GunState.DRAWING
                        && DualUziItem.timer(gun, 1) == 25,
                "An empty secondary click must dry-fire only config one");
        tickHeld(helper, player, 25);
        helper.assertTrue(DualUziItem.state(gun, 0) == DualUziItem.GunState.IDLE
                        && DualUziItem.state(gun, 1) == DualUziItem.GunState.IDLE
                        && DualUziItem.rounds(gun, 0) == 0
                        && DualUziItem.rounds(gun, 1) == 0,
                "Held empty automatic receivers must not repeat their dry click");
        helper.succeed();
    }

    private static Player armedPlayer(GameTestHelper helper, int leftRounds, int rightRounds) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_UZI_AKIMBO.get());
        DualUziItem.setTestState(gun, 0, DualUziItem.GunState.IDLE, 0, leftRounds,
                NineMillimeterAmmoType.SOFT_POINT, 0.0F, false);
        DualUziItem.setTestState(gun, 1, DualUziItem.GunState.IDLE, 0, rightRounds,
                NineMillimeterAmmoType.SOFT_POINT, 0.0F, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(GameTestHelper helper, Player player, int ticks) {
        DualUziItem gun = (DualUziItem) player.getMainHandItem().getItem();
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
}
