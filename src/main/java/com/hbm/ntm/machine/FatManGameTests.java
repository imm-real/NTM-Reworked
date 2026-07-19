package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.NukeManBlock;
import com.hbm.ntm.blockentity.NukeManBlockEntity;
import com.hbm.ntm.nuclear.MushroomCloudEntity;
import com.hbm.ntm.nuclear.NuclearExplosionEntity;
import com.hbm.ntm.nuclear.NuclearRayExplosion;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FatManGameTests {
    private FatManGameTests() { }

    @GameTest(template = "empty")
    public static void readinessRequiresExactSixComponents(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        NukeManBlockEntity bomb = bomb(helper, pos);
        check(helper, !bomb.isReady(), "Empty Fat Man must not be ready");
        fillBomb(bomb);
        check(helper, bomb.isReady(), "Fat Man must become ready with igniter, four early lenses and core");
        bomb.setItem(3, new ItemStack(Blocks.DIRT));
        check(helper, !bomb.isReady(), "Any incorrect component must make the Fat Man unready");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inventoryPersistsAndKeepsStackLimitOne(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        NukeManBlockEntity bomb = bomb(helper, pos);
        bomb.setItem(0, new ItemStack(ModItems.MAN_IGNITER.get(), 8));
        bomb.setItem(5, new ItemStack(ModItems.MAN_CORE.get()));
        check(helper, bomb.getItem(0).getCount() == 1, "Fat Man component slots must clamp to one item");
        CompoundTag tag = bomb.saveWithoutMetadata(helper.getLevel().registryAccess());
        NukeManBlockEntity loaded = new NukeManBlockEntity(helper.absolutePos(pos), helper.getBlockState(pos));
        loaded.loadWithComponents(tag, helper.getLevel().registryAccess());
        check(helper, loaded.getItem(0).is(ModItems.MAN_IGNITER.get())
                        && loaded.getItem(5).is(ModItems.MAN_CORE.get()),
                "Fat Man inventory must persist through save/load");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingUnarmedBombDropsStoredComponents(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        NukeManBlockEntity bomb = bomb(helper, pos);
        fillBomb(bomb);
        helper.setBlock(pos, Blocks.AIR);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(2.0D));
        check(helper, drops.stream().filter(item -> item.getItem().is(ModItems.EARLY_EXPLOSIVE_LENSES.get()))
                        .mapToInt(item -> item.getItem().getCount()).sum() == 4,
                "Breaking Fat Man must recover all four lens arrays");
        check(helper, drops.stream().anyMatch(item -> item.getItem().is(ModItems.MAN_IGNITER.get())),
                "Breaking Fat Man must recover the firing unit");
        check(helper, drops.stream().anyMatch(item -> item.getItem().is(ModItems.MAN_CORE.get())),
                "Breaking Fat Man must recover the plutonium core");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void detonationConsumesComponentsAndSpawnsFullRadiusSystems(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        NukeManBlockEntity bomb = bomb(helper, pos);
        fillBomb(bomb);
        check(helper, ModBlocks.NUKE_MAN.get().detonate(helper.getLevel(), helper.absolutePos(pos)),
                "Ready Fat Man must detonate");
        check(helper, helper.getBlockState(pos).isAir(), "Detonation must remove the bomb block");
        List<NuclearExplosionEntity> explosions = helper.getLevel().getEntitiesOfClass(NuclearExplosionEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(3.0D));
        List<MushroomCloudEntity> clouds = helper.getLevel().getEntitiesOfClass(MushroomCloudEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(3.0D));
        check(helper, explosions.size() == 1 && explosions.getFirst().strength() == 350
                        && explosions.getFirst().length() == 175 && explosions.getFirst().speed() == 286,
                "Default Fat Man must use MK5 strength 350, length 175 and speed 286");
        check(helper, clouds.size() == 1 && clouds.getFirst().maxAge() == 1461,
                "Default Fat Man must create the original approximately 73-second Torex lifetime");
        MushroomCloudEntity cloud = clouds.getFirst();
        check(helper, cloud.fireImmune() && NuclearExplosionEntity.isNuclearProcessEntity(cloud),
                "Mushroom cloud must be exempt from MK5 damage and ignition");
        cloud.igniteForSeconds(5.0F);
        check(helper, !cloud.displayFireAnimation(),
                "Mushroom cloud must never render Minecraft's entity fire overlay");
        explosions.forEach(NuclearExplosionEntity::discard);
        clouds.forEach(MushroomCloudEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void batchedRayExplosionDestroysWithoutDrops(GameTestHelper helper) {
        BlockPos center = new BlockPos(5, 3, 5);
        for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) {
            helper.setBlock(center.offset(x, y, z), Blocks.STONE);
        }
        BlockPos absolute = helper.absolutePos(center);
        NuclearRayExplosion explosion = new NuclearRayExplosion(helper.getLevel(),
                absolute.getX(), absolute.getY(), absolute.getZ(), 20, 10_000, 4);
        int guard = 0;
        while (!explosion.isComplete() && guard++ < 20) {
            explosion.cacheTick();
            explosion.destructionTick(1_000);
        }
        check(helper, explosion.isComplete(), "Small test MK5 ray explosion must complete");
        check(helper, helper.getBlockState(center).isAir(), "MK5 rays must remove center terrain");
        check(helper, helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        new AABB(absolute).inflate(4.0D)).stream()
                .noneMatch(item -> item.getItem().is(net.minecraft.world.item.Items.COBBLESTONE)),
                "MK5 crater destruction must create no terrain drops");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void batchedRayExplosionDrainsFluidSheetsAcrossChunks(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int relativeX = 16 - Math.floorMod(origin.getX(), 16);
        int relativeZ = 16 - Math.floorMod(origin.getZ(), 16);
        BlockPos center = new BlockPos(relativeX, 4, relativeZ);
        BlockPos absolute = helper.absolutePos(center);
        for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) {
            helper.getLevel().setBlock(absolute.offset(x, y, z), Blocks.STONE.defaultBlockState(), 2);
        }

        NuclearRayExplosion explosion = new NuclearRayExplosion(helper.getLevel(),
                absolute.getX(), absolute.getY(), absolute.getZ(), 20, 10_000, 12);
        while (explosion.generatedRays() < explosion.totalRays()) explosion.cacheTick();
        while (explosion.cachedChunkCount() > 0) explosion.destructionTick(1_000);

        check(helper, !explosion.isComplete(),
                "A blast crossing chunks must remain alive for the modern fluid settling passes");
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
            helper.getLevel().setBlock(absolute.offset(x, 0, z), Blocks.WATER.defaultBlockState(), 2);
        }
        for (int i = 0; i < 20 && !explosion.isComplete(); i++) explosion.destructionTick(1_000);
        check(helper, explosion.isComplete(), "Cross-chunk fluid cleanup must finish within its bounded pass count");
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
            check(helper, helper.getLevel().getFluidState(absolute.offset(x, 0, z)).isEmpty(),
                    "The blast must drain fluid inside chunk interiors as well as along chunk seams");
        }
        helper.succeed();
    }

    private static BlockPos placeBomb(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.NUKE_MAN.get().defaultBlockState()
                .setValue(NukeManBlock.FACING, net.minecraft.core.Direction.SOUTH));
        return pos;
    }

    private static NukeManBlockEntity bomb(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof NukeManBlockEntity bomb) return bomb;
        helper.fail("Expected Fat Man block entity");
        throw new IllegalStateException();
    }

    private static void fillBomb(NukeManBlockEntity bomb) {
        bomb.setItem(0, new ItemStack(ModItems.MAN_IGNITER.get()));
        for (int slot = 1; slot <= 4; slot++) bomb.setItem(slot, new ItemStack(ModItems.EARLY_EXPLOSIVE_LENSES.get()));
        bomb.setItem(5, new ItemStack(ModItems.MAN_CORE.get()));
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
