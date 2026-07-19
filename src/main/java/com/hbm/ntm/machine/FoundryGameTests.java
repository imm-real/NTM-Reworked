package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.anvil.AnvilRecipes;
import com.hbm.ntm.block.CrucibleBlock;
import com.hbm.ntm.block.StoneResourceBlock;
import com.hbm.ntm.blockentity.CrucibleBlockEntity;
import com.hbm.ntm.blockentity.CrucibleProxyBlockEntity;
import com.hbm.ntm.blockentity.FoundryMoldBlockEntity;
import com.hbm.ntm.blockentity.FoundryChannelBlockEntity;
import com.hbm.ntm.blockentity.FoundryTankBlockEntity;
import com.hbm.ntm.blockentity.FoundryOutletBlockEntity;
import com.hbm.ntm.blockentity.DynamicSlagBlockEntity;
import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.foundry.FoundryMaterial;
import com.hbm.ntm.hazard.HazardCarrier;
import com.hbm.ntm.item.BoltItem;
import com.hbm.ntm.item.CastPlateItem;
import com.hbm.ntm.item.CircuitItem;
import com.hbm.ntm.item.FoundryMoldItem;
import com.hbm.ntm.item.FoundryIngotItem;
import com.hbm.ntm.item.FoundryScrapsItem;
import com.hbm.ntm.item.OreChunkItem;
import com.hbm.ntm.item.PipeItem;
import com.hbm.ntm.item.StoneResourceBlockItem;
import com.hbm.ntm.inventory.AnvilMenu;
import com.hbm.ntm.pollution.PollutionData;
import com.hbm.ntm.recipe.AssemblyRecipes;
import com.hbm.ntm.recipe.CrucibleRecipes;
import com.hbm.ntm.recipe.ShredderRecipes;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.worldgen.ConfigOreCountPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FoundryGameTests {
    private FoundryGameTests() { }

    @GameTest(template = "empty")
    public static void boundedMaterialMappingsPreserveSourceQuanta(GameTestHelper helper) {
        check(helper, amount(new ItemStack(Items.IRON_NUGGET)) == 8, "Iron nugget must equal 8 quanta");
        check(helper, amount(new ItemStack(Items.IRON_INGOT)) == 72, "Iron ingot must equal 72 quanta");
        check(helper, amount(new ItemStack(Items.COPPER_INGOT)) == 72, "Copper ingot must equal 72 quanta");
        check(helper, amount(new ItemStack(Items.COPPER_BLOCK)) == 648, "Copper block must equal 648 quanta");
        check(helper, amount(new ItemStack(Items.CHARCOAL)) == 24, "Charcoal must equal 3 carbon nuggets");
        check(helper, amount(new ItemStack(Items.COAL)) == 72, "Coal gem must equal one carbon ingot");
        check(helper, amount(new ItemStack(ModItems.POWDER_FLUX.get())) == 72, "Flux dust must equal one ingot shape");
        check(helper, amount(new ItemStack(ModItems.getBlockItem("block_steel").get())) == 648,
                "Steel block lookup must use the block-item registry and equal 648 quanta");
        check(helper, amount(BoltItem.create(ModItems.BOLT.get(), BoltItem.BoltMaterial.STEEL, 1)) == 9,
                "Steel Bolt must equal 9 quanta");
        for (String id : new String[]{"powder_flux", "ball_fireclay_from_aluminum_dust",
                "ball_fireclay_from_aluminum_ore", "ball_fireclay_from_limestone",
                "ingot_firebrick_from_fireclay", "mold_base", "foundry_mold", "foundry_basin",
                "foundry_channel", "foundry_tank", "foundry_outlet", "foundry_slagtap", "screwdriver"}) {
            check(helper, helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id)).isPresent(),
                    "Foundry bootstrap recipe hbm:" + id + " must load");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void crucibleUsesEighteenPartsAndForwardsAutomation(GameTestHelper helper) {
        BlockPos coreRelative = new BlockPos(4, 1, 4);
        CrucibleBlock block = ModBlocks.MACHINE_CRUCIBLE.get();
        var state = block.defaultBlockState().setValue(CrucibleBlock.FACING, Direction.NORTH);
        helper.setBlock(coreRelative, state);
        BlockPos core = helper.absolutePos(coreRelative);
        block.setPlacedBy(helper.getLevel(), core, state, helper.makeMockPlayer(GameType.SURVIVAL),
                new ItemStack(ModItems.MACHINE_CRUCIBLE_ITEM.get()));
        int proxies = 0;
        for (BlockPos part : CrucibleBlock.partPositions(core)) {
            check(helper, helper.getLevel().getBlockState(part).is(ModBlocks.MACHINE_CRUCIBLE.get()),
                    "Every 3x2x3 Crucible cell must use the shared block identity");
            if (helper.getLevel().getBlockEntity(part) instanceof CrucibleProxyBlockEntity) proxies++;
        }
        check(helper, proxies == 17, "Crucible must have one core and seventeen proxy block entities");
        BlockPos proxy = core.offset(1, 0, 1);
        check(helper, helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, proxy, Direction.UP) != null,
                "Crucible proxies must forward the nine input slots");
        helper.getLevel().destroyBlock(proxy, false);
        for (BlockPos part : CrucibleBlock.partPositions(core)) check(helper, helper.getLevel().getBlockState(part).isAir(),
                "Breaking any Crucible cell must dismantle the entire multiblock");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void steelRecipeUsesExactRatioFrequencyAndDirectPour(GameTestHelper helper) {
        BlockPos cruciblePos = helper.absolutePos(new BlockPos(4, 2, 5));
        var crucibleState = ModBlocks.MACHINE_CRUCIBLE.get().defaultBlockState()
                .setValue(CrucibleBlock.FACING, Direction.NORTH);
        helper.getLevel().setBlock(cruciblePos, crucibleState, 3);
        CrucibleBlockEntity crucible = (CrucibleBlockEntity) helper.getLevel().getBlockEntity(cruciblePos);
        crucible.selectSteelRecipe(true);
        crucible.addMoltenForTest(true, FoundryMaterial.IRON, 16);
        crucible.addMoltenForTest(true, FoundryMaterial.CARBON, 24);
        crucible.addMoltenForTest(true, FoundryMaterial.FLUX, 8);
        check(helper, !crucible.runSteelRecipeForTest(19L) && crucible.runSteelRecipeForTest(20L),
                "Steel alloying must wait for its 20-tick frequency boundary");
        check(helper, crucible.recipeAmount(FoundryMaterial.IRON) == 0
                        && crucible.recipeAmount(FoundryMaterial.CARBON) == 0
                        && crucible.recipeAmount(FoundryMaterial.FLUX) == 0
                        && crucible.recipeAmount(FoundryMaterial.STEEL) == 16,
                "Steel alloying must consume 16 Iron + 24 Carbon + 8 Flux and produce 16 Steel on a 20-tick boundary");

        BlockPos moldPos = cruciblePos.relative(Direction.NORTH, 2);
        helper.getLevel().setBlock(moldPos, ModBlocks.FOUNDRY_MOLD.get().defaultBlockState(), 3);
        FoundryMoldBlockEntity mold = (FoundryMoldBlockEntity) helper.getLevel().getBlockEntity(moldPos);
        mold.setItem(FoundryMoldBlockEntity.MOLD, FoundryMoldItem.castPlate(ModItems.MOLD.get()));
        crucible.addMoltenForTest(true, FoundryMaterial.STEEL, 200);
        float before = PollutionData.get(helper.getLevel()).get(cruciblePos, PollutionData.Type.SOOT);
        for (int i = 0; i < 9; i++) tick(helper, crucible);
        check(helper, mold.amount() == 216 && mold.material() == FoundryMaterial.STEEL,
                "Front spout must pour Steel into a same-level mold in 24-quanta operations up to 216");
        check(helper, PollutionData.get(helper.getLevel()).get(cruciblePos, PollutionData.Type.SOOT) > before,
                "A nonempty recipe branch must emit soot even while direct pouring");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void moldCoolingPreservesInitialAndNormalCycleQuirk(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.getLevel().setBlock(pos, ModBlocks.FOUNDRY_MOLD.get().defaultBlockState(), 3);
        FoundryMoldBlockEntity mold = (FoundryMoldBlockEntity) helper.getLevel().getBlockEntity(pos);
        mold.setItem(FoundryMoldBlockEntity.MOLD, FoundryMoldItem.castPlate(ModItems.MOLD.get()));
        check(helper, mold.pour(FoundryMaterial.STEEL, 216) == 216, "Cast Plate mold must accept exactly 216 Steel quanta");
        for (int i = 0; i < 99; i++) tick(helper, mold);
        check(helper, mold.getItem(FoundryMoldBlockEntity.OUTPUT).isEmpty(), "Instantly full fresh mold must not finish before 100 ticks");
        tick(helper, mold);
        check(helper, CastPlateItem.material(mold.getItem(FoundryMoldBlockEntity.OUTPUT))
                        == CastPlateItem.CastPlateMaterial.STEEL,
                "Fresh full mold must output one component-exact Cast Steel Plate on tick 100");
        mold.removeItem(FoundryMoldBlockEntity.OUTPUT, 1);

        check(helper, mold.pour(FoundryMaterial.STEEL, 24) == 24, "Normal cycle must accept its first 24-quanta pour");
        tick(helper, mold);
        check(helper, mold.cooloff() == 200, "Any non-full tick must reset cooling to 200");
        check(helper, mold.pour(FoundryMaterial.STEEL, 192) == 192, "Normal cycle must fill to 216 without overflow");
        for (int i = 0; i < 199; i++) tick(helper, mold);
        check(helper, mold.getItem(FoundryMoldBlockEntity.OUTPUT).isEmpty(), "Normal gradual fill must not finish before 200 ticks");
        tick(helper, mold);
        check(helper, !mold.getItem(FoundryMoldBlockEntity.OUTPUT).isEmpty(), "Normal gradual fill must finish on tick 200");
        check(helper, !mold.canPlaceItemThroughFace(0, FoundryMoldItem.castPlate(ModItems.MOLD.get()), Direction.UP)
                        && mold.canTakeItemThroughFace(1, mold.getItem(1), Direction.DOWN),
                "Foundry Mold automation must expose output extraction only");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void completeMoldCatalogEnforcesShallowAndBasinSizes(GameTestHelper helper) {
        check(helper, FoundryMoldItem.Mold.values().length == 26,
                "The source foundry catalog must expose all 26 mold subtypes");
        check(helper, java.util.Arrays.stream(FoundryMoldItem.Mold.values())
                        .mapToInt(FoundryMoldItem.Mold::id).distinct().count() == 26,
                "Every source mold must retain a unique stable metadata ID");

        BlockPos shallowPos = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.getLevel().setBlock(shallowPos, ModBlocks.FOUNDRY_MOLD.get().defaultBlockState(), 3);
        FoundryMoldBlockEntity shallow = (FoundryMoldBlockEntity) helper.getLevel().getBlockEntity(shallowPos);
        ItemStack ingotMold = FoundryMoldItem.create(ModItems.MOLD.get(), FoundryMoldItem.Mold.INGOT);
        ItemStack batchMold = FoundryMoldItem.create(ModItems.MOLD.get(), FoundryMoldItem.Mold.INGOTS);
        check(helper, shallow.acceptsMold(ingotMold) && !shallow.acceptsMold(batchMold),
                "The shallow Foundry Mold must accept only source size-zero molds");

        BlockPos basinPos = helper.absolutePos(new BlockPos(5, 1, 2));
        helper.getLevel().setBlock(basinPos, ModBlocks.FOUNDRY_BASIN.get().defaultBlockState(), 3);
        FoundryMoldBlockEntity basin = (FoundryMoldBlockEntity) helper.getLevel().getBlockEntity(basinPos);
        check(helper, !basin.acceptsMold(ingotMold) && basin.acceptsMold(batchMold),
                "The Foundry Basin must accept only source size-one molds");
        basin.setItem(FoundryMoldBlockEntity.MOLD, batchMold);
        check(helper, basin.capacity() == FoundryMaterial.BLOCK
                        && basin.pour(FoundryMaterial.IRON, FoundryMaterial.BLOCK) == FoundryMaterial.BLOCK,
                "The nine-ingot basin mold must consume exactly 648 Iron quanta");
        for (int tick = 0; tick < 100; tick++) tick(helper, basin);
        check(helper, basin.getItem(FoundryMoldBlockEntity.OUTPUT).is(Items.IRON_INGOT)
                        && basin.getItem(FoundryMoldBlockEntity.OUTPUT).getCount() == 9,
                "The source batch mold must cool into exactly nine Iron Ingots");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void copperCastPlateAndSteelPipeUnlockExactDerrickParts(GameTestHelper helper) {
        BlockPos moldPos = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlock(moldPos, ModBlocks.FOUNDRY_MOLD.get().defaultBlockState(), 3);
        FoundryMoldBlockEntity mold = (FoundryMoldBlockEntity) helper.getLevel().getBlockEntity(moldPos);
        mold.setItem(FoundryMoldBlockEntity.MOLD, FoundryMoldItem.castPlate(ModItems.MOLD.get()));
        check(helper, mold.pour(FoundryMaterial.COPPER, FoundryMaterial.CAST_PLATE)
                        == FoundryMaterial.CAST_PLATE,
                "Cast Plate mold must accept exactly 216 Copper quanta");
        for (int i = 0; i < 100; i++) tick(helper, mold);
        ItemStack castCopper = mold.getItem(FoundryMoldBlockEntity.OUTPUT);
        check(helper, castCopper.is(ModItems.PLATE_CAST.get())
                        && CastPlateItem.material(castCopper) == CastPlateItem.CastPlateMaterial.COPPER,
                "A fresh full Copper mold must output one component-exact Cast Copper Plate");
        check(helper, CastPlateItem.CastPlateMaterial.COPPER.legacyMetadata() == 2900
                        && "item.hbm.plate_cast.copper".equals(castCopper.getDescriptionId()),
                "Cast Copper Plate must preserve source metadata 2900 and its exact material name");
        FoundryMaterial.MaterialAmount castCopperMaterial = FoundryMaterial.fromItem(castCopper);
        check(helper, castCopperMaterial != null && castCopperMaterial.material() == FoundryMaterial.COPPER
                        && castCopperMaterial.amount() == FoundryMaterial.CAST_PLATE,
                "Cast Copper Plate must remelt to exactly 216 Copper quanta");

        var steelPipe = AnvilRecipes.byId(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "anvil/pipe_steel"));
        ItemStack steelPipeOutput = steelPipe == null ? ItemStack.EMPTY : steelPipe.outputs().getFirst().stack().get();
        check(helper, steelPipe != null && steelPipe.tierLower() == 1 && steelPipe.inputs().size() == 1
                        && steelPipe.inputs().getFirst().count() == 3
                        && PipeItem.isSteel(steelPipeOutput),
                "Tier-1 Anvil must convert three Steel Plates into one exact Steel Pipe");
        var wrongPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        wrongPlayer.getInventory().add(new ItemStack(ModItems.get("plate_copper").get(), 3));
        check(helper, !AnvilRecipes.canCraft(wrongPlayer.getInventory(), steelPipe),
                "Steel Pipe construction must reject three Copper Plates");
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().add(new ItemStack(ModItems.get("plate_steel").get(), 3));
        check(helper, AnvilRecipes.canCraft(player.getInventory(), steelPipe)
                        && AnvilRecipes.craft(player, steelPipe, false),
                "A player carrying exactly three Steel Plates must be able to complete the Steel Pipe construction");
        int craftedSteelPipes = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.PIPE.get()) && PipeItem.isSteel(stack)) craftedSteelPipes += stack.getCount();
        }
        check(helper, craftedSteelPipes == 1
                        && player.getInventory().countItem(ModItems.get("plate_steel").get()) == 0,
                "Steel Pipe construction must consume all three Steel Plates and award one Steel Pipe");
        ItemStack copperPipe = PipeItem.copper(ModItems.PIPE.get(), 1);
        FoundryMaterial.MaterialAmount steelPipeMaterial = FoundryMaterial.fromItem(steelPipeOutput);
        check(helper, PipeItem.PipeMaterial.STEEL.legacyMetadata() == 30
                        && "item.hbm.pipe.steel".equals(steelPipeOutput.getDescriptionId())
                        && steelPipeMaterial != null && steelPipeMaterial.material() == FoundryMaterial.STEEL
                        && steelPipeMaterial.amount() == FoundryMaterial.CAST_PLATE,
                "Steel Pipe must preserve source metadata 30, name, and lossless 216-Steel-quanta remelting");
        FoundryMaterial.MaterialAmount copperPipeMaterial = FoundryMaterial.fromItem(copperPipe);
        check(helper, PipeItem.PipeMaterial.COPPER.legacyMetadata() == 2900
                        && "item.hbm.pipe.copper".equals(copperPipe.getDescriptionId())
                        && copperPipeMaterial != null && copperPipeMaterial.material() == FoundryMaterial.COPPER
                        && copperPipeMaterial.amount() == FoundryMaterial.CAST_PLATE,
                "Copper Pipe must preserve source metadata 2900, name, and lossless 216-Copper-quanta remelting");
        check(helper, !PipeItem.isSteel(copperPipe)
                        && !PipeItem.isCopper(steelPipeOutput),
                "Copper and Steel Pipe carrier subtypes must remain component-distinct");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void earlyOresHssAndTitaniumDrillCompleteDerrickPrerequisites(GameTestHelper helper) {
        check(helper, HbmConfig.TITANIUM_SPAWN_RATE.getDefault() == 8
                        && HbmConfig.TUNGSTEN_SPAWN_RATE.getDefault() == 10
                        && HbmConfig.COBALT_SPAWN_RATE.getDefault() == 2,
                "Early ore config must preserve source default vein counts 8/10/2");
        check(helper, new ConfigOreCountPlacement(ConfigOreCountPlacement.OreType.TITANIUM).configuredCount()
                        == HbmConfig.TITANIUM_SPAWN_RATE.get()
                        && new ConfigOreCountPlacement(ConfigOreCountPlacement.OreType.TUNGSTEN).configuredCount()
                        == HbmConfig.TUNGSTEN_SPAWN_RATE.get()
                        && new ConfigOreCountPlacement(ConfigOreCountPlacement.OreType.COBALT).configuredCount()
                        == HbmConfig.COBALT_SPAWN_RATE.get(),
                "Config-backed placement must use each live source ore rate");
        var placedFeatures = helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        for (String ore : new String[]{"ore_titanium", "ore_tungsten", "ore_cobalt"}) {
            check(helper, placedFeatures.containsKey(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, ore)),
                    "Placed feature hbm:" + ore + " must load");
            check(helper, helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(
                            HbmNtm.MOD_ID, "ingot_" + ore.substring(4) + "_from_ore")).isPresent(),
                    "Direct source smelting for " + ore + " must load");
        }

        FoundryMaterial.MaterialAmount fragment = FoundryMaterial.fromItem(
                new ItemStack(ModItems.get("fragment_cobalt").get()));
        FoundryMaterial.MaterialAmount tungsten = FoundryMaterial.fromItem(
                new ItemStack(ModItems.get("ingot_tungsten").get()));
        check(helper, fragment != null && fragment.material() == FoundryMaterial.COBALT
                        && fragment.amount() == FoundryMaterial.NUGGET,
                "One Cobalt Fragment must retain one-nugget/eight-quanta value");
        check(helper, tungsten != null && tungsten.material() == FoundryMaterial.TUNGSTEN
                        && tungsten.amount() == FoundryMaterial.INGOT,
                "One Tungsten Ingot must retain 72-quanta value");
        ItemStack shredded = ShredderRecipes.getResult(new ItemStack(ModItems.get("fragment_cobalt").get()));
        check(helper, shredded.is(ModItems.get("powder_cobalt_tiny").get()) && shredded.getCount() == 1,
                "The Shredder must turn one Cobalt Fragment into one Tiny Pile of Cobalt Powder");

        BlockPos cruciblePos = helper.absolutePos(new BlockPos(3, 1, 3));
        var crucibleState = ModBlocks.MACHINE_CRUCIBLE.get().defaultBlockState()
                .setValue(CrucibleBlock.FACING, Direction.NORTH);
        helper.getLevel().setBlock(cruciblePos, crucibleState, 3);
        CrucibleBlockEntity crucible = (CrucibleBlockEntity) helper.getLevel().getBlockEntity(cruciblePos);
        crucible.selectRecipe(CrucibleBlockEntity.RECIPE_DURA_STEEL);
        crucible.addMoltenForTest(true, FoundryMaterial.STEEL, FoundryMaterial.NUGGET * 5);
        crucible.addMoltenForTest(true, FoundryMaterial.TUNGSTEN, FoundryMaterial.NUGGET * 3);
        crucible.addMoltenForTest(true, FoundryMaterial.COBALT, FoundryMaterial.NUGGET);
        check(helper, !crucible.runSelectedRecipeForTest(8L) && crucible.runSelectedRecipeForTest(9L),
                "HSS alloying must wait for its exact nine-tick frequency boundary");
        check(helper, crucible.recipeAmount(FoundryMaterial.STEEL) == 0
                        && crucible.recipeAmount(FoundryMaterial.TUNGSTEN) == 0
                        && crucible.recipeAmount(FoundryMaterial.COBALT) == 0
                        && crucible.recipeAmount(FoundryMaterial.DURA_STEEL) == FoundryMaterial.INGOT,
                "HSS must consume 40 Steel + 24 Tungsten + 8 Cobalt and produce 72 Dura Steel quanta");

        BlockPos moldPos = helper.absolutePos(new BlockPos(6, 1, 3));
        helper.getLevel().setBlock(moldPos, ModBlocks.FOUNDRY_MOLD.get().defaultBlockState(), 3);
        FoundryMoldBlockEntity mold = (FoundryMoldBlockEntity) helper.getLevel().getBlockEntity(moldPos);
        mold.setItem(FoundryMoldBlockEntity.MOLD, FoundryMoldItem.castPlate(ModItems.MOLD.get()));
        check(helper, mold.pour(FoundryMaterial.DURA_STEEL, FoundryMaterial.CAST_PLATE)
                        == FoundryMaterial.CAST_PLATE,
                "Cast Plate mold must accept exactly 216 Dura Steel quanta");
        for (int i = 0; i < 100; i++) tick(helper, mold);
        ItemStack castDura = mold.getItem(FoundryMoldBlockEntity.OUTPUT);
        FoundryMaterial.MaterialAmount remelted = FoundryMaterial.fromItem(castDura);
        check(helper, castDura.is(ModItems.PLATE_CAST.get())
                        && CastPlateItem.material(castDura) == CastPlateItem.CastPlateMaterial.DURA_STEEL
                        && CastPlateItem.CastPlateMaterial.DURA_STEEL.legacyMetadata() == 33
                        && "item.hbm.plate_cast.dura_steel".equals(castDura.getDescriptionId())
                        && remelted != null && remelted.material() == FoundryMaterial.DURA_STEEL
                        && remelted.amount() == FoundryMaterial.CAST_PLATE,
                "Cast HSS Plate must preserve source metadata 33, name and lossless remelting");

        var drill = AssemblyRecipes.byName("ass.titaniumdrill");
        check(helper, drill != null && drill.duration() == 100 && drill.power() == 100L
                        && drill.output().is(ModItems.DRILL_TITANIUM.get()) && drill.inputs().size() == 2
                        && drill.inputs().get(0).matches(castDura.copy())
                        && !drill.inputs().get(0).matches(CastPlateItem.create(ModItems.PLATE_CAST.get(),
                                CastPlateItem.CastPlateMaterial.STEEL, 1))
                        && drill.inputs().get(1).matches(new ItemStack(ModItems.get("plate_titanium").get(), 8)),
                "Titanium Drill Assembly must require one exact Cast HSS Plate and eight Titanium Plates at 100x100");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void remainingSourceAlloysKeepRatiosFrequenciesAndSlag(GameTestHelper helper) {
        BlockPos cruciblePos = helper.absolutePos(new BlockPos(3, 1, 3));
        var crucibleState = ModBlocks.MACHINE_CRUCIBLE.get().defaultBlockState()
                .setValue(CrucibleBlock.FACING, Direction.NORTH);
        helper.getLevel().setBlock(cruciblePos, crucibleState, 3);
        CrucibleBlockEntity crucible = (CrucibleBlockEntity) helper.getLevel().getBlockEntity(cruciblePos);

        assertAlloy(helper, crucible, CrucibleBlockEntity.RECIPE_FERROURANIUM, 3,
                amounts(FoundryMaterial.STEEL, 16, FoundryMaterial.URANIUM_238, 8),
                amounts(FoundryMaterial.FERROURANIUM, 24));
        assertAlloy(helper, crucible, CrucibleBlockEntity.RECIPE_TECHNETIUM_STEEL, 9,
                amounts(FoundryMaterial.STEEL, 64, FoundryMaterial.TECHNETIUM, 8),
                amounts(FoundryMaterial.TECHNETIUM_STEEL, 72));
        assertAlloy(helper, crucible, CrucibleBlockEntity.RECIPE_CADMIUM_STEEL, 9,
                amounts(FoundryMaterial.STEEL, 64, FoundryMaterial.CADMIUM, 8),
                amounts(FoundryMaterial.CADMIUM_STEEL, 72));
        assertAlloy(helper, crucible, CrucibleBlockEntity.RECIPE_BISMUTH_BRONZE, 9,
                amounts(FoundryMaterial.COPPER, 64, FoundryMaterial.BISMUTH, 8, FoundryMaterial.FLUX, 24),
                amounts(FoundryMaterial.BISMUTH_BRONZE, 72, FoundryMaterial.SLAG, 24));
        assertAlloy(helper, crucible, CrucibleBlockEntity.RECIPE_ARSENIC_BRONZE, 9,
                amounts(FoundryMaterial.COPPER, 64, FoundryMaterial.ARSENIC, 8, FoundryMaterial.FLUX, 24),
                amounts(FoundryMaterial.ARSENIC_BRONZE, 72, FoundryMaterial.SLAG, 24));
        assertAlloy(helper, crucible, CrucibleBlockEntity.RECIPE_BSCCO, 3,
                amounts(FoundryMaterial.BISMUTH, 16, FoundryMaterial.STRONTIUM, 16,
                        FoundryMaterial.CALCIUM, 16, FoundryMaterial.COPPER, 24),
                amounts(FoundryMaterial.BSCCO, 72));

        check(helper, material(new ItemStack(ModItems.get("nugget_technetium").get()))
                        == FoundryMaterial.TECHNETIUM
                        && amount(new ItemStack(ModItems.get("nugget_technetium").get())) == 8
                        && material(new ItemStack(ModItems.get("powder_strontium").get()))
                        == FoundryMaterial.STRONTIUM
                        && amount(new ItemStack(ModItems.get("powder_strontium").get())) == 72,
                "Technetium nuggets and Strontium powder must preserve their source foundry quanta");

        ItemStack slag = FoundryMaterial.SLAG.ingot();
        check(helper, slag.is(ModItems.INGOT_RAW.get())
                        && FoundryIngotItem.material(slag) == FoundryMaterial.SLAG
                        && material(slag) == FoundryMaterial.SLAG && amount(slag) == 72,
                "Bronze byproduct must cast as the source component-backed Slag raw ingot");

        BlockPos moldPos = helper.absolutePos(new BlockPos(5, 1, 5));
        helper.getLevel().setBlock(moldPos, ModBlocks.FOUNDRY_MOLD.get().defaultBlockState(), 3);
        FoundryMoldBlockEntity mold = (FoundryMoldBlockEntity) helper.getLevel().getBlockEntity(moldPos);
        mold.setItem(FoundryMoldBlockEntity.MOLD, FoundryMoldItem.ingot(ModItems.MOLD.get()));
        check(helper, mold.pour(FoundryMaterial.SLAG, FoundryMaterial.INGOT) == FoundryMaterial.INGOT,
                "The Ingot Mold must accept one full Slag ingot charge");
        for (int tick = 0; tick < 100; tick++) tick(helper, mold);
        check(helper, FoundryIngotItem.material(mold.getItem(FoundryMoldBlockEntity.OUTPUT))
                        == FoundryMaterial.SLAG,
                "Cooling a full Slag charge must output the component-exact raw Slag Ingot");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void finalSourceCrucibleRecipesKeepExactInputsIconsAndOutputs(GameTestHelper helper) {
        BlockPos cruciblePos = helper.absolutePos(new BlockPos(3, 1, 3));
        var crucibleState = ModBlocks.MACHINE_CRUCIBLE.get().defaultBlockState()
                .setValue(CrucibleBlock.FACING, Direction.NORTH);
        helper.getLevel().setBlock(cruciblePos, crucibleState, 3);
        CrucibleBlockEntity crucible = (CrucibleBlockEntity) helper.getLevel().getBlockEntity(cruciblePos);

        assertUnderfilled(helper, crucible, CrucibleBlockEntity.RECIPE_HEMATITE, 6,
                amounts(FoundryMaterial.HEMATITE, 144, FoundryMaterial.FLUX, 16));
        assertAlloy(helper, crucible, CrucibleBlockEntity.RECIPE_HEMATITE, 6,
                amounts(FoundryMaterial.HEMATITE, 144, FoundryMaterial.FLUX, 16),
                amounts(FoundryMaterial.IRON, 72, FoundryMaterial.SLAG, 24));
        assertUnderfilled(helper, crucible, CrucibleBlockEntity.RECIPE_MALACHITE, 6,
                amounts(FoundryMaterial.MALACHITE, 144, FoundryMaterial.FLUX, 16));
        assertAlloy(helper, crucible, CrucibleBlockEntity.RECIPE_MALACHITE, 6,
                amounts(FoundryMaterial.MALACHITE, 144, FoundryMaterial.FLUX, 16),
                amounts(FoundryMaterial.COPPER, 72, FoundryMaterial.SLAG, 24));
        assertUnderfilled(helper, crucible, CrucibleBlockEntity.RECIPE_MAGNETIZED_TUNGSTEN, 3,
                amounts(FoundryMaterial.TUNGSTEN, 72, FoundryMaterial.SCHRABIDIUM, 8));
        assertAlloy(helper, crucible, CrucibleBlockEntity.RECIPE_MAGNETIZED_TUNGSTEN, 3,
                amounts(FoundryMaterial.TUNGSTEN, 72, FoundryMaterial.SCHRABIDIUM, 8),
                amounts(FoundryMaterial.MAGNETIZED_TUNGSTEN, 72));
        assertUnderfilled(helper, crucible, CrucibleBlockEntity.RECIPE_COMBINE_STEEL, 3,
                amounts(FoundryMaterial.MAGNETIZED_TUNGSTEN, 48, FoundryMaterial.MUD, 24));
        assertAlloy(helper, crucible, CrucibleBlockEntity.RECIPE_COMBINE_STEEL, 3,
                amounts(FoundryMaterial.MAGNETIZED_TUNGSTEN, 48, FoundryMaterial.MUD, 24),
                amounts(FoundryMaterial.COMBINE_STEEL, 72));

        ItemStack hematite = StoneResourceBlockItem.create(ModItems.STONE_RESOURCE_ITEM.get(),
                StoneResourceBlock.Type.HEMATITE, 1);
        ItemStack malachite = StoneResourceBlockItem.create(ModItems.STONE_RESOURCE_ITEM.get(),
                StoneResourceBlock.Type.MALACHITE, 1);
        ItemStack malachiteChunk = OreChunkItem.create(ModItems.CHUNK_ORE.get(),
                OreChunkItem.ChunkType.MALACHITE, 1);
        check(helper, material(hematite) == FoundryMaterial.HEMATITE && amount(hematite) == 72
                        && material(malachite) == FoundryMaterial.MALACHITE && amount(malachite) == 432
                        && material(malachiteChunk) == FoundryMaterial.MALACHITE && amount(malachiteChunk) == 72,
                "Hematite ore, Malachite ore, and Malachite chunks must retain 72/432/72 source quanta");
        check(helper, FoundryMaterial.HEMATITE.additive() && FoundryMaterial.MALACHITE.additive()
                        && FoundryMaterial.HEMATITE.ingot().isEmpty() && FoundryMaterial.MALACHITE.ingot().isEmpty(),
                "Hematite and Malachite must remain non-castable foundry additives");

        check(helper, amount(new ItemStack(ModItems.get("ingot_schrabidium").get())) == 72
                        && amount(new ItemStack(ModItems.get("powder_schrabidium").get())) == 72
                        && amount(new ItemStack(ModItems.get("billet_schrabidium").get())) == 48
                        && amount(new ItemStack(ModItems.get("nugget_schrabidium").get())) == 8
                        && amount(new ItemStack(ModItems.get("ingot_mud").get())) == 72,
                "Schrabidium forms and the Solid Mud Brick must preserve their source quanta");
        for (String id : new String[]{
                "ingot_schrabidium_from_nugget_schrabidium",
                "nugget_schrabidium_from_ingot_schrabidium",
                "billet_schrabidium_from_nugget_schrabidium",
                "nugget_schrabidium_from_billet_schrabidium",
                "ingot_schrabidium_from_billet_schrabidium",
                "billet_schrabidium_from_ingot_schrabidium",
                "ingot_schrabidium_from_powder",
                "ingot_technetium_from_nugget_technetium",
                "nugget_technetium_from_ingot_technetium",
                "billet_technetium_from_nugget_technetium",
                "nugget_technetium_from_billet_technetium",
                "ingot_technetium_from_billet_technetium",
                "billet_technetium_from_ingot_technetium",
                "ingot_aluminium_from_powder",
                "ingot_beryllium_from_powder",
                "ingot_copper_from_powder",
                "gold_ingot_from_powder",
                "iron_ingot_from_powder",
                "ingot_uranium_from_powder",
                "ingot_red_copper_from_powder",
                "ingot_steel_from_powder",
                "ingot_asbestos_from_powder",
                "ingot_bismuth_from_powder",
                "ingot_calcium_from_powder",
                "ingot_cadmium_from_powder",
                "ingot_tcalloy_from_powder",
                "ingot_magnetized_tungsten_from_powder",
                "ingot_combine_steel_from_powder",
                "magnetized_tungsten_block",
                "ingot_magnetized_tungsten_from_block_magnetized_tungsten",
                "combine_steel_block",
                "ingot_combine_steel_from_block_combine_steel",
                "scraps_red_copper",
                "scraps_steel",
                "scraps_steel_bulk",
                "scraps_magnetized_tungsten",
                "scraps_technetium_steel",
                "powder_flux_from_coal_powder",
                "powder_flux_from_calcium_powder",
                "powder_flux_from_fluorite",
                "gunpowder_from_coal",
                "gunpowder_from_charcoal",
                "clay_ball_from_clay",
                "cobblestone_from_gravel",
                "obsidian_from_gravel_obsidian",
                "diamond_from_gravel_diamond"}) {
            check(helper, helper.getLevel().getRecipeManager().byKey(
                            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id)).isPresent(),
                    "Source material recipe hbm:" + id + " must load");
        }
        ItemStack schrabIngot = new ItemStack(ModItems.get("ingot_schrabidium").get());
        ItemStack schrabPowder = new ItemStack(ModItems.get("powder_schrabidium").get());
        ItemStack mudIngot = new ItemStack(ModItems.get("ingot_mud").get());
        check(helper, schrabIngot.getRarity() == Rarity.RARE && schrabPowder.getRarity() == Rarity.RARE
                        && schrabIngot.getItem() instanceof HazardCarrier ingotHazard
                        && ingotHazard.hbm$getHazards(schrabIngot).radiation() == 15.0F
                        && ingotHazard.hbm$getHazards(schrabIngot).blinding() == 50.0F
                        && schrabPowder.getItem() instanceof HazardCarrier powderHazard
                        && powderHazard.hbm$getHazards(schrabPowder).radiation() == 45.0F
                        && powderHazard.hbm$getHazards(schrabPowder).blinding() == 150.0F
                        && mudIngot.getItem() instanceof HazardCarrier mudHazard
                        && mudHazard.hbm$getHazards(mudIngot).radiation() == 1.0F,
                "Schrabidium forms and Solid Mud must retain their exact source hazards and rarity");
        ItemStack magTungScraps = helper.getLevel().getRecipeManager().byKey(
                        ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "scraps_magnetized_tungsten"))
                .orElseThrow().value().getResultItem(helper.getLevel().registryAccess());
        ItemStack tcSteelScraps = helper.getLevel().getRecipeManager().byKey(
                        ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "scraps_technetium_steel"))
                .orElseThrow().value().getResultItem(helper.getLevel().registryAccess());
        check(helper, FoundryScrapsItem.contents(magTungScraps)
                        .equals(new FoundryMaterial.MaterialAmount(FoundryMaterial.MAGNETIZED_TUNGSTEN, 72))
                        && FoundryScrapsItem.contents(tcSteelScraps)
                        .equals(new FoundryMaterial.MaterialAmount(FoundryMaterial.TECHNETIUM_STEEL, 72)),
                "Powder-alloy recipes must return one ingot quantum in their exact scraps carriers");
        ItemStack redCopperScraps = helper.getLevel().getRecipeManager().byKey(
                        ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "scraps_red_copper"))
                .orElseThrow().value().getResultItem(helper.getLevel().registryAccess());
        ItemStack steelScraps = helper.getLevel().getRecipeManager().byKey(
                        ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "scraps_steel"))
                .orElseThrow().value().getResultItem(helper.getLevel().registryAccess());
        ItemStack bulkSteelScraps = helper.getLevel().getRecipeManager().byKey(
                        ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "scraps_steel_bulk"))
                .orElseThrow().value().getResultItem(helper.getLevel().registryAccess());
        ItemStack coalFlux = helper.getLevel().getRecipeManager().byKey(
                        ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "powder_flux_from_coal_powder"))
                .orElseThrow().value().getResultItem(helper.getLevel().registryAccess());
        ItemStack calciumFlux = helper.getLevel().getRecipeManager().byKey(
                        ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "powder_flux_from_calcium_powder"))
                .orElseThrow().value().getResultItem(helper.getLevel().registryAccess());
        ItemStack fluoriteFlux = helper.getLevel().getRecipeManager().byKey(
                        ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "powder_flux_from_fluorite"))
                .orElseThrow().value().getResultItem(helper.getLevel().registryAccess());
        ItemStack charcoalGunpowder = helper.getLevel().getRecipeManager().byKey(
                        ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "gunpowder_from_charcoal"))
                .orElseThrow().value().getResultItem(helper.getLevel().registryAccess());
        ItemStack clayBalls = helper.getLevel().getRecipeManager().byKey(
                        ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "clay_ball_from_clay"))
                .orElseThrow().value().getResultItem(helper.getLevel().registryAccess());
        check(helper, FoundryScrapsItem.contents(redCopperScraps)
                        .equals(new FoundryMaterial.MaterialAmount(FoundryMaterial.RED_COPPER, 144))
                        && FoundryScrapsItem.contents(steelScraps)
                        .equals(new FoundryMaterial.MaterialAmount(FoundryMaterial.STEEL, 72))
                        && FoundryScrapsItem.contents(bulkSteelScraps)
                        .equals(new FoundryMaterial.MaterialAmount(FoundryMaterial.STEEL, 288))
                        && coalFlux.is(ModItems.POWDER_FLUX.get()) && coalFlux.getCount() == 2
                        && calciumFlux.is(ModItems.POWDER_FLUX.get()) && calciumFlux.getCount() == 12
                        && fluoriteFlux.is(ModItems.POWDER_FLUX.get()) && fluoriteFlux.getCount() == 4
                        && charcoalGunpowder.is(Items.GUNPOWDER) && charcoalGunpowder.getCount() == 3
                        && clayBalls.is(Items.CLAY_BALL) && clayBalls.getCount() == 4,
                "The remaining source powder, Flux, gunpowder, and clay recipes must preserve exact yields");
        check(helper, material(new ItemStack(ModItems.get("ingot_magnetized_tungsten").get()))
                        == FoundryMaterial.MAGNETIZED_TUNGSTEN
                        && material(new ItemStack(ModItems.get("powder_magnetized_tungsten").get()))
                        == FoundryMaterial.MAGNETIZED_TUNGSTEN
                        && amount(new ItemStack(ModItems.getBlockItem("block_magnetized_tungsten").get())) == 648
                        && material(new ItemStack(ModItems.get("ingot_combine_steel").get()))
                        == FoundryMaterial.COMBINE_STEEL
                        && material(new ItemStack(ModItems.get("powder_combine_steel").get()))
                        == FoundryMaterial.COMBINE_STEEL
                        && amount(new ItemStack(ModItems.getBlockItem("block_combine_steel").get())) == 648,
                "Magnetized Tungsten and CMB forms must remelt to their exact materials");

        ItemStack hematiteIcon = CrucibleRecipes.byId(CrucibleRecipes.HEMATITE).icon();
        ItemStack malachiteIcon = CrucibleRecipes.byId(CrucibleRecipes.MALACHITE).icon();
        check(helper, CrucibleRecipes.lastId() == CrucibleRecipes.COMBINE_STEEL
                        && hematiteIcon.is(ModItems.STONE_RESOURCE_ITEM.get())
                        && malachiteIcon.is(ModItems.STONE_RESOURCE_ITEM.get())
                        && StoneResourceBlockItem.type(hematiteIcon) == StoneResourceBlock.Type.HEMATITE
                        && StoneResourceBlockItem.type(malachiteIcon) == StoneResourceBlock.Type.MALACHITE,
                "The appended recipe ids must stay stable and ore recipes must use their source selector icons");

        int[] sourceOrder = {CrucibleRecipes.NONE, CrucibleRecipes.STEEL, CrucibleRecipes.HEMATITE,
                CrucibleRecipes.MALACHITE, CrucibleRecipes.RED_COPPER, CrucibleRecipes.DURA_STEEL,
                CrucibleRecipes.FERROURANIUM, CrucibleRecipes.TECHNETIUM_STEEL,
                CrucibleRecipes.CADMIUM_STEEL, CrucibleRecipes.BISMUTH_BRONZE,
                CrucibleRecipes.ARSENIC_BRONZE, CrucibleRecipes.COMBINE_STEEL,
                CrucibleRecipes.MAGNETIZED_TUNGSTEN, CrucibleRecipes.BSCCO, CrucibleRecipes.NONE};
        for (int index = 0; index < sourceOrder.length - 1; index++) {
            check(helper, CrucibleRecipes.nextId(sourceOrder[index]) == sourceOrder[index + 1],
                    "Crucible selector must retain the source cycle order at index " + index);
        }

        BlockPos stoneRelative = new BlockPos(1, 1, 1);
        helper.setBlock(stoneRelative, ModBlocks.STONE_RESOURCE.get().defaultBlockState()
                .setValue(StoneResourceBlock.TYPE, StoneResourceBlock.Type.MALACHITE));
        BlockPos stoneAbsolute = helper.absolutePos(stoneRelative);
        var stoneState = helper.getLevel().getBlockState(stoneAbsolute);
        var normalDrops = net.minecraft.world.level.block.Block.getDrops(
                stoneState, helper.getLevel(), stoneAbsolute, null);
        check(helper, normalDrops.size() == 1 && normalDrops.getFirst().is(ModItems.CHUNK_ORE.get())
                        && OreChunkItem.type(normalDrops.getFirst()) == OreChunkItem.ChunkType.MALACHITE
                        && normalDrops.getFirst().getCount() >= 3 && normalDrops.getFirst().getCount() <= 4,
                "Normally mined Malachite must yield the source three-to-four chunks");
        ItemStack silkPick = new ItemStack(Items.DIAMOND_PICKAXE);
        silkPick.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH), 1);
        var silkDrops = net.minecraft.world.level.block.Block.getDrops(
                stoneState, helper.getLevel(), stoneAbsolute, null, null, silkPick);
        check(helper, silkDrops.size() == 1 && silkDrops.getFirst().is(ModItems.STONE_RESOURCE_ITEM.get())
                        && StoneResourceBlockItem.type(silkDrops.getFirst()) == StoneResourceBlock.Type.MALACHITE,
                "Silk Touch must preserve the component-backed Malachite block");

        BlockPos moldPos = helper.absolutePos(new BlockPos(6, 1, 3));
        helper.getLevel().setBlock(moldPos, ModBlocks.FOUNDRY_MOLD.get().defaultBlockState(), 3);
        FoundryMoldBlockEntity mold = (FoundryMoldBlockEntity) helper.getLevel().getBlockEntity(moldPos);
        mold.setItem(FoundryMoldBlockEntity.MOLD, FoundryMoldItem.ingot(ModItems.MOLD.get()));
        check(helper, mold.pour(FoundryMaterial.MAGNETIZED_TUNGSTEN, FoundryMaterial.INGOT)
                        == FoundryMaterial.INGOT,
                "The source Ingot Mold must accept one Magnetized Tungsten ingot charge");
        for (int tick = 0; tick < 100; tick++) tick(helper, mold);
        check(helper, mold.getItem(FoundryMoldBlockEntity.OUTPUT).is(
                        ModItems.get("ingot_magnetized_tungsten").get()),
                "Cooling Magnetized Tungsten must produce its dedicated source ingot");
        mold.removeItemNoUpdate(FoundryMoldBlockEntity.OUTPUT);
        check(helper, mold.pour(FoundryMaterial.COMBINE_STEEL, FoundryMaterial.INGOT)
                        == FoundryMaterial.INGOT,
                "The source Ingot Mold must accept one CMB Steel ingot charge");
        for (int tick = 0; tick < 200; tick++) tick(helper, mold);
        check(helper, mold.getItem(FoundryMoldBlockEntity.OUTPUT).is(ModItems.get("ingot_combine_steel").get()),
                "Cooling CMB Steel must produce its dedicated source ingot");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exactAnvilBootstrapPreservesDemonstrationPlate(GameTestHelper helper) {
        ItemStack ironPlate = CastPlateItem.create(ModItems.PLATE_CAST.get(), CastPlateItem.CastPlateMaterial.IRON, 1);
        AnvilRecipes.Smithing mold = AnvilRecipes.findSmithing(ironPlate, new ItemStack(ModItems.MOLD_BASE.get()), 1);
        check(helper, mold != null && mold.leftConsumed() == 0 && mold.rightConsumed() == 1
                        && FoundryMoldItem.isCastPlate(mold.output().get()),
                "Tier-1 mold smithing must preserve the Cast Iron demonstration plate and consume one Blank Mold");
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        AnvilMenu menu = new AnvilMenu(0, player.getInventory(), 1);
        menu.getSlot(0).set(ironPlate.copy());
        menu.getSlot(1).set(new ItemStack(ModItems.MOLD_BASE.get()));
        ItemStack taken = menu.getSlot(2).remove(1);
        menu.getSlot(2).onTake(player, taken);
        check(helper, CastPlateItem.material(menu.input(0)) == CastPlateItem.CastPlateMaterial.IRON
                        && menu.input(0).getCount() == 1 && menu.input(1).isEmpty(),
                "Taking the mold result must leave the demonstration plate and consume the Blank Mold");

        var crucible = AnvilRecipes.byId(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "anvil/machine_crucible"));
        var soldering = AnvilRecipes.byId(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "anvil/machine_soldering_station"));
        check(helper, crucible != null && crucible.tierLower() == 2 && crucible.inputs().size() == 3,
                "Crucible must be a Tier-2 construction with Firebrick, Copper and Steel Plate inputs");
        check(helper, soldering != null && soldering.tierLower() == 2 && soldering.inputs().size() == 4,
                "Soldering Station must activate only with its four exact source inputs");
        check(helper, soldering.inputs().get(0).matches(CastPlateItem.create(ModItems.PLATE_CAST.get(),
                        CastPlateItem.CastPlateMaterial.STEEL, 1))
                        && soldering.inputs().get(2).matches(BoltItem.create(ModItems.BOLT.get(), BoltItem.BoltMaterial.TUNGSTEN, 1))
                        && soldering.inputs().get(3).matches(CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.VACUUM_TUBE, 1)),
                "Soldering construction must require Cast Steel Plates, Tungsten Bolts and Vacuum Tubes by subtype");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void foundryTransportSetPreservesSourceCapacitiesAndPriorities(GameTestHelper helper) {
        BlockPos channelPos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos moldPos = channelPos.east();
        helper.getLevel().setBlock(channelPos, ModBlocks.FOUNDRY_CHANNEL.get().defaultBlockState(), 3);
        helper.getLevel().setBlock(moldPos, ModBlocks.FOUNDRY_MOLD.get().defaultBlockState(), 3);
        FoundryChannelBlockEntity channel = (FoundryChannelBlockEntity) helper.getLevel().getBlockEntity(channelPos);
        FoundryMoldBlockEntity mold = (FoundryMoldBlockEntity) helper.getLevel().getBlockEntity(moldPos);
        mold.setItem(FoundryMoldBlockEntity.MOLD, FoundryMoldItem.ingot(ModItems.MOLD.get()));
        channel.setMoltenForTest(FoundryMaterial.IRON, FoundryChannelBlockEntity.CAPACITY);
        for (int tick = 0; tick < 5; tick++) {
            FoundryChannelBlockEntity.tick(helper.getLevel(), channelPos, channel.getBlockState(), channel);
        }
        check(helper, channel.amount() == FoundryMaterial.INGOT
                        && mold.amount() == FoundryMaterial.INGOT,
                "A two-ingot channel must prioritize and fill an adjacent one-ingot mold");

        BlockPos tankPos = helper.absolutePos(new BlockPos(5, 2, 2));
        helper.getLevel().setBlock(tankPos, ModBlocks.FOUNDRY_TANK.get().defaultBlockState(), 3);
        FoundryTankBlockEntity tank = (FoundryTankBlockEntity) helper.getLevel().getBlockEntity(tankPos);
        check(helper, tank.capacity() == FoundryMaterial.BLOCK * 4,
                "Foundry Storage Basin must retain the source four-block capacity");

        BlockPos outletPos = helper.absolutePos(new BlockPos(8, 3, 2));
        BlockPos outletMoldPos = outletPos.below();
        helper.getLevel().setBlock(outletPos, ModBlocks.FOUNDRY_OUTLET.get().defaultBlockState(), 3);
        helper.getLevel().setBlock(outletMoldPos, ModBlocks.FOUNDRY_MOLD.get().defaultBlockState(), 3);
        FoundryOutletBlockEntity outlet = (FoundryOutletBlockEntity) helper.getLevel().getBlockEntity(outletPos);
        FoundryMoldBlockEntity outletMold = (FoundryMoldBlockEntity) helper.getLevel().getBlockEntity(outletMoldPos);
        outletMold.setItem(FoundryMoldBlockEntity.MOLD, FoundryMoldItem.ingot(ModItems.MOLD.get()));
        int accepted = outlet.acceptFlow(FoundryMaterial.COPPER, FoundryMaterial.INGOT, Direction.SOUTH);
        check(helper, accepted == FoundryMaterial.INGOT && outletMold.amount() == FoundryMaterial.INGOT,
                "Foundry Outlet must turn horizontal flow into a vertical pour");

        BlockPos tapPos = helper.absolutePos(new BlockPos(10, 4, 2));
        BlockPos floorPos = tapPos.below(3);
        helper.getLevel().setBlock(floorPos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(tapPos, ModBlocks.FOUNDRY_SLAGTAP.get().defaultBlockState(), 3);
        FoundryOutletBlockEntity tap = (FoundryOutletBlockEntity) helper.getLevel().getBlockEntity(tapPos);
        check(helper, tap.acceptFlow(FoundryMaterial.SLAG, FoundryMaterial.INGOT, Direction.SOUTH)
                        == FoundryMaterial.INGOT
                        && helper.getLevel().getBlockEntity(floorPos.above()) instanceof DynamicSlagBlockEntity,
                "Foundry Spill Outlet must create a material-bearing dynamic slag volume");
        helper.succeed();
    }

    private static int amount(ItemStack stack) {
        FoundryMaterial.MaterialAmount amount = FoundryMaterial.fromItem(stack);
        return amount == null ? -1 : amount.amount();
    }
    private static FoundryMaterial material(ItemStack stack) {
        FoundryMaterial.MaterialAmount amount = FoundryMaterial.fromItem(stack);
        return amount == null ? null : amount.material();
    }
    private static FoundryMaterial.MaterialAmount[] amounts(Object... values) {
        FoundryMaterial.MaterialAmount[] amounts = new FoundryMaterial.MaterialAmount[values.length / 2];
        for (int index = 0; index < values.length; index += 2) {
            amounts[index / 2] = new FoundryMaterial.MaterialAmount(
                    (FoundryMaterial) values[index], (Integer) values[index + 1]);
        }
        return amounts;
    }
    private static void assertAlloy(GameTestHelper helper, CrucibleBlockEntity crucible, int recipe, int frequency,
                                    FoundryMaterial.MaterialAmount[] inputs,
                                    FoundryMaterial.MaterialAmount[] outputs) {
        crucible.clearMolten();
        crucible.selectRecipe(recipe);
        for (FoundryMaterial.MaterialAmount input : inputs) {
            crucible.addMoltenForTest(true, input.material(), input.amount());
        }
        check(helper, !crucible.runSelectedRecipeForTest(frequency - 1L)
                        && crucible.runSelectedRecipeForTest(frequency),
                "Crucible recipe " + recipe + " must run only on its source frequency " + frequency);
        for (FoundryMaterial.MaterialAmount input : inputs) {
            check(helper, crucible.recipeAmount(input.material()) == 0,
                    "Crucible recipe " + recipe + " must consume its exact " + input.material().id() + " input");
        }
        for (FoundryMaterial.MaterialAmount output : outputs) {
            check(helper, crucible.recipeAmount(output.material()) == output.amount(),
                    "Crucible recipe " + recipe + " must produce exactly " + output.amount()
                            + " quanta of " + output.material().id());
        }
    }
    private static void assertUnderfilled(GameTestHelper helper, CrucibleBlockEntity crucible, int recipe, int frequency,
                                          FoundryMaterial.MaterialAmount[] inputs) {
        crucible.clearMolten();
        crucible.selectRecipe(recipe);
        for (int index = 0; index < inputs.length; index++) {
            FoundryMaterial.MaterialAmount input = inputs[index];
            crucible.addMoltenForTest(true, input.material(), input.amount() - (index == 0 ? 1 : 0));
        }
        check(helper, !crucible.runSelectedRecipeForTest(frequency),
                "Crucible recipe " + recipe + " must reject a charge one quantum below its source ratio");
    }
    private static void tick(GameTestHelper helper, CrucibleBlockEntity crucible) {
        CrucibleBlockEntity.tick(helper.getLevel(), crucible.getBlockPos(), crucible.getBlockState(), crucible);
    }
    private static void tick(GameTestHelper helper, FoundryMoldBlockEntity mold) {
        FoundryMoldBlockEntity.tick(helper.getLevel(), mold.getBlockPos(), mold.getBlockState(), mold);
    }
    private static void check(GameTestHelper helper, boolean condition, String message) { if (!condition) helper.fail(message); }
}
