package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.PepperboxItem;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PepperboxGameTests {
    private PepperboxGameTests() { }

    @GameTest(template = "empty")
    public static void ammoVariantsPreserveLegacyIdentityAndStats(GameTestHelper helper) {
        for (PepperboxAmmoType type : PepperboxAmmoType.values()) {
            ItemStack stack = type.createStack(ModItems.AMMO_STANDARD.get(), 6);
            helper.assertTrue(PepperboxAmmoType.fromStack(stack) == type,
                    type + " must survive the metadata-to-component adaptation");
            helper.assertTrue(type.legacyBulletConfig() == type.legacyMetadata() + 2,
                    type + " must retain the original startup BulletConfig ID");
        }
        helper.assertTrue(PepperboxAmmoType.SHOT.projectiles() == 6
                        && Math.abs(PepperboxAmmoType.SHOT.damageMultiplier() - 1.0F / 6.0F) < 0.0001F,
                "Shot and Powder must split one five-damage load into six pellets");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void firingConsumesOneRoundAddsWearAndHonorsTwentySevenTickDelay(GameTestHelper helper) {
        Player player = armedPlayer(helper, PepperboxAmmoType.STONE, 6);
        ItemStack gun = player.getMainHandItem();

        PepperboxItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(PepperboxItem.rounds(gun) == 5, "One firing cycle must consume one loaded round");
        helper.assertTrue(PepperboxItem.wear(gun) == 1.0F, "One firing cycle must add one wear");
        helper.assertTrue(PepperboxItem.state(gun) == PepperboxItem.GunState.COOLDOWN
                && PepperboxItem.timer(gun) == 27, "Live fire must begin the original 27-tick cooldown");
        helper.assertTrue(bullets(helper, player).size() == 1,
                "Ball and Powder must spawn one real bullet entity owned by the shooter");

        tickHeld(helper, player, 26);
        helper.assertTrue(PepperboxItem.state(gun) == PepperboxItem.GunState.COOLDOWN
                && PepperboxItem.timer(gun) == 1, "The Pepperbox must remain locked through tick 26");
        tickHeld(helper, player, 1);
        helper.assertTrue(PepperboxItem.state(gun) == PepperboxItem.GunState.IDLE,
                "The Pepperbox must return to idle on tick 27");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullReloadWaitsSixtySevenTicksAndKeepsPartialAmmoHomogeneous(GameTestHelper helper) {
        Player player = armedPlayer(helper, PepperboxAmmoType.FLINT, 2);
        ItemStack gun = player.getMainHandItem();
        player.getInventory().add(PepperboxAmmoType.STONE.createStack(ModItems.AMMO_STANDARD.get(), 8));
        ItemStack flint = PepperboxAmmoType.FLINT.createStack(ModItems.AMMO_STANDARD.get(), 8);
        player.getInventory().add(flint);

        PepperboxItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(PepperboxItem.state(gun) == PepperboxItem.GunState.RELOADING
                && PepperboxItem.timer(gun) == 67, "Reload must begin with the original 67-tick timer");
        tickHeld(helper, player, 66);
        helper.assertTrue(PepperboxItem.rounds(gun) == 2 && PepperboxItem.timer(gun) == 1,
                "Full reload must not transfer ammunition before its final tick");
        tickHeld(helper, player, 1);
        helper.assertTrue(PepperboxItem.rounds(gun) == 6
                        && PepperboxItem.loadedAmmo(gun) == PepperboxAmmoType.FLINT,
                "A partial cylinder must only accept its existing ammunition type");
        helper.assertTrue(countAmmo(player, PepperboxAmmoType.FLINT) == 4,
                "Reload must consume exactly four loose Flint and Powder loads");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyTriggerDryFiresWithoutAmmoOrWear(GameTestHelper helper) {
        Player player = armedPlayer(helper, PepperboxAmmoType.STONE, 0);
        ItemStack gun = player.getMainHandItem();
        PepperboxItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(PepperboxItem.rounds(gun) == 0 && PepperboxItem.wear(gun) == 0.0F,
                "Dry fire must not consume ammunition or condition");
        helper.assertTrue(PepperboxItem.state(gun) == PepperboxItem.GunState.DRAWING
                        && PepperboxItem.timer(gun) == 27
                        && PepperboxItem.animation(gun) == PepperboxItem.GunAnimation.CYCLE_DRY,
                "Dry fire must use the original locked dry-cycle state");
        helper.assertTrue(bullets(helper, player).isEmpty(),
                "Dry fire must not spawn a projectile owned by the shooter");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void projectileDamagePenetrationAndFalloffMatchBlackPowderLoads(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 start = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
        owner.setPos(start.x - 1.0D, start.y, start.z);
        Zombie first = zombie(level, start.add(4.0D, -0.5D, 0.0D));
        Zombie second = zombie(level, start.add(7.0D, -0.5D, 0.0D));

        BulletEntity flint = new BulletEntity(level, owner, PepperboxAmmoType.FLINT, 7.5F, 0.0F,
                start, new Vec3(1.0D, 0.0D, 0.0D));
        level.addFreshEntity(flint);
        flint.tick();

        helper.assertTrue(first.getHealth() < 20.0F && second.getHealth() < 20.0F,
                "Flint and Powder must penetrate every intersected entity in the swept path");
        helper.assertTrue(flint.damage() < 7.5F,
                "Flint penetration must lose half of the actual health damage dealt");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void glassPassThroughAndIronRicochetRemainObservable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos glass = helper.absolutePos(new BlockPos(5, 2, 2));
        BlockPos wall = helper.absolutePos(new BlockPos(9, 2, 2));
        level.setBlockAndUpdate(glass, Blocks.GLASS.defaultBlockState());
        level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        Vec3 start = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
        owner.setPos(start.x - 1.0D, start.y, start.z);

        BulletEntity iron = new BulletEntity(level, owner, PepperboxAmmoType.IRON, 7.5F, 0.0F,
                start, new Vec3(1.0D, 0.0D, 0.0D));
        level.addFreshEntity(iron);
        iron.tick();
        helper.assertTrue(level.getBlockState(glass).isAir() && iron.isAlive(),
                "Bullets must destroy glass without drops and continue");
        iron.tick();
        helper.assertTrue(iron.isAlive() && iron.ricochets() == 1 && iron.getDeltaMovement().x < 0.0D,
                "Iron Ball and Powder must ricochet even on a head-on impact at its 90-degree threshold");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wearCurvesAndJamThresholdUseOriginalConstants(GameTestHelper helper) {
        helper.assertTrue(PepperboxItem.jamChance(197.0F) == 0.0F,
                "Jamming must be impossible below 66 percent wear");
        helper.assertTrue(Math.abs(PepperboxItem.jamChance(273.0F) - 1.0F) < 0.0001F,
                "Jamming must reach certainty at 91 percent wear");
        helper.assertTrue(PepperboxItem.wearDamageMultiplier(225.0F) == 1.0F
                        && PepperboxItem.wearDamageMultiplier(300.0F) == 0.5F,
                "Damage must remain full through 75 percent wear and fall to half at maximum wear");
        helper.assertTrue(PepperboxItem.wearSpread(150.0F) == 0.0F
                        && PepperboxItem.wearSpread(300.0F) == 0.125F,
                "Wear spread must rise from zero at 50 percent to 0.125 at maximum wear");
        helper.succeed();
    }

    private static Player armedPlayer(GameTestHelper helper, PepperboxAmmoType ammo, int rounds) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_PEPPERBOX.get());
        PepperboxItem.setTestState(gun, PepperboxItem.GunState.IDLE, 0, rounds, ammo, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        return player;
    }

    private static void tickHeld(GameTestHelper helper, Player player, int ticks) {
        PepperboxItem item = (PepperboxItem) player.getMainHandItem().getItem();
        for (int i = 0; i < ticks; i++) {
            item.inventoryTick(player.getMainHandItem(), helper.getLevel(), player,
                    player.getInventory().selected, true);
        }
    }

    private static int countAmmo(Player player, PepperboxAmmoType type) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.AMMO_STANDARD.get()) && PepperboxAmmoType.fromStack(stack) == type) {
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

    private static Zombie zombie(ServerLevel level, Vec3 position) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) throw new IllegalStateException("Could not create GameTest zombie");
        zombie.setPos(position);
        level.addFreshEntity(zombie);
        return zombie;
    }
}
