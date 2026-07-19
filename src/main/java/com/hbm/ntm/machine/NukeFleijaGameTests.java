package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.NukeFleijaBlock;
import com.hbm.ntm.blockentity.NukeFleijaBlockEntity;
import com.hbm.ntm.explosion.DetonationResult;
import com.hbm.ntm.explosion.RemoteDetonation;
import com.hbm.ntm.nuclear.FleijaCloudEntity;
import com.hbm.ntm.nuclear.FleijaExplosionEntity;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NukeFleijaGameTests {
    private NukeFleijaGameTests() { }

    @GameTest(template = "empty")
    public static void readinessRequiresExactElevenGroupedComponents(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        NukeFleijaBlockEntity bomb = bomb(helper, pos);
        check(helper, !bomb.isReady(), "Empty F.L.E.I.J.A. must not be ready");
        fillBomb(bomb);
        check(helper, bomb.isReady(), "F.L.E.I.J.A. must arm with two igniters, three propellant and six cores");

        bomb.setItem(4, ItemStack.EMPTY);
        check(helper, !bomb.isReady(), "A missing propellant charge must make the F.L.E.I.J.A. unready");
        fillBomb(bomb);

        // Wrong grouping: a core placed in an igniter slot must not arm.
        bomb.setItem(0, new ItemStack(ModItems.FLEIJA_CORE.get()));
        check(helper, !bomb.isReady(), "A core in an igniter slot must not arm the F.L.E.I.J.A.");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inventoryPersistsWithStackLimitSixtyFour(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        NukeFleijaBlockEntity bomb = bomb(helper, pos);
        bomb.setItem(0, new ItemStack(ModItems.FLEIJA_IGNITER.get(), 64));
        check(helper, bomb.getItem(0).getCount() == 64,
                "F.L.E.I.J.A. slots must accept the source stack limit of 64");
        bomb.setItem(5, new ItemStack(ModItems.FLEIJA_CORE.get()));
        var tag = bomb.saveWithoutMetadata(helper.getLevel().registryAccess());
        NukeFleijaBlockEntity loaded = new NukeFleijaBlockEntity(helper.absolutePos(pos), helper.getBlockState(pos));
        loaded.loadWithComponents(tag, helper.getLevel().registryAccess());
        check(helper, loaded.getItem(0).is(ModItems.FLEIJA_IGNITER.get())
                        && loaded.getItem(0).getCount() == 64
                        && loaded.getItem(5).is(ModItems.FLEIJA_CORE.get()),
                "F.L.E.I.J.A. inventory must persist through save/load");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void redstoneDetonatesOnlyWhenReady(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        // Powered but not ready: the block must stay intact and spawn nothing.
        helper.setBlock(pos.above(), Blocks.REDSTONE_BLOCK);
        check(helper, !helper.getBlockState(pos).isAir(), "An unarmed powered F.L.E.I.J.A. must not detonate");
        check(helper, explosions(helper, pos).isEmpty() && clouds(helper, pos).isEmpty(),
                "An unarmed powered F.L.E.I.J.A. must spawn no blast systems");
        helper.setBlock(pos.above(), Blocks.AIR);

        // Arm it, then re-power it: it must detonate.
        fillBomb(bomb(helper, pos));
        helper.setBlock(pos.above(), Blocks.REDSTONE_BLOCK);
        check(helper, helper.getBlockState(pos).isAir(), "A ready powered F.L.E.I.J.A. must remove its block");
        check(helper, explosions(helper, pos).size() == 1 && clouds(helper, pos).size() == 1,
                "Redstone detonation must spawn one FLEIJA blast and one FLEIJA cloud");
        discardBlast(helper, pos);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void remoteDetonationHonoursComponentGate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = placeBomb(helper);
        BlockPos absolute = helper.absolutePos(pos);

        RemoteDetonation.Attempt unarmed = RemoteDetonation.trigger(level, absolute);
        check(helper, unarmed.compatible() && unarmed.result() == DetonationResult.ERROR_MISSING_COMPONENT,
                "An unarmed F.L.E.I.J.A. must report ERROR_MISSING_COMPONENT to a remote detonator");
        check(helper, !helper.getBlockState(pos).isAir(), "A failed remote trigger must leave the block intact");

        fillBomb(bomb(helper, pos));
        RemoteDetonation.Attempt armed = RemoteDetonation.trigger(level, absolute);
        check(helper, armed.compatible() && armed.result() == DetonationResult.DETONATED,
                "A ready F.L.E.I.J.A. must report DETONATED to a remote detonator");
        check(helper, helper.getBlockState(pos).isAir(), "A successful remote trigger must remove the block");
        discardBlast(helper, pos);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void detonationConsumesComponentsWithoutDrops(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        fillBomb(bomb(helper, pos));
        check(helper, ModBlocks.NUKE_FLEIJA.get().detonate(helper.getLevel(), helper.absolutePos(pos)),
                "Ready F.L.E.I.J.A. must detonate");
        check(helper, helper.getBlockState(pos).isAir(), "Detonation must remove the bomb block");
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(3.0D));
        check(helper, drops.stream().noneMatch(item -> item.getItem().is(ModItems.FLEIJA_IGNITER.get())
                        || item.getItem().is(ModItems.FLEIJA_PROPELLANT.get())
                        || item.getItem().is(ModItems.FLEIJA_CORE.get())),
                "Detonating F.L.E.I.J.A. must consume its parts and drop nothing");
        discardBlast(helper, pos);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingLoadedBombDropsAllParts(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        fillBomb(bomb(helper, pos));
        helper.setBlock(pos, Blocks.AIR);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(2.0D));
        int igniters = countOf(drops, ModItems.FLEIJA_IGNITER.get());
        int propellant = countOf(drops, ModItems.FLEIJA_PROPELLANT.get());
        int cores = countOf(drops, ModItems.FLEIJA_CORE.get());
        check(helper, igniters == 2 && propellant == 3 && cores == 6,
                "Breaking a loaded F.L.E.I.J.A. must recover two igniters, three propellant and six cores");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spawnKeepsSourcePositionAsymmetry(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        BlockPos absolute = helper.absolutePos(pos);
        fillBomb(bomb(helper, pos));
        ModBlocks.NUKE_FLEIJA.get().detonate(helper.getLevel(), absolute);

        FleijaExplosionEntity explosion = explosions(helper, pos).getFirst();
        FleijaCloudEntity cloud = clouds(helper, pos).getFirst();
        // Source igniteTestBomb: the explosion is centered (block + 0.5) but the cloud sits
        // on the raw block coordinate. Preserve that asymmetry.
        check(helper, explosion.getX() == absolute.getX() + 0.5D
                        && explosion.getY() == absolute.getY() + 0.5D
                        && explosion.getZ() == absolute.getZ() + 0.5D,
                "The FLEIJA blast must be centered on the block");
        check(helper, cloud.getX() == absolute.getX()
                        && cloud.getY() == absolute.getY()
                        && cloud.getZ() == absolute.getZ(),
                "The FLEIJA cloud must sit at the raw block coordinate, not the centered point");
        check(helper, explosion.radius() == 50 && cloud.maxAge() == 50,
                "The placed F.L.E.I.J.A. must use the config radius of 50 for both blast and cloud");
        discardBlast(helper, pos);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void smallBlastCarvesSymmetricBowlAndKeepsFloor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(5, 0, 5));
        int centerX = (int) (base.getX() + 0.5D);
        int centerZ = (int) (base.getZ() + 0.5D);
        int minY = level.getMinBuildHeight();
        BlockPos floor = new BlockPos(centerX, minY, centerZ);
        BlockPos aboveFloor = floor.above();
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(aboveFloor, Blocks.STONE.defaultBlockState());

        FleijaExplosionEntity blast = FleijaExplosionEntity.create(level, centerX, minY, centerZ, 1);
        blast.tick();
        check(helper, level.getBlockState(aboveFloor).isAir(),
                "A small placed-path FLEIJA blast must delete interior terrain");
        check(helper, level.getBlockState(floor).is(Blocks.STONE),
                "The dimension floor layer must survive like the source Y=0 floor");
        blast.discard();
        helper.succeed();
    }

    private static BlockPos placeBomb(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.NUKE_FLEIJA.get().defaultBlockState()
                .setValue(NukeFleijaBlock.FACING, Direction.SOUTH));
        return pos;
    }

    private static NukeFleijaBlockEntity bomb(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof NukeFleijaBlockEntity bomb) return bomb;
        helper.fail("Expected F.L.E.I.J.A. block entity");
        throw new IllegalStateException();
    }

    private static void fillBomb(NukeFleijaBlockEntity bomb) {
        bomb.setItem(0, new ItemStack(ModItems.FLEIJA_IGNITER.get()));
        bomb.setItem(1, new ItemStack(ModItems.FLEIJA_IGNITER.get()));
        for (int slot = 2; slot <= 4; slot++) bomb.setItem(slot, new ItemStack(ModItems.FLEIJA_PROPELLANT.get()));
        for (int slot = 5; slot <= 10; slot++) bomb.setItem(slot, new ItemStack(ModItems.FLEIJA_CORE.get()));
    }

    private static List<FleijaExplosionEntity> explosions(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(FleijaExplosionEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(3.0D));
    }

    private static List<FleijaCloudEntity> clouds(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(FleijaCloudEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(3.0D));
    }

    private static void discardBlast(GameTestHelper helper, BlockPos pos) {
        explosions(helper, pos).forEach(Entity::discard);
        clouds(helper, pos).forEach(Entity::discard);
    }

    private static int countOf(List<ItemEntity> drops, net.minecraft.world.item.Item item) {
        return drops.stream().filter(entity -> entity.getItem().is(item))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
