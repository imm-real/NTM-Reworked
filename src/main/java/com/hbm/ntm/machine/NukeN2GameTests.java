package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.NukeN2Block;
import com.hbm.ntm.blockentity.NukeN2BlockEntity;
import com.hbm.ntm.explosion.DetonationResult;
import com.hbm.ntm.explosion.RemoteDetonation;
import com.hbm.ntm.nuclear.FalloutRainEntity;
import com.hbm.ntm.nuclear.MushroomCloudEntity;
import com.hbm.ntm.nuclear.NuclearExplosionEntity;
import com.hbm.ntm.radiation.RadiationSystem;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NukeN2GameTests {
    private NukeN2GameTests() { }

    @GameTest(template = "empty")
    public static void placementIsInvisibleAndOrientedFromFacing(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper, Direction.SOUTH);
        bomb(helper, pos);
        check(helper, helper.getBlockState(pos).getRenderShape() == RenderShape.INVISIBLE,
                "The N2 Mine must be an invisible, TESR-only block (source getRenderType == -1)");
        check(helper, helper.getBlockState(pos).getValue(NukeN2Block.FACING) == Direction.SOUTH,
                "Placement must record the horizontal facing for the renderer");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void readinessRequiresAllTwelveCharges(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper, Direction.SOUTH);
        NukeN2BlockEntity bomb = bomb(helper, pos);
        check(helper, !bomb.isReady(), "An empty N2 Mine must not be ready");

        for (int slot = 0; slot < NukeN2BlockEntity.SLOTS - 1; slot++) {
            bomb.setItem(slot, new ItemStack(ModItems.N2_CHARGE.get()));
        }
        check(helper, !bomb.isReady(), "Eleven charges must not arm the twelve-slot N2 Mine");

        bomb.setItem(NukeN2BlockEntity.SLOTS - 1, new ItemStack(ModItems.N2_CHARGE.get()));
        check(helper, bomb.isReady(), "Twelve Large Explosive Charges must arm the N2 Mine");

        bomb.setItem(6, new ItemStack(Blocks.DIRT));
        check(helper, !bomb.isReady(), "Any wrong component must make the N2 Mine unready");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void redstoneDetonatesOnlyWhenReadyAlongTheNoRadPath(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper, Direction.SOUTH);

        // Powered but not ready: the mine must stay intact and spawn nothing.
        helper.setBlock(pos.above(), Blocks.REDSTONE_BLOCK);
        check(helper, !helper.getBlockState(pos).isAir(), "An unarmed powered N2 Mine must not detonate");
        check(helper, explosions(helper, pos).isEmpty() && clouds(helper, pos).isEmpty(),
                "An unarmed powered N2 Mine must spawn no blast systems");
        helper.setBlock(pos.above(), Blocks.AIR);

        // Arm it, then re-power it: it must detonate with the radiation- and fallout-suppressed blast.
        fillBomb(bomb(helper, pos));
        helper.setBlock(pos.above(), Blocks.REDSTONE_BLOCK);
        check(helper, helper.getBlockState(pos).isAir(), "A ready powered N2 Mine must remove its block");

        List<NuclearExplosionEntity> blasts = explosions(helper, pos);
        check(helper, blasts.size() == 1, "Redstone detonation must spawn exactly one MK5 blast");
        NuclearExplosionEntity blast = blasts.getFirst();
        check(helper, !blast.fallout(),
                "The N2 Mine blast must be statFacNoRad: fallout suppressed (no flash radiation, no fallout rain)");
        check(helper, blast.strength() == 400 && blast.speed() == 250 && blast.length() == 200,
                "Radius 200 must yield MK5 strength 400, speed 250 and length 200");
        check(helper, clouds(helper, pos).size() == 1, "Detonation must spawn exactly one standard Torex cloud");
        check(helper, falloutRain(helper, pos).isEmpty(),
                "The N2 Mine must never spawn a fallout-rain entity");
        discardBlast(helper, pos);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void remoteDetonationHonoursComponentGate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = placeBomb(helper, Direction.SOUTH);
        BlockPos absolute = helper.absolutePos(pos);

        RemoteDetonation.Attempt unarmed = RemoteDetonation.trigger(level, absolute);
        check(helper, unarmed.compatible() && unarmed.result() == DetonationResult.ERROR_MISSING_COMPONENT,
                "An unarmed N2 Mine must report ERROR_MISSING_COMPONENT to a remote detonator");
        check(helper, !helper.getBlockState(pos).isAir(), "A failed remote trigger must leave the mine intact");

        fillBomb(bomb(helper, pos));
        RemoteDetonation.Attempt armed = RemoteDetonation.trigger(level, absolute);
        check(helper, armed.compatible() && armed.result() == DetonationResult.DETONATED,
                "A ready N2 Mine must report DETONATED to a remote detonator");
        check(helper, helper.getBlockState(pos).isAir(), "A successful remote trigger must remove the mine");
        discardBlast(helper, pos);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void detonationConsumesChargesWithoutDrops(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper, Direction.SOUTH);
        fillBomb(bomb(helper, pos));
        check(helper, ModBlocks.NUKE_N2.get().detonate(helper.getLevel(), helper.absolutePos(pos)),
                "A ready N2 Mine must detonate");
        check(helper, helper.getBlockState(pos).isAir(), "Detonation must remove the mine block");
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(3.0D));
        check(helper, drops.stream().noneMatch(item -> item.getItem().is(ModItems.N2_CHARGE.get())),
                "Detonating the N2 Mine must consume its charges and drop nothing");
        discardBlast(helper, pos);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingUnarmedMineDropsCharges(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper, Direction.SOUTH);
        NukeN2BlockEntity bomb = bomb(helper, pos);
        bomb.setItem(0, new ItemStack(ModItems.N2_CHARGE.get()));
        bomb.setItem(1, new ItemStack(ModItems.N2_CHARGE.get()));
        bomb.setItem(2, new ItemStack(ModItems.N2_CHARGE.get()));
        helper.setBlock(pos, Blocks.AIR);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(2.0D));
        check(helper, drops.stream().filter(item -> item.getItem().is(ModItems.N2_CHARGE.get()))
                        .mapToInt(item -> item.getItem().getCount()).sum() == 3,
                "Breaking a partially filled N2 Mine must recover its stored charges");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inventoryPersistsWithCustomNameAndStackLimit(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper, Direction.SOUTH);
        NukeN2BlockEntity bomb = bomb(helper, pos);
        for (int slot = 0; slot < NukeN2BlockEntity.SLOTS; slot++) {
            bomb.setItem(slot, new ItemStack(ModItems.N2_CHARGE.get(), 64));
        }
        bomb.setCustomName(Component.literal("Mine Zero"));
        check(helper, bomb.getItem(0).getCount() == 64, "N2 Mine slots must accept the source stack limit of 64");

        var tag = bomb.saveWithoutMetadata(helper.getLevel().registryAccess());
        NukeN2BlockEntity loaded = new NukeN2BlockEntity(helper.absolutePos(pos), helper.getBlockState(pos));
        loaded.loadWithComponents(tag, helper.getLevel().registryAccess());
        check(helper, loaded.isReady(), "A saved-and-loaded twelve-charge N2 Mine must remain armed");
        check(helper, loaded.getItem(11).getCount() == 64,
                "Loaded N2 Mine slots must preserve their stored counts");
        check(helper, loaded.getDisplayName().getString().equals("Mine Zero"),
                "The custom inventory name must survive save/load");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "n2_norad_isolated")
    public static void blastDamagesEntitiesButNeverRadiates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Void altitude above the platform so the MK5 rays find no terrain to destroy.
        BlockPos relative = new BlockPos(2, 30, 2);
        BlockPos base = helper.absolutePos(relative);
        double cx = base.getX() + 0.5D;
        double cy = base.getY() + 0.5D;
        double cz = base.getZ() + 0.5D;

        Pig victim = helper.spawnWithNoFreeWill(EntityType.PIG, relative);
        victim.moveTo(cx, cy, cz, 0.0F, 0.0F);
        float startHealth = victim.getHealth();

        NuclearExplosionEntity blast = NuclearExplosionEntity.createNoRad(level, 5, cx, cy, cz);
        check(helper, !blast.fallout(), "createNoRad must clear the fallout flag");
        level.addFreshEntity(blast);
        blast.tick();

        check(helper, !victim.isAlive() || victim.getHealth() < startHealth,
                "The unconditional dealDamage sweep must still hurt entities in range");
        check(helper, RadiationSystem.data(victim).radEnv() == 0.0F,
                "A statFacNoRad blast must never contaminate: no flash radiation on the no-rad path");

        blast.discard();
        victim.discard();
        helper.succeed();
    }

    private static BlockPos placeBomb(GameTestHelper helper, Direction facing) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.NUKE_N2.get().defaultBlockState().setValue(NukeN2Block.FACING, facing));
        return pos;
    }

    private static NukeN2BlockEntity bomb(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof NukeN2BlockEntity bomb) return bomb;
        helper.fail("Expected N2 Mine block entity");
        throw new IllegalStateException();
    }

    private static void fillBomb(NukeN2BlockEntity bomb) {
        for (int slot = 0; slot < NukeN2BlockEntity.SLOTS; slot++) {
            bomb.setItem(slot, new ItemStack(ModItems.N2_CHARGE.get()));
        }
    }

    private static List<NuclearExplosionEntity> explosions(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(NuclearExplosionEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(3.0D));
    }

    private static List<MushroomCloudEntity> clouds(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(MushroomCloudEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(3.0D));
    }

    private static List<FalloutRainEntity> falloutRain(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(FalloutRainEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(6.0D));
    }

    private static void discardBlast(GameTestHelper helper, BlockPos pos) {
        explosions(helper, pos).forEach(Entity::discard);
        clouds(helper, pos).forEach(Entity::discard);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
