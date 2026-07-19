package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.BreakActionRevolverItem;
import com.hbm.ntm.item.SednaGunItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BreakActionRevolverGameTests {
    private BreakActionRevolverGameTests() { }

    @GameTest(template = "empty")
    public static void magnumVariantsPreserveIdentityAndSourceStats(GameTestHelper helper) {
        for (Magnum357AmmoType type : Magnum357AmmoType.values()) {
            ItemStack stack = type.createStack(ModItems.AMMO_STANDARD.get(), 8);
            helper.assertTrue(Magnum357AmmoType.fromStack(stack) == type,
                    type + " must survive component serialization");
            helper.assertTrue(type.legacyMetadata() >= 4 && type.legacyMetadata() <= 9,
                    type + " must preserve legacy ammo_standard metadata");
        }
        helper.assertTrue(Magnum357AmmoType.BLACK_POWDER.blackPowder()
                        && Magnum357AmmoType.BLACK_POWDER.damageMultiplier() == 0.75F,
                "The black-powder load must retain its effect and 0.75 damage multiplier");
        helper.assertTrue(Magnum357AmmoType.HOLLOW_POINT.headshotMultiplier() == 1.5F
                        && Magnum357AmmoType.HOLLOW_POINT.armorPiercing() == -0.25F,
                "JHP must retain its stronger headshot and negative armor piercing");
        helper.assertTrue(Magnum357AmmoType.ARMOR_PIERCING.penetrates()
                        && !Magnum357AmmoType.ARMOR_PIERCING.penetrationDamageFalloff()
                        && Magnum357AmmoType.ARMOR_PIERCING.armorThresholdNegation() == 5.0F,
                "AP must penetrate without damage falloff and retain five DT negation");
        helper.assertTrue(Magnum357AmmoType.EXPRESS.penetrates()
                        && Magnum357AmmoType.EXPRESS.wear() == 1.5F,
                "Express must penetrate and apply 1.5 wear per shot");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void standardAndAtlasUseExactDamageAndElevenTickCycle(GameTestHelper helper) {
        Player standardPlayer = armedPlayer(helper, false, Magnum357AmmoType.SOFT_POINT, 6, 0.0F);
        BreakActionRevolverItem standard = (BreakActionRevolverItem) standardPlayer.getMainHandItem().getItem();
        SednaGunItem.handleInput(standardPlayer, GunInput.PRIMARY);
        helper.assertTrue(standard.baseDamage() == 7.5F, "The standard revolver must use 7.5 base damage");
        helper.assertTrue(BreakActionRevolverItem.rounds(standardPlayer.getMainHandItem()) == 5
                        && BreakActionRevolverItem.timer(standardPlayer.getMainHandItem()) == 11,
                "Standard fire must consume one round and start the 11-tick cycle");
        List<BulletEntity> standardBullets = bullets(helper, standardPlayer);
        helper.assertTrue(standardBullets.size() == 1 && standardBullets.getFirst().damage() == 7.5F,
                "Soft Point must produce one 7.5-damage standard bullet");

        Player atlasPlayer = armedPlayer(helper, true, Magnum357AmmoType.SOFT_POINT, 6, 0.0F);
        BreakActionRevolverItem atlas = (BreakActionRevolverItem) atlasPlayer.getMainHandItem().getItem();
        SednaGunItem.handleInput(atlasPlayer, GunInput.PRIMARY);
        List<BulletEntity> atlasBullets = bullets(helper, atlasPlayer);
        helper.assertTrue(atlas.baseDamage() == 12.5F && atlasBullets.size() == 1
                        && atlasBullets.getFirst().damage() == 12.5F,
                "Atlas must use the source 12.5 base damage");

        tickHeld(helper, standardPlayer, 10);
        helper.assertTrue(BreakActionRevolverItem.state(standardPlayer.getMainHandItem())
                        == BreakActionRevolverItem.GunState.COOLDOWN,
                "The revolver must remain locked through cycle tick ten");
        tickHeld(helper, standardPlayer, 1);
        helper.assertTrue(BreakActionRevolverItem.state(standardPlayer.getMainHandItem())
                        == BreakActionRevolverItem.GunState.IDLE,
                "The revolver must return to idle on cycle tick eleven");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullReloadWaitsFiftyFiveTicksAndKeepsAmmoHomogeneous(GameTestHelper helper) {
        Player player = armedPlayer(helper, false, Magnum357AmmoType.FULL_METAL_JACKET, 2, 0.0F);
        player.getInventory().add(Magnum357AmmoType.SOFT_POINT.createStack(ModItems.AMMO_STANDARD.get(), 8));
        player.getInventory().add(Magnum357AmmoType.FULL_METAL_JACKET.createStack(ModItems.AMMO_STANDARD.get(), 8));
        ItemStack gun = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(BreakActionRevolverItem.state(gun) == BreakActionRevolverItem.GunState.RELOADING
                        && BreakActionRevolverItem.timer(gun) == 55,
                "Reload must begin with the source 55-tick timer");
        tickHeld(helper, player, 54);
        helper.assertTrue(BreakActionRevolverItem.rounds(gun) == 2
                        && BreakActionRevolverItem.timer(gun) == 1,
                "A full reload must not transfer rounds early");
        tickHeld(helper, player, 1);
        helper.assertTrue(BreakActionRevolverItem.rounds(gun) == 6
                        && BreakActionRevolverItem.loadedAmmo(gun) == Magnum357AmmoType.FULL_METAL_JACKET,
                "A partial cylinder must accept only its loaded .357 identity");
        helper.assertTrue(countAmmo(player, Magnum357AmmoType.FULL_METAL_JACKET) == 4,
                "Reload must consume exactly four compatible rounds");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void expressAddsOnePointFiveWearAndDryFireConsumesNothing(GameTestHelper helper) {
        Player player = armedPlayer(helper, false, Magnum357AmmoType.EXPRESS, 1, 10.0F);
        ItemStack gun = player.getMainHandItem();
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(BreakActionRevolverItem.wear(gun) == 11.5F
                        && BreakActionRevolverItem.rounds(gun) == 0,
                "Express must add 1.5 wear while consuming one round");
        tickHeld(helper, player, 11);
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(BreakActionRevolverItem.wear(gun) == 11.5F
                        && BreakActionRevolverItem.animation(gun) == BreakActionRevolverItem.GunAnimation.CYCLE_DRY
                        && BreakActionRevolverItem.timer(gun) == 11,
                "Dry fire must preserve wear and use the locked 11-tick dry cycle");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "break_action_ballistics_isolated")
    public static void headshotsAndArmorPiercingAffectActualHits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);

        Vec3 bodyBase = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 2, 2)));
        Zombie body = zombie(level, bodyBase);
        Zombie head = zombie(level, bodyBase.add(0.0D, 0.0D, 3.0D));
        BulletEntity bodyShot = new BulletEntity(level, owner, Magnum357AmmoType.SOFT_POINT, 4.0F, 0.0F,
                new Vec3(bodyBase.x - 4.0D, bodyBase.y + 0.8D, bodyBase.z), new Vec3(1.0D, 0.0D, 0.0D));
        BulletEntity headShot = new BulletEntity(level, owner, Magnum357AmmoType.SOFT_POINT, 4.0F, 0.0F,
                new Vec3(bodyBase.x - 4.0D, bodyBase.y + 1.6D, bodyBase.z + 3.0D), new Vec3(1.0D, 0.0D, 0.0D));
        level.addFreshEntity(bodyShot);
        level.addFreshEntity(headShot);
        bodyShot.tick();
        headShot.tick();
        helper.assertTrue(head.getHealth() < body.getHealth(),
                "A source-defined .357 headshot must deal more damage than an equal body hit");

        Zombie softPointTarget = zombie(level, bodyBase.add(0.0D, 0.0D, 6.0D));
        Zombie armorPiercingTarget = zombie(level, bodyBase.add(0.0D, 0.0D, 9.0D));
        softPointTarget.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        armorPiercingTarget.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        BulletEntity softPoint = new BulletEntity(level, owner, Magnum357AmmoType.SOFT_POINT, 10.0F, 0.0F,
                new Vec3(bodyBase.x - 4.0D, bodyBase.y + 0.8D, bodyBase.z + 6.0D), new Vec3(1.0D, 0.0D, 0.0D));
        BulletEntity armorPiercing = new BulletEntity(level, owner, Magnum357AmmoType.ARMOR_PIERCING, 10.0F, 0.0F,
                new Vec3(bodyBase.x - 4.0D, bodyBase.y + 0.8D, bodyBase.z + 9.0D), new Vec3(1.0D, 0.0D, 0.0D));
        level.addFreshEntity(softPoint);
        level.addFreshEntity(armorPiercing);
        softPoint.tick();
        armorPiercing.tick();
        helper.assertTrue(armorPiercingTarget.getHealth() < softPointTarget.getHealth(),
                "Positive .357 armor piercing must reduce effective vanilla armor");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wearAndJamCurvesRetainSourceThresholds(GameTestHelper helper) {
        helper.assertTrue(BreakActionRevolverItem.jamChance(198.0F) == 0.0F,
                "Jamming must remain impossible at exactly 66 percent wear");
        helper.assertTrue(BreakActionRevolverItem.jamChance(273.0F) == 1.0F,
                "Jamming must reach certainty at 91 percent wear");
        helper.assertTrue(BreakActionRevolverItem.wearDamageMultiplier(225.0F) == 1.0F
                        && BreakActionRevolverItem.wearDamageMultiplier(300.0F) == 0.5F,
                "Wear damage must remain full through 75 percent and fall to half at maximum");
        helper.assertTrue(BreakActionRevolverItem.wearSpread(150.0F) == 0.0F
                        && BreakActionRevolverItem.wearSpread(300.0F) == 0.125F,
                "Wear spread must rise from zero at half wear to 0.125 at maximum");
        helper.succeed();
    }

    private static Player armedPlayer(GameTestHelper helper, boolean atlas, Magnum357AmmoType ammo,
                                      int rounds, float wear) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(atlas ? ModItems.GUN_LIGHT_REVOLVER_ATLAS.get()
                : ModItems.GUN_LIGHT_REVOLVER.get());
        BreakActionRevolverItem.setTestState(gun, BreakActionRevolverItem.GunState.IDLE,
                0, rounds, ammo, wear);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(atlas ? 7 : 2, 2, 2)));
        player.setPos(origin);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(GameTestHelper helper, Player player, int ticks) {
        BreakActionRevolverItem gun = (BreakActionRevolverItem) player.getMainHandItem().getItem();
        for (int i = 0; i < ticks; i++) {
            gun.inventoryTick(player.getMainHandItem(), helper.getLevel(), player,
                    player.getInventory().selected, true);
        }
    }

    private static int countAmmo(Player player, Magnum357AmmoType type) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.AMMO_STANDARD.get()) && Magnum357AmmoType.fromStack(stack) == type) {
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
