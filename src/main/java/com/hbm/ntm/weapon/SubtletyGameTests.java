package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BuildingEntity;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.entity.PowerFistRubbleEntity;
import com.hbm.ntm.item.AmatItem;
import com.hbm.ntm.item.SednaGunItem;
import com.hbm.ntm.item.SubtletyItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SubtletyGameTests {
    private SubtletyGameTests() { }

    @GameTest(template = "empty")
    public static void receiverKeepsLegendarySourceProfileAndSpawnsEmpty(GameTestHelper helper) {
        SubtletyItem gun = ModItems.GUN_AMAT_SUBTLETY.get();
        ItemStack stack = new ItemStack(gun);
        List<FiftyCalAmmoType> sourceOrder = List.of(
                FiftyCalAmmoType.EQUESTRIAN,
                FiftyCalAmmoType.SOFT_POINT,
                FiftyCalAmmoType.FULL_METAL_JACKET,
                FiftyCalAmmoType.HOLLOW_POINT,
                FiftyCalAmmoType.ARMOR_PIERCING,
                FiftyCalAmmoType.DEPLETED_URANIUM,
                FiftyCalAmmoType.STARMETAL,
                FiftyCalAmmoType.HIGH_EXPLOSIVE);

        helper.assertTrue(gun.gunDurability() == 1_000.0F
                        && gun.baseDamage() == 50.0F
                        && gun.gunCapacity() == 7
                        && gun.gunCrosshair() == SednaCrosshair.CIRCLE
                        && gun.gunAimFovMultiplier() == 0.2F
                        && gun.recoilVertical() == 12.5F
                        && gun.recoilHorizontalSigma() == 1.0F,
                "Subtlety must retain its source legendary AMAT receiver profile");
        helper.assertTrue(gun.profile().supportedAmmo().equals(sourceOrder)
                        && AmatItem.rounds(stack) == 0
                        && AmatItem.loadedAmmo(stack) == FiftyCalAmmoType.HOLLOW_POINT,
                "Subtlety must spawn empty with JHP selected and preserve hidden-round ordering");
        helper.assertTrue(FiftyCalAmmoType.EQUESTRIAN.damageMultiplier() == 0.0F
                        && FiftyCalAmmoType.EQUESTRIAN.spawnsBuildingOnImpact(),
                "the .50 BMG Demolisher must deal no bullet damage and create a building instead");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "subtlety_building_isolated")
    public static void demolisherRoundCreatesBuildingFiftyBlocksAboveImpact(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_AMAT_SUBTLETY.get());
        AmatItem.setTestState(gun, AmatItem.GunState.IDLE, 0, 1,
                FiftyCalAmmoType.EQUESTRIAN, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        Vec3 playerPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
        player.setPos(playerPos);
        player.setYRot(-90.0F);
        player.setXRot(0.0F);

        BlockPos wall = helper.absolutePos(new BlockPos(8, 4, 2));
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        BulletEntity bullet = level.getEntitiesOfClass(BulletEntity.class,
                        new AABB(playerPos, playerPos).inflate(32.0D), shot -> shot.getOwner() == player)
                .stream().findFirst().orElseThrow();
        bullet.tick();

        List<BuildingEntity> buildings = level.getEntitiesOfClass(BuildingEntity.class,
                new AABB(wall).inflate(4.0D, 60.0D, 4.0D));
        helper.assertTrue(buildings.size() == 1 && !bullet.isAlive(),
                "the Demolisher impact must consume its bullet and create exactly one falling building");
        BuildingEntity building = buildings.getFirst();
        helper.assertTrue(Math.abs(building.getY() - (wall.getY() + BuildingEntity.SPAWN_HEIGHT)) < 1.0D,
                "the building must appear exactly fifty blocks over the collision point");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "subtlety_building_isolated")
    public static void buildingImpactKillsAndThrowsTwoHundredFiftyBrickChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos impactBlock = helper.absolutePos(new BlockPos(5, 3, 5));
        level.setBlock(impactBlock, Blocks.STONE.defaultBlockState(), 3);
        Vec3 center = Vec3.atCenterOf(impactBlock);

        Zombie victim = EntityType.ZOMBIE.create(level);
        if (victim == null) throw new IllegalStateException("Could not create Subtlety test zombie");
        victim.setPos(center.add(2.0D, 0.0D, 0.0D));
        level.addFreshEntity(victim);

        BuildingEntity building = BuildingEntity.spawn(level,
                new Vec3(center.x, center.y - BuildingEntity.SPAWN_HEIGHT, center.z));
        building.tick();

        List<PowerFistRubbleEntity> rubble = level.getEntitiesOfClass(PowerFistRubbleEntity.class,
                new AABB(center, center).inflate(8.0D));
        helper.assertTrue(!building.isAlive() && !victim.isAlive(),
                "a building touching terrain must vanish and deal the source 1,000 impact damage");
        helper.assertTrue(rubble.size() == BuildingEntity.RUBBLE_COUNT
                        && rubble.stream().allMatch(piece -> piece.blockState().is(Blocks.BRICKS)),
                "building impact must throw exactly 250 brick rubble entities");
        helper.assertTrue(rubble.stream().allMatch(piece -> {
                    double speed = piece.getDeltaMovement().length();
                    return speed > 0.999D && speed < 1.001D && piece.getDeltaMovement().y >= 0.0D;
                }),
                "building rubble must retain the source unit-speed upper-hemisphere launch");
        helper.succeed();
    }
}
