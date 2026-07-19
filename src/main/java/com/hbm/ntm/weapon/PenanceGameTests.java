package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.PenanceItem;
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
public final class PenanceGameTests {
    private PenanceGameTests() { }

    @GameTest(template = "empty")
    public static void receiverKeepsSourceConfigurationAndSpawnsEmpty(GameTestHelper helper) {
        PenanceItem gun = ModItems.GUN_AMAT_PENANCE.get();
        ItemStack stack = new ItemStack(gun);
        helper.assertTrue(gun.gunDurability() == 5_000.0F && gun.gunCapacity() == 7
                        && gun.gunAimFovMultiplier() == 0.2F
                        && gun.recoilVertical() == 12.5F && gun.recoilHorizontalSigma() == 1.0F,
                "Penance must retain its durability, seven-round magazine, thermal scope zoom and recoil");
        helper.assertTrue(gun.gunScopeTexture() != null
                        && gun.gunScopeTexture().getPath().equals("textures/misc/scope_penance.png"),
                "Penance must use the authored thermal scope overlay");
        helper.assertTrue(PenanceItem.rounds(stack) == 0
                        && PenanceItem.loadedAmmo(stack) == FiftyCalAmmoType.HOLLOW_POINT,
                "Fresh Penance must spawn empty with JHP selected");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "penance_black_round_isolated")
    public static void blackRoundFiresAsTheSourceSpectralLoad(GameTestHelper helper) {
        Player player = armed(helper, FiftyCalAmmoType.BLACK, 1);
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        BulletEntity bullet = ownerShot(bullets(helper), player);
        helper.assertTrue(bullet.ammoType() == FiftyCalAmmoType.BLACK
                        && bullet.damage() == 67.5F
                        && PenanceItem.rounds(player.getMainHandItem()) == 0
                        && PenanceItem.wear(player.getMainHandItem()) == 5.0F,
                "Penance must fire Black at 45 x 1.5 damage, consume one round and take five wear");
        helper.assertTrue(FiftyCalAmmoType.BLACK.spectral()
                        && FiftyCalAmmoType.BLACK.penetrates()
                        && !FiftyCalAmmoType.BLACK.penetrationDamageFalloff()
                        && FiftyCalAmmoType.BLACK.headshotMultiplier() == 3.0F
                        && FiftyCalAmmoType.BLACK.armorThresholdNegation() == 30.0F
                        && FiftyCalAmmoType.BLACK.armorPiercing() == 0.35F,
                "Black must retain its spectral bypass, no-falloff penetration and armor hooks");
        helper.assertTrue(FiftyCalAmmoType.BLACK.tracerDarkColor() == 0xFF000000
                        && FiftyCalAmmoType.BLACK.tracerLightColor() == 0xFF7F006E
                        && FiftyCalAmmoType.BLACK.tracerFullbright(),
                "Black must use the source black-to-purple fullbright tracer");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullReloadAcceptsAndLocksSecretBlackRounds(GameTestHelper helper) {
        Player player = armed(helper, FiftyCalAmmoType.HOLLOW_POINT, 0);
        ItemStack black = FiftyCalAmmoType.BLACK.createStack(ModItems.AMMO_SECRET.get(), 9);
        player.getInventory().add(black);

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        tickHeld(player, PenanceItem.RELOAD_TICKS);
        helper.assertTrue(PenanceItem.rounds(player.getMainHandItem()) == 7
                        && PenanceItem.loadedAmmo(player.getMainHandItem()) == FiftyCalAmmoType.BLACK
                        && countBlack(player) == 2,
                "An empty Penance must load seven secret Black rounds and retain the two-round remainder");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void demolisherRoundIsNotAcceptedByPenance(GameTestHelper helper) {
        Player player = armed(helper, FiftyCalAmmoType.HOLLOW_POINT, 0);
        player.getInventory().add(FiftyCalAmmoType.EQUESTRIAN.createStack(ModItems.AMMO_SECRET.get(), 7));
        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(PenanceItem.state(player.getMainHandItem()) == PenanceItem.GunState.DRAWING
                        && PenanceItem.animation(player.getMainHandItem()) == PenanceItem.GunAnimation.INSPECT
                        && PenanceItem.rounds(player.getMainHandItem()) == 0,
                "Penance's source magazine excludes the Equestrian Demolisher round");
        helper.succeed();
    }

    private static Player armed(GameTestHelper helper, FiftyCalAmmoType ammo, int rounds) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_AMAT_PENANCE.get());
        PenanceItem.setTestState(gun, PenanceItem.GunState.IDLE, 0, rounds, ammo, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(Player player, int ticks) {
        PenanceItem gun = (PenanceItem) player.getMainHandItem().getItem();
        for (int i = 0; i < ticks; i++) {
            gun.inventoryTick(player.getMainHandItem(), player.level(), player,
                    player.getInventory().selected, true);
        }
    }

    private static List<BulletEntity> bullets(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntitiesOfClass(BulletEntity.class, new AABB(origin).inflate(64.0D));
    }

    private static BulletEntity ownerShot(List<BulletEntity> bullets, Player owner) {
        return bullets.stream().filter(bullet -> bullet.getOwner() == owner).findFirst()
                .orElseThrow(() -> new AssertionError("Expected a Penance bullet owned by test player"));
    }

    private static int countBlack(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.AMMO_SECRET.get())
                    && StandardAmmoTypes.fromStack(stack) == FiftyCalAmmoType.BLACK) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
