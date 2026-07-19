package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.NukeSoliniumBlock;
import com.hbm.ntm.blockentity.NukeSoliniumBlockEntity;
import com.hbm.ntm.explosion.DetonationResult;
import com.hbm.ntm.explosion.RemoteDetonation;
import com.hbm.ntm.nuclear.FleijaExplosionEntity;
import com.hbm.ntm.nuclear.SoliniumCloudEntity;
import com.hbm.ntm.nuclear.SoliniumExplosionEntity;
import com.hbm.ntm.radiation.RadiationSystem;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NukeSoliniumGameTests {
    private NukeSoliniumGameTests() { }

    @GameTest(template = "empty")
    public static void readinessRequiresExactNineGroupedComponents(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        NukeSoliniumBlockEntity bomb = bomb(helper, pos);
        check(helper, !bomb.isReady(), "Empty Blue Rinse must not be ready");
        fillBomb(bomb);
        check(helper, bomb.isReady(),
                "Blue Rinse must arm with four igniters (0/3/5/8), four propellant (1/2/6/7) and one core (4)");

        bomb.setItem(4, ItemStack.EMPTY);
        check(helper, !bomb.isReady(), "A missing core must make the Blue Rinse unready");
        fillBomb(bomb);

        // Wrong grouping: a propellant placed in an igniter slot must not arm.
        bomb.setItem(0, new ItemStack(ModItems.SOLINIUM_PROPELLANT.get()));
        check(helper, !bomb.isReady(), "A propellant in an igniter slot must not arm the Blue Rinse");
        fillBomb(bomb);

        // Wrong grouping: an igniter in the core slot must not arm.
        bomb.setItem(4, new ItemStack(ModItems.SOLINIUM_IGNITER.get()));
        check(helper, !bomb.isReady(), "An igniter in the core slot must not arm the Blue Rinse");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inventoryPersistsWithStackLimitSixtyFour(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        NukeSoliniumBlockEntity bomb = bomb(helper, pos);
        bomb.setItem(0, new ItemStack(ModItems.SOLINIUM_IGNITER.get(), 64));
        check(helper, bomb.getItem(0).getCount() == 64,
                "Blue Rinse slots must accept the source stack limit of 64");
        bomb.setItem(4, new ItemStack(ModItems.SOLINIUM_CORE.get()));
        var tag = bomb.saveWithoutMetadata(helper.getLevel().registryAccess());
        NukeSoliniumBlockEntity loaded = new NukeSoliniumBlockEntity(helper.absolutePos(pos), helper.getBlockState(pos));
        loaded.loadWithComponents(tag, helper.getLevel().registryAccess());
        check(helper, loaded.getItem(0).is(ModItems.SOLINIUM_IGNITER.get())
                        && loaded.getItem(0).getCount() == 64
                        && loaded.getItem(4).is(ModItems.SOLINIUM_CORE.get()),
                "Blue Rinse inventory must persist through save/load");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void redstoneDetonatesOnlyWhenReady(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        // Powered but not ready: the block must stay intact and spawn nothing.
        helper.setBlock(pos.above(), Blocks.REDSTONE_BLOCK);
        check(helper, !helper.getBlockState(pos).isAir(), "An unarmed powered Blue Rinse must not detonate");
        check(helper, explosions(helper, pos).isEmpty() && clouds(helper, pos).isEmpty(),
                "An unarmed powered Blue Rinse must spawn no blast systems");
        helper.setBlock(pos.above(), Blocks.AIR);

        // Arm it, then re-power it: it must detonate.
        fillBomb(bomb(helper, pos));
        helper.setBlock(pos.above(), Blocks.REDSTONE_BLOCK);
        check(helper, helper.getBlockState(pos).isAir(), "A ready powered Blue Rinse must remove its block");
        check(helper, explosions(helper, pos).size() == 1 && clouds(helper, pos).size() == 1,
                "Redstone detonation must spawn one SOLINIUM blast and one teal cloud");
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
                "An unarmed Blue Rinse must report ERROR_MISSING_COMPONENT to a remote detonator");
        check(helper, !helper.getBlockState(pos).isAir(), "A failed remote trigger must leave the block intact");

        fillBomb(bomb(helper, pos));
        RemoteDetonation.Attempt armed = RemoteDetonation.trigger(level, absolute);
        check(helper, armed.compatible() && armed.result() == DetonationResult.DETONATED,
                "A ready Blue Rinse must report DETONATED to a remote detonator");
        check(helper, helper.getBlockState(pos).isAir(), "A successful remote trigger must remove the block");
        discardBlast(helper, pos);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void detonationConsumesComponentsWithoutDrops(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        fillBomb(bomb(helper, pos));
        check(helper, ModBlocks.NUKE_SOLINIUM.get().detonate(helper.getLevel(), helper.absolutePos(pos)),
                "Ready Blue Rinse must detonate");
        check(helper, helper.getBlockState(pos).isAir(), "Detonation must remove the bomb block");
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(3.0D));
        check(helper, drops.stream().noneMatch(item -> item.getItem().is(ModItems.SOLINIUM_IGNITER.get())
                        || item.getItem().is(ModItems.SOLINIUM_PROPELLANT.get())
                        || item.getItem().is(ModItems.SOLINIUM_CORE.get())),
                "Detonating Blue Rinse must consume its parts and drop nothing");
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
        int igniters = countOf(drops, ModItems.SOLINIUM_IGNITER.get());
        int propellant = countOf(drops, ModItems.SOLINIUM_PROPELLANT.get());
        int cores = countOf(drops, ModItems.SOLINIUM_CORE.get());
        check(helper, igniters == 4 && propellant == 4 && cores == 1,
                "Breaking a loaded Blue Rinse must recover four igniters, four propellant and one core");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spawnKeepsSourcePositionAsymmetryAndRadius(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        BlockPos absolute = helper.absolutePos(pos);
        fillBomb(bomb(helper, pos));
        ModBlocks.NUKE_SOLINIUM.get().detonate(helper.getLevel(), absolute);

        SoliniumExplosionEntity explosion = explosions(helper, pos).getFirst();
        SoliniumCloudEntity cloud = clouds(helper, pos).getFirst();
        // Source igniteTestBomb: the blast is centered (block + 0.5) but the cloud sits on the raw
        // block coordinate. Preserve that asymmetry.
        check(helper, explosion.getX() == absolute.getX() + 0.5D
                        && explosion.getY() == absolute.getY() + 0.5D
                        && explosion.getZ() == absolute.getZ() + 0.5D,
                "The SOLINIUM blast must be centered on the block");
        check(helper, cloud.getX() == absolute.getX()
                        && cloud.getY() == absolute.getY()
                        && cloud.getZ() == absolute.getZ(),
                "The teal cloud must sit at the raw block coordinate, not the centered point");
        check(helper, explosion.radius() == 150 && cloud.maxAge() == 150,
                "The placed Blue Rinse must use the config radius of 150 for both blast and cloud");
        discardBlast(helper, pos);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cleanseConvertsOrganicButSparesStoneAndOre(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(3, 6, 3));
        int cx = origin.getX();
        int cy = origin.getY();
        int cz = origin.getZ();

        // Place a stack of test blocks in the centre column, all within the radius-3 cleanse span
        // (y from cy+3 down to cy-2). Grass and glowing waste convert to dirt; leaf and wood-material
        // blocks are deleted; stone and ore are the "blue rinse" survivors.
        level.setBlock(new BlockPos(cx, cy + 3, cz), ModBlocks.WASTE_EARTH.get().defaultBlockState(), 2);
        level.setBlock(new BlockPos(cx, cy + 2, cz), Blocks.IRON_ORE.defaultBlockState(), 2);
        level.setBlock(new BlockPos(cx, cy + 1, cz), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(new BlockPos(cx, cy, cz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        level.setBlock(new BlockPos(cx, cy - 1, cz), Blocks.OAK_LOG.defaultBlockState(), 2);
        level.setBlock(new BlockPos(cx, cy - 2, cz), Blocks.OAK_LEAVES.defaultBlockState(), 2);

        SoliniumExplosionEntity blast = SoliniumExplosionEntity.create(level, cx, cy, cz, 3);
        blast.tick();

        check(helper, level.getBlockState(new BlockPos(cx, cy + 3, cz)).is(Blocks.DIRT),
                "Glowing waste earth must cleanse to dirt");
        check(helper, level.getBlockState(new BlockPos(cx, cy + 2, cz)).is(Blocks.IRON_ORE),
                "Iron ore must survive the blue rinse (cleanse, not deletion)");
        check(helper, level.getBlockState(new BlockPos(cx, cy + 1, cz)).is(Blocks.STONE),
                "Stone must survive the blue rinse (cleanse, not deletion)");
        check(helper, level.getBlockState(new BlockPos(cx, cy, cz)).is(Blocks.DIRT),
                "Grass must cleanse to dirt");
        check(helper, level.getBlockState(new BlockPos(cx, cy - 1, cz)).isAir(),
                "Oak log (wood material) must be deleted by the blue rinse");
        check(helper, level.getBlockState(new BlockPos(cx, cy - 2, cz)).isAir(),
                "Oak leaves (leaf material) must be deleted by the blue rinse");
        blast.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spiralFootprintMatchesFleija(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int radius = 2;
        BlockPos solOrigin = helper.absolutePos(new BlockPos(3, 6, 3));
        BlockPos fleOrigin = helper.absolutePos(new BlockPos(3, 12, 3));

        // A single oak-log layer at each centre. FLEIJA deletes every block in a visited column and
        // SOLINIUM cleanses wood-material blocks to air, so an all-log layer produces an identical
        // air footprint iff both traverse the same spiral columns.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlock(new BlockPos(solOrigin.getX() + dx, solOrigin.getY(), solOrigin.getZ() + dz),
                        Blocks.OAK_LOG.defaultBlockState(), 2);
                level.setBlock(new BlockPos(fleOrigin.getX() + dx, fleOrigin.getY(), fleOrigin.getZ() + dz),
                        Blocks.OAK_LOG.defaultBlockState(), 2);
            }
        }

        SoliniumExplosionEntity sol = SoliniumExplosionEntity.create(
                level, solOrigin.getX(), solOrigin.getY(), solOrigin.getZ(), radius);
        sol.tick();
        FleijaExplosionEntity fle = FleijaExplosionEntity.create(
                level, fleOrigin.getX(), fleOrigin.getY(), fleOrigin.getZ(), radius);
        fle.tick();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean solAir = level.getBlockState(
                        new BlockPos(solOrigin.getX() + dx, solOrigin.getY(), solOrigin.getZ() + dz)).isAir();
                boolean fleAir = level.getBlockState(
                        new BlockPos(fleOrigin.getX() + dx, fleOrigin.getY(), fleOrigin.getZ() + dz)).isAir();
                check(helper, solAir == fleAir,
                        "SOLINIUM spiral footprint must match FLEIJA at offset " + dx + "," + dz);
            }
        }
        // Sanity: the footprint is non-trivial (centre cleansed, the (2,0) column outside radius^2 spared).
        check(helper, level.getBlockState(solOrigin).isAir(), "The centre column must be cleansed");
        check(helper, level.getBlockState(new BlockPos(solOrigin.getX() + 2, solOrigin.getY(), solOrigin.getZ()))
                        .is(Blocks.OAK_LOG),
                "A column at distance 2 (outside radius^2) must be untouched");
        sol.discard();
        fle.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void radiationContaminationInterpolatesFromCentreToEdge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int radius = 4;
        BlockPos relative = new BlockPos(3, 6, 3);
        BlockPos base = helper.absolutePos(relative);
        double cx = base.getX() + 0.5D;
        double cy = base.getY() + 0.5D;
        double cz = base.getZ() + 0.5D;

        Pig center = helper.spawnWithNoFreeWill(EntityType.PIG, relative);
        center.moveTo(cx, cy, cz, 0.0F, 0.0F);
        Pig mid = helper.spawnWithNoFreeWill(EntityType.PIG, relative);
        mid.moveTo(cx + 3.5D, cy, cz, 0.0F, 0.0F);
        Pig outside = helper.spawnWithNoFreeWill(EntityType.PIG, relative);
        outside.moveTo(cx + 10.0D, cy, cz, 0.0F, 0.0F);

        SoliniumExplosionEntity blast = SoliniumExplosionEntity.create(level, cx, cy, cz, radius);
        blast.tick();

        // radEnv accumulates the raw, pre-clamp contamination dose (radiation() clamps at 2500).
        float centreDose = RadiationSystem.data(center).radEnv();
        float midDose = RadiationSystem.data(mid).radEnv();
        float outsideDose = RadiationSystem.data(outside).radEnv();

        // dist 0 -> 250000 (inner); dist 3.5 -> 15000 + 235000 * 0.125 = 44375; dist 10 -> out of range.
        check(helper, centreDose > 249000.0F,
                "An entity at the centre must receive the full 250000 contamination");
        check(helper, midDose > 15000.0F && midDose < centreDose,
                "A mid-range entity must receive an interpolated dose between the edge value and the centre");
        check(helper, outsideDose == 0.0F,
                "An entity beyond the radius must receive no contamination");
        blast.discard();
        center.discard();
        mid.discard();
        outside.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cloudSpawnsLightningEachTickAndExpires(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(3, 4, 3));
        SoliniumCloudEntity cloud = SoliniumCloudEntity.create(level, base.getX(), base.getY(), base.getZ(), 3);
        level.addFreshEntity(cloud);

        int before = lightningCount(level, base);
        cloud.tick();
        check(helper, lightningCount(level, base) == before + 1,
                "The teal cloud must spawn a lightning bolt every tick");
        check(helper, cloud.age() == 1, "The cloud age must advance each tick");

        cloud.tick();
        cloud.tick();
        check(helper, cloud.isRemoved(), "The cloud must expire once it reaches its max age");
        // Clean up the lightning bolts spawned by the cloud.
        level.getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class,
                new AABB(base).inflate(2.0D, 260.0D, 2.0D)).forEach(Entity::discard);
        helper.succeed();
    }

    private static int lightningCount(ServerLevel level, BlockPos base) {
        return level.getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class,
                new AABB(base).inflate(2.0D, 260.0D, 2.0D)).size();
    }

    private static BlockPos placeBomb(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.NUKE_SOLINIUM.get().defaultBlockState()
                .setValue(NukeSoliniumBlock.FACING, Direction.SOUTH));
        return pos;
    }

    private static NukeSoliniumBlockEntity bomb(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof NukeSoliniumBlockEntity bomb) return bomb;
        helper.fail("Expected Blue Rinse block entity");
        throw new IllegalStateException();
    }

    private static void fillBomb(NukeSoliniumBlockEntity bomb) {
        bomb.setItem(0, new ItemStack(ModItems.SOLINIUM_IGNITER.get()));
        bomb.setItem(1, new ItemStack(ModItems.SOLINIUM_PROPELLANT.get()));
        bomb.setItem(2, new ItemStack(ModItems.SOLINIUM_PROPELLANT.get()));
        bomb.setItem(3, new ItemStack(ModItems.SOLINIUM_IGNITER.get()));
        bomb.setItem(4, new ItemStack(ModItems.SOLINIUM_CORE.get()));
        bomb.setItem(5, new ItemStack(ModItems.SOLINIUM_IGNITER.get()));
        bomb.setItem(6, new ItemStack(ModItems.SOLINIUM_PROPELLANT.get()));
        bomb.setItem(7, new ItemStack(ModItems.SOLINIUM_PROPELLANT.get()));
        bomb.setItem(8, new ItemStack(ModItems.SOLINIUM_IGNITER.get()));
    }

    private static List<SoliniumExplosionEntity> explosions(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(SoliniumExplosionEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(3.0D));
    }

    private static List<SoliniumCloudEntity> clouds(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(SoliniumCloudEntity.class,
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
