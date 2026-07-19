package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.HangmanItem;
import com.hbm.ntm.item.SednaGunItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
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
public final class HangmanGameTests {
    private HangmanGameTests() { }

    @GameTest(template = "empty")
    public static void sourceConfigurationIsPreserved(GameTestHelper helper) {
        HangmanItem gun = ModItems.GUN_HANGMAN.get();
        helper.assertTrue(HangmanItem.DURABILITY == 600
                        && HangmanItem.DRAW_TICKS == 10
                        && HangmanItem.FIRE_DELAY == 10
                        && HangmanItem.RELOAD_TICKS == 46
                        && HangmanItem.JAM_TICKS == 23
                        && HangmanItem.INSPECT_TICKS == 31,
                "Hangman timings and durability must match XFactory44");
        helper.assertTrue(HangmanItem.CAPACITY == 8 && HangmanItem.BASE_DAMAGE == 25.0F,
                "Hangman must retain its eight-round magazine and 25 base damage");
        helper.assertTrue(gun.gunCrosshair() == SednaCrosshair.CIRCLE
                        && gun.recoilVertical() == 5.0F && gun.recoilHorizontalSigma() == 1.0F,
                "Hangman must retain its circle reticle and source recoil");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fireUsesFortyFourAmmoAndTenTickCycle(GameTestHelper helper) {
        Player player = armedPlayer(helper, Magnum44AmmoType.HOLLOW_POINT, 2, 0.0F);
        ItemStack stack = player.getMainHandItem();
        SednaGunItem.handleInput(player, GunInput.PRIMARY);

        helper.assertTrue(HangmanItem.rounds(stack) == 1
                        && HangmanItem.timer(stack) == 10
                        && HangmanItem.animation(stack) == HangmanItem.GunAnimation.CYCLE,
                "A shot must consume one round and start the ten-tick Hangman cycle");
        List<BulletEntity> bullets = bullets(helper, player);
        helper.assertTrue(bullets.size() == 1 && bullets.getFirst().damage() == 37.5F,
                ".44 JHP must apply its 1.5 multiplier to the 25-damage receiver");

        tickHeld(helper, player, 9);
        helper.assertTrue(HangmanItem.state(stack) == HangmanItem.GunState.COOLDOWN,
                "Hangman must remain locked through cycle tick nine");
        tickHeld(helper, player, 1);
        helper.assertTrue(HangmanItem.state(stack) == HangmanItem.GunState.IDLE,
                "Hangman must return to idle on cycle tick ten");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullReloadWaitsAndKeepsMagazineHomogeneous(GameTestHelper helper) {
        Player player = armedPlayer(helper, Magnum44AmmoType.FULL_METAL_JACKET, 2, 0.0F);
        player.getInventory().add(Magnum44AmmoType.SOFT_POINT.createStack(ModItems.AMMO_STANDARD.get(), 8));
        player.getInventory().add(Magnum44AmmoType.FULL_METAL_JACKET.createStack(ModItems.AMMO_STANDARD.get(), 8));
        ItemStack gun = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(HangmanItem.state(gun) == HangmanItem.GunState.RELOADING
                        && HangmanItem.timer(gun) == 46,
                "Reload must start the source 46-tick full-magazine action");
        tickHeld(helper, player, 45);
        helper.assertTrue(HangmanItem.rounds(gun) == 2 && HangmanItem.timer(gun) == 1,
                "Full reload must not transfer ammunition before its final tick");
        tickHeld(helper, player, 1);
        helper.assertTrue(HangmanItem.rounds(gun) == 8
                        && HangmanItem.loadedAmmo(gun) == Magnum44AmmoType.FULL_METAL_JACKET,
                "A partial magazine must accept only its existing .44 identity");
        helper.assertTrue(countAmmo(player, Magnum44AmmoType.FULL_METAL_JACKET) == 2,
                "Reload must consume exactly six compatible rounds");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void secondaryPerformsSourceThreeBlockSmack(GameTestHelper helper) {
        Player player = armedPlayer(helper, Magnum44AmmoType.FULL_METAL_JACKET, 8, 0.0F);
        ItemStack stack = player.getMainHandItem();
        tickHeld(helper, player, 1);
        HangmanItem.setTestState(stack, HangmanItem.GunState.IDLE, 0, 8,
                Magnum44AmmoType.FULL_METAL_JACKET, 0.0F);

        ServerLevel level = helper.getLevel();
        Cow target = EntityType.COW.create(level);
        if (target == null) throw new IllegalStateException("Could not create GameTest cow");
        // The butt-stroke ray originates at the shooter's eye (the shared SednaGunItem origin
        // convention). Center the target on that horizontal eye-level ray two blocks ahead so the
        // 3-block reach connects; a ground-level cow (1.4 tall) sits entirely below the crosshair.
        target.setPos(player.getX(), player.getEyeY() - target.getBbHeight() / 2.0D, player.getZ() + 2.0D);
        level.addFreshEntity(target);

        SednaGunItem.handleInput(player, GunInput.SECONDARY);
        helper.assertTrue(HangmanItem.state(stack) == HangmanItem.GunState.DRAWING
                        && HangmanItem.timer(stack) == 31
                        && HangmanItem.animation(stack) == HangmanItem.GunAnimation.INSPECT,
                "Right click must start the non-cancelable 31-tick source inspect/smack animation");
        tickHeld(helper, player, 16);
        helper.assertTrue(target.getHealth() == target.getMaxHealth(),
                "The target must remain untouched before orchestra tick sixteen");
        tickHeld(helper, player, 1);
        helper.assertTrue(target.getMaxHealth() - target.getHealth() == HangmanItem.SMACK_DAMAGE,
                "The source butt-stroke must deal ten player damage at three-block reach");
        helper.succeed();
    }

    private static Player armedPlayer(GameTestHelper helper, Magnum44AmmoType ammo, int rounds, float wear) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_HANGMAN.get());
        HangmanItem.setTestState(gun, HangmanItem.GunState.IDLE, 0, rounds, ammo, wear);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(GameTestHelper helper, Player player, int ticks) {
        HangmanItem gun = (HangmanItem) player.getMainHandItem().getItem();
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
