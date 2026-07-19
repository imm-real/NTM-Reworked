package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.BombThermoBlock;
import com.hbm.ntm.explosion.DetonationResult;
import com.hbm.ntm.explosion.ExplosionThermo;
import com.hbm.ntm.explosion.RemoteDetonatable;
import com.hbm.ntm.explosion.RemoteDetonation;
import com.hbm.ntm.recipe.AssemblyRecipe;
import com.hbm.ntm.recipe.AssemblyRecipes;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Focused tests for the two Thermobaric Bombs ({@code hbm:therm_endo}/{@code hbm:therm_exo}). The
 * source freeze/scorch conversions scan a 60^3 = 216,000-cell sphere and the freezer/fire passes edit
 * a wide entity box, so the terrain/entity behavior is exercised by calling the {@link ExplosionThermo}
 * helpers directly at explicit safe world coordinates (high in void air) inside their own isolated
 * batches, cleaning up everything they touch — mirroring the Levitation Bomb tests.
 */
@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThermoBombGameTests {
    private ThermoBombGameTests() {
    }

    @GameTest(template = "empty")
    public static void propertiesMatchSource(GameTestHelper helper) {
        BombThermoBlock endo = ModBlocks.THERM_ENDO.get();
        BombThermoBlock exo = ModBlocks.THERM_EXO.get();
        check(helper, endo.getExplosionResistance() == 120.0F && exo.getExplosionResistance() == 120.0F,
                "Source setResistance(200.0F) must map to modern explosion resistance 120 for both bombs");
        check(helper, endo instanceof RemoteDetonatable && exo instanceof RemoteDetonatable,
                "Source BombThermo implements IBomb, so both thermo bombs must be remotely detonatable");
        check(helper, BuiltInRegistries.BLOCK.getKey(endo).equals(hbm("therm_endo"))
                        && BuiltInRegistries.BLOCK.getKey(exo).equals(hbm("therm_exo")),
                "The blocks must keep the source registry ids hbm:therm_endo / hbm:therm_exo");
        check(helper, endo.defaultBlockState().getRenderShape() == RenderShape.MODEL,
                "The thermo bombs are full opaque model cubes");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "thermo_freeze_isolated")
    public static void freezeConvertsTerrain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = helper.absolutePos(new BlockPos(2, 40, 2));
        int x = c.getX();
        int y = c.getY();
        int z = c.getZ();

        BlockPos stone = new BlockPos(x + 1, y, z);
        BlockPos water = new BlockPos(x - 1, y, z);
        BlockPos grass = new BlockPos(x, y, z + 1);
        BlockPos dirt = new BlockPos(x, y, z - 1);
        BlockPos log = new BlockPos(x + 1, y, z + 1);
        BlockPos leaves = new BlockPos(x - 1, y, z - 1);

        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(water, Blocks.WATER.defaultBlockState(), 2);
        level.setBlock(grass, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        level.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(log, Blocks.OAK_LOG.defaultBlockState(), 2);
        level.setBlock(leaves, Blocks.OAK_LEAVES.defaultBlockState(), 2);

        ExplosionThermo.freeze(level, x, y, z, 3);

        check(helper, level.getBlockState(stone).is(Blocks.PACKED_ICE), "stone must freeze to packed ice");
        check(helper, level.getBlockState(water).is(Blocks.ICE), "water must freeze to ice");
        check(helper, level.getBlockState(grass).is(ModBlocks.FROZEN_GRASS.get()),
                "grass must freeze to frozen grass");
        check(helper, level.getBlockState(dirt).is(ModBlocks.FROZEN_DIRT.get()),
                "dirt must freeze to frozen dirt");
        check(helper, level.getBlockState(log).is(ModBlocks.FROZEN_LOG.get()),
                "an oak log must freeze to a frozen log");
        check(helper, level.getBlockState(leaves).is(Blocks.SNOW_BLOCK),
                "leaves must freeze to a full snow block (not a snow layer)");

        clear(level, stone, water, grass, dirt, log, leaves);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "thermo_scorch_isolated")
    public static void scorchConvertsTerrain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = helper.absolutePos(new BlockPos(2, 40, 2));
        int x = c.getX();
        int y = c.getY();
        int z = c.getZ();

        BlockPos stone = new BlockPos(x + 1, y, z);
        BlockPos grass = new BlockPos(x, y, z + 1);
        BlockPos log = new BlockPos(x - 1, y, z);
        BlockPos leaves = new BlockPos(x, y, z - 1);
        BlockPos water = new BlockPos(x + 1, y, z + 1);

        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(grass, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        level.setBlock(log, Blocks.OAK_LOG.defaultBlockState(), 2);
        level.setBlock(leaves, Blocks.OAK_LEAVES.defaultBlockState(), 2);
        level.setBlock(water, Blocks.WATER.defaultBlockState(), 2);

        ExplosionThermo.scorch(level, x, y, z, 3);

        check(helper, level.getBlockState(stone).is(Blocks.LAVA), "stone must scorch to lava");
        check(helper, level.getBlockState(grass).is(Blocks.DIRT), "grass must scorch to dirt");
        check(helper, level.getBlockState(log).is(ModBlocks.WASTE_LOG.get()),
                "an oak log must scorch to a charred log");
        check(helper, level.getBlockState(leaves).isAir(), "leaves must scorch to air");
        check(helper, level.getBlockState(water).isAir(), "water must scorch to air");

        clear(level, stone, grass, log, leaves, water);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "thermo_freezer_isolated")
    public static void freezerEncasesLivingButNotCats(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = helper.absolutePos(new BlockPos(2, 40, 2));
        int x = c.getX();
        int y = c.getY();
        int z = c.getZ();

        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 2, 2));
        pig.moveTo(x + 0.5D, y, z + 0.5D, 0.0F, 0.0F);

        Cat cat = helper.spawnWithNoFreeWill(EntityType.CAT, new BlockPos(2, 2, 2));
        cat.moveTo(x + 3.5D, y, z + 0.5D, 0.0F, 0.0F);

        ExplosionThermo.freezer(level, x, y, z, 20);

        MobEffectInstance weakness = pig.getEffect(MobEffects.WEAKNESS);
        MobEffectInstance slowness = pig.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        MobEffectInstance fatigue = pig.getEffect(MobEffects.DIG_SLOWDOWN);
        check(helper, weakness != null && weakness.getAmplifier() == 4 && weakness.getDuration() == 2 * 60 * 20,
                "A frozen living entity must get Weakness amp 4 for 2400 ticks");
        check(helper, slowness != null && slowness.getAmplifier() == 2 && slowness.getDuration() == 90 * 20,
                "A frozen living entity must get Slowness amp 2 for 1800 ticks");
        check(helper, fatigue != null && fatigue.getAmplifier() == 2 && fatigue.getDuration() == 3 * 60 * 20,
                "A frozen living entity must get Mining Fatigue amp 2 for 3600 ticks");
        // Ice box: X in [px-2, px], Y in [py, py+2], Z in [pz-1, pz+1]. (x-1, y, z) is inside it.
        check(helper, level.getBlockState(new BlockPos(x - 1, y, z)).is(Blocks.ICE),
                "A frozen living entity must be encased in the source asymmetric 3x3x3 ice box");

        check(helper, cat.getEffect(MobEffects.WEAKNESS) == null,
                "Cats (source ocelot immunity) must never be frozen");

        pig.discard();
        cat.discard();
        for (int a = x - 2; a <= x; a++) {
            for (int b = y; b <= y + 2; b++) {
                for (int d = z - 1; d <= z + 1; d++) {
                    level.setBlock(new BlockPos(a, b, d), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "thermo_fire_isolated")
    public static void setEntitiesOnFireIgnitesAndWeakens(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = helper.absolutePos(new BlockPos(2, 40, 2));
        int x = c.getX();
        int y = c.getY();
        int z = c.getZ();

        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 2, 2));
        pig.moveTo(x + 0.5D, y, z + 0.5D, 0.0F, 0.0F);

        ExplosionThermo.setEntitiesOnFire(level, x, y, z, 20);

        check(helper, pig.getRemainingFireTicks() > 0,
                "An entity in range must be set on fire (source setFire(10))");
        MobEffectInstance weakness = pig.getEffect(MobEffects.WEAKNESS);
        check(helper, weakness != null && weakness.getAmplifier() == 4 && weakness.getDuration() == 15 * 20,
                "A living entity in range must also get Weakness amp 4 for 300 ticks");

        pig.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "thermo_endo_redstone_isolated")
    public static void redstoneDetonatesEndoAndRemovesBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bomb = helper.absolutePos(new BlockPos(2, 40, 2));
        level.setBlock(bomb, ModBlocks.THERM_ENDO.get().defaultBlockState(), 3);
        check(helper, level.getBlockState(bomb).is(ModBlocks.THERM_ENDO.get()),
                "The Endothermic Bomb must place without self-triggering");

        level.setBlock(bomb.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);

        check(helper, level.getBlockState(bomb).isAir(),
                "An indirectly powered Endothermic Bomb must detonate instantly and remove its own block");

        clear(level, bomb, bomb.above());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "thermo_exo_remote_isolated")
    public static void remoteDetonationExoReturnsDetonated(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bomb = helper.absolutePos(new BlockPos(2, 40, 2));
        level.setBlock(bomb, ModBlocks.THERM_EXO.get().defaultBlockState(), 2);

        RemoteDetonation.Attempt attempt = RemoteDetonation.trigger(level, bomb);
        check(helper, attempt.compatible() && attempt.result() == DetonationResult.DETONATED,
                "BombThermo implements IBomb, so a remote detonator must fire the Exothermic Bomb and get DETONATED");
        check(helper, level.getBlockState(bomb).isAir(),
                "A successful remote detonation must remove the bomb block");

        clear(level, bomb);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void frozenBlocksDropSnowballs(GameTestHelper helper) {
        check(helper, destroyAndCountDrops(helper, new BlockPos(2, 2, 2),
                ModBlocks.FROZEN_DIRT.get(), Items.SNOWBALL) == 1,
                "Breaking frozen dirt must drop exactly one snowball");
        check(helper, destroyAndCountDrops(helper, new BlockPos(4, 2, 2),
                ModBlocks.FROZEN_PLANKS.get(), Items.SNOWBALL) == 1,
                "Breaking frozen planks must drop exactly one snowball");
        check(helper, destroyAndCountDrops(helper, new BlockPos(2, 2, 4),
                ModBlocks.FROZEN_GRASS.get(), Items.SNOWBALL) == 1,
                "Breaking frozen grass must drop exactly one snowball");
        int logDrop = destroyAndCountDrops(helper, new BlockPos(4, 2, 4),
                ModBlocks.FROZEN_LOG.get(), Items.SNOWBALL);
        check(helper, logDrop >= 2 && logDrop <= 4,
                "Breaking a frozen log must drop 2..4 snowballs (source quantityDropped 2 + rand(3))");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void frozenDirtAndGrassSlowWalkers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos dirt = new BlockPos(2, 2, 2);
        helper.setBlock(dirt, ModBlocks.FROZEN_DIRT.get());
        Pig pigOnDirt = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 3, 2));
        ModBlocks.FROZEN_DIRT.get().stepOn(level, helper.absolutePos(dirt),
                helper.getBlockState(dirt), pigOnDirt);
        MobEffectInstance dirtSlow = pigOnDirt.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        check(helper, dirtSlow != null && dirtSlow.getAmplifier() == 2 && dirtSlow.getDuration() == 2 * 60 * 20,
                "Walking on frozen dirt must apply Slowness amp 2 for 2400 ticks");

        BlockPos grass = new BlockPos(4, 2, 4);
        helper.setBlock(grass, ModBlocks.FROZEN_GRASS.get());
        Pig pigOnGrass = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(4, 3, 4));
        ModBlocks.FROZEN_GRASS.get().stepOn(level, helper.absolutePos(grass),
                helper.getBlockState(grass), pigOnGrass);
        MobEffectInstance grassSlow = pigOnGrass.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        check(helper, grassSlow != null && grassSlow.getAmplifier() == 2 && grassSlow.getDuration() == 2 * 60 * 20,
                "Walking on frozen grass must apply Slowness amp 2 for 2400 ticks");

        pigOnDirt.discard();
        pigOnGrass.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void assemblyRecipesMatchSource(GameTestHelper helper) {
        // Exothermic Bomb: 12 titanium plates, 32 red phosphorus dust, 1 advanced circuit, 4 gold coils.
        AssemblyRecipe exo = AssemblyRecipes.byName("ass.exobomb");
        check(helper, exo != null, "Datapack Assembly recipe hbm:ass.exobomb must load");
        check(helper, exo.duration() == 200 && exo.power() == 100L, "ass.exobomb must be 200 ticks at 100 HE/t");
        check(helper, exo.inputs().size() == 4
                        && exo.inputs().get(0).count() == 12
                        && exo.inputs().get(1).count() == 32
                        && exo.inputs().get(2).count() == 1
                        && exo.inputs().get(3).count() == 4,
                "ass.exobomb must require 12 Titanium Plates, 32 Red Phosphorus, 1 circuit, 4 Gold Coils");
        check(helper, exo.inputs().get(1).matches(
                        new ItemStack(ModItems.legacyOreResourceItem("powder_fire").get(), 32)),
                "ass.exobomb's dust slot (c:dusts/red_phosphorus) must resolve to powder_fire");
        check(helper, exo.inputs().get(3).matches(new ItemStack(ModItems.COIL_GOLD.get(), 4)),
                "ass.exobomb's coil slot must be the Gold Coil");
        check(helper, exo.output().is(ModItems.THERM_EXO_ITEM.get()) && exo.output().getCount() == 1,
                "ass.exobomb must output exactly one Exothermic Bomb");

        // Endothermic Bomb: 12 titanium plates, 32 Cryo Powder, 1 advanced circuit, 4 gold coils.
        AssemblyRecipe endo = AssemblyRecipes.byName("ass.endobomb");
        check(helper, endo != null, "Datapack Assembly recipe hbm:ass.endobomb must load");
        check(helper, endo.duration() == 200 && endo.power() == 100L, "ass.endobomb must be 200 ticks at 100 HE/t");
        check(helper, endo.inputs().size() == 4
                        && endo.inputs().get(0).count() == 12
                        && endo.inputs().get(1).count() == 32
                        && endo.inputs().get(2).count() == 1
                        && endo.inputs().get(3).count() == 4,
                "ass.endobomb must require 12 Titanium Plates, 32 Cryo Powder, 1 circuit, 4 Gold Coils");
        check(helper, endo.inputs().get(1).matches(
                        new ItemStack(ModItems.legacyOreResourceItem("powder_ice").get(), 32)),
                "ass.endobomb's powder slot must be Cryo Powder (hbm:powder_ice)");
        check(helper, endo.output().is(ModItems.THERM_ENDO_ITEM.get()) && endo.output().getCount() == 1,
                "ass.endobomb must output exactly one Endothermic Bomb");
        helper.succeed();
    }

    private static int destroyAndCountDrops(GameTestHelper helper, BlockPos relative,
                                            net.minecraft.world.level.block.Block block, Item drop) {
        helper.setBlock(relative, block);
        BlockPos abs = helper.absolutePos(relative);
        helper.getLevel().destroyBlock(abs, true);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(abs).inflate(1.5D));
        return drops.stream().filter(entity -> entity.getItem().is(drop))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static ResourceLocation hbm(String path) {
        return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path);
    }

    private static void clear(ServerLevel level, BlockPos... positions) {
        for (BlockPos pos : positions) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
