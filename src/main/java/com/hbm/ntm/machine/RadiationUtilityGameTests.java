package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.RadGenBlock;
import com.hbm.ntm.blockentity.MachineShredderBlockEntity;
import com.hbm.ntm.blockentity.RadGenBlockEntity;
import com.hbm.ntm.blockentity.WasteDrumBlockEntity;
import com.hbm.ntm.hazard.HazardCarrier;
import com.hbm.ntm.item.NuclearWasteItem;
import com.hbm.ntm.recipe.RadGenFuelRecipes;
import com.hbm.ntm.recipe.WasteDrumRecipes;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RadiationUtilityGameTests {
    private RadiationUtilityGameTests() { }

    @GameTest(template = "empty")
    public static void radGenRetainsSourceDummyTopologyAndRearPowerPort(GameTestHelper helper) {
        RadGenBlock block = ModBlocks.MACHINE_RADGEN.get();
        BlockPos core = new BlockPos(20, 5, 20);

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            List<BlockPos> parts = RadGenBlock.partPositions(core, facing);
            check(helper, parts.size() == 54 && new HashSet<>(parts).size() == 54,
                    "RadGen must occupy the source 3x3x6 volume for " + facing);

            int proxies = 0;
            int powerPorts = 0;
            for (BlockPos part : parts) {
                var state = block.stateForPart(part, core, facing);
                check(helper, RadGenBlock.corePosition(part, state).equals(core),
                        "Every RadGen dummy part must resolve back to its core");
                if (RadGenBlock.isProxy(state)) proxies++;
                if (RadGenBlock.isPowerPort(state)) powerPorts++;
            }
            check(helper, proxies == 3, "RadGen must expose exactly three source proxy cells");
            check(helper, powerPorts == 1,
                    "RadGen must expose exactly one HE port at longitudinal -3");
            check(helper, RadGenBlock.powerTarget(core, facing)
                            .equals(core.relative(facing.getOpposite(), 4)),
                    "RadGen HE output target must remain one block behind the rear proxy");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void radGenFuelTableKeepsExactSourceRatesAndDurations(GameTestHelper helper) {
        assertFuel(helper, RadGenFuelRecipes.SHORT, 1_500, 36_000,
                "nuclear_waste_short_depleted", true);
        assertFuel(helper, RadGenFuelRecipes.SHORT_TINY, 150, 3_600,
                "nuclear_waste_short_depleted_tiny", true);
        assertFuel(helper, RadGenFuelRecipes.LONG, 500, 144_000,
                "nuclear_waste_long_depleted", true);
        assertFuel(helper, RadGenFuelRecipes.LONG_TINY, 50, 14_400,
                "nuclear_waste_long_depleted_tiny", true);
        assertFuel(helper, RadGenFuelRecipes.NUCLEAR_SCRAP, 50, 6_000, null, false);
        assertFuel(helper, RadGenFuelRecipes.RADIATION_GEM, 25_000, 36_000,
                "diamond", false);
        check(helper, RadGenFuelRecipes.find(new ItemStack(Items.DIAMOND)) == null,
                "An unrelated registered item must never substitute for a missing source fuel");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void radGenFuelItemsRetainSourceVariantsHazardsAndDroppedWasteRules(GameTestHelper helper) {
        NuclearWasteItem shortWaste = ModItems.NUCLEAR_WASTE_SHORT.get();
        NuclearWasteItem shortTiny = ModItems.NUCLEAR_WASTE_SHORT_TINY.get();
        NuclearWasteItem longWaste = ModItems.NUCLEAR_WASTE_LONG.get();
        NuclearWasteItem longDepletedTiny = ModItems.NUCLEAR_WASTE_LONG_DEPLETED_TINY.get();

        ItemStack shortSchrabidium = NuclearWasteItem.stack(shortWaste, 7, 1);
        ItemStack longThorium = NuclearWasteItem.stack(longWaste, 3, 1);
        check(helper, shortWaste.variantCount() == 8 && longWaste.variantCount() == 5
                        && NuclearWasteItem.variant(shortSchrabidium) == 7
                        && shortWaste.className(shortSchrabidium).equals("Schrabidium-326")
                        && longWaste.className(longThorium).equals("Thorium-232"),
                "Waste families must retain all source metadata classes and their display names");

        var shortHazards = ((HazardCarrier) shortWaste).hbm$getHazards(shortSchrabidium);
        var shortTinyHazards = ((HazardCarrier) shortTiny).hbm$getHazards(
                NuclearWasteItem.stack(shortTiny, 0, 1));
        var longTinyDepletedHazards = ((HazardCarrier) longDepletedTiny).hbm$getHazards(
                NuclearWasteItem.stack(longDepletedTiny, 0, 1));
        check(helper, shortHazards.radiation() == 30.0F && shortHazards.heat() == 5.0F
                        && shortTinyHazards.radiation() == 3.0F && shortTinyHazards.heat() == 5.0F
                        && longTinyDepletedHazards.radiation() == 0.05F
                        && longTinyDepletedHazards.heat() == 0.0F,
                "Fresh/depleted and normal/tiny waste hazards must match HazardRegistry");
        check(helper, ((HazardCarrier) ModItems.SCRAP_NUCLEAR.get()).hbm$getHazards(
                        new ItemStack(ModItems.SCRAP_NUCLEAR.get())).radiation() == 1.0F
                        && ((HazardCarrier) ModItems.GEM_RAD.get()).hbm$getHazards(
                        new ItemStack(ModItems.GEM_RAD.get())).radiation() == 25.0F,
                "Nuclear scrap and radioactive gem must retain their source radiation");

        ItemEntity dropped = new ItemEntity(helper.getLevel(), 2.5D, 2.0D, 2.5D, longThorium);
        longWaste.onEntityItemUpdate(longThorium, dropped);
        check(helper, dropped.isInvulnerable()
                        && longWaste.getEntityLifespan(longThorium, helper.getLevel()) == Integer.MAX_VALUE,
                "Long-lived waste must retain the invulnerable, non-despawning EntityItemWaste behavior");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void radGenRunsQueuesOutputsRearPowerAndPreservesWasteClass(GameTestHelper helper) {
        BlockPos radRelative = new BlockPos(8, 4, 8);
        var radState = ModBlocks.MACHINE_RADGEN.get().defaultBlockState()
                .setValue(RadGenBlock.FACING, Direction.NORTH);
        helper.setBlock(radRelative, radState);
        RadGenBlockEntity radGen = helper.getBlockEntity(radRelative);

        BlockPos receiverRelative = radRelative.relative(Direction.SOUTH, 4);
        helper.setBlock(receiverRelative, ModBlocks.MACHINE_SHREDDER.get().defaultBlockState());
        MachineShredderBlockEntity receiver = helper.getBlockEntity(receiverRelative);

        ItemStack uranium233 = NuclearWasteItem.stack(ModItems.NUCLEAR_WASTE_SHORT.get(), 1, 1);
        uranium233.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("discarded source NBT"));
        radGen.setItem(0, uranium233);
        radGen.setPower(1_000L);
        RadGenBlockEntity.tick(helper.getLevel(), helper.absolutePos(radRelative), radState, radGen);

        check(helper, receiver.getPower() == 1_000L && radGen.getPower() == 1_500L,
                "RadGen must provide its previous buffer behind the rear port before generating this tick");
        check(helper, radGen.isOn() && radGen.output() == 1_500 && radGen.progress(0) == 1
                        && radGen.maxProgress(0) == 36_000 && radGen.production(0) == 1_500
                        && NuclearWasteItem.variant(radGen.processing(0)) == 1
                        && !radGen.processing(0).has(net.minecraft.core.component.DataComponents.CUSTOM_NAME),
                "A short-lived waste lane must start immediately with source timing and metadata-only copying");

        CompoundTag nearlyFinished = radGen.saveWithoutMetadata(helper.getLevel().registryAccess());
        int[] progress = nearlyFinished.getIntArray("progress");
        progress[0] = 35_999;
        nearlyFinished.putIntArray("progress", progress);
        radGen.loadWithComponents(nearlyFinished, helper.getLevel().registryAccess());
        RadGenBlockEntity.tick(helper.getLevel(), helper.absolutePos(radRelative), radState, radGen);

        ItemStack depleted = radGen.getItem(RadGenBlockEntity.LANE_COUNT);
        check(helper, depleted.is(ModItems.NUCLEAR_WASTE_SHORT_DEPLETED.get())
                        && NuclearWasteItem.variant(depleted) == 1 && radGen.processing(0).isEmpty(),
                "A completed queue must output the matching source depleted isotope and become idle");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void radGenTwelveLanesRemainIndependentAndClampOnlyStoredPower(GameTestHelper helper) {
        BlockPos relative = new BlockPos(8, 4, 8);
        var state = ModBlocks.MACHINE_RADGEN.get().defaultBlockState();
        helper.setBlock(relative, state);
        RadGenBlockEntity radGen = helper.getBlockEntity(relative);
        for (int lane = 0; lane < RadGenBlockEntity.LANE_COUNT; lane++) {
            radGen.setItem(lane, new ItemStack(ModItems.GEM_RAD.get()));
        }
        radGen.setPower(950_000L);
        RadGenBlockEntity.tick(helper.getLevel(), helper.absolutePos(relative), state, radGen);

        check(helper, radGen.getPower() == RadGenBlockEntity.MAX_POWER
                        && radGen.output() == 300_000 && radGen.isOn(),
                "Twelve radioactive gems must report 300kHE/t while only stored HE clamps at one million");
        for (int lane = 0; lane < RadGenBlockEntity.LANE_COUNT; lane++) {
            check(helper, radGen.progress(lane) == 1 && radGen.production(lane) == 25_000
                            && radGen.maxProgress(lane) == 36_000,
                    "Every RadGen queue must advance independently on the same tick");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wasteDrumCountsSixWaterFacesAndKeepsOneItemCells(GameTestHelper helper) {
        BlockPos relative = new BlockPos(4, 4, 4);
        BlockPos center = helper.absolutePos(relative);
        for (Direction direction : Direction.values()) {
            helper.getLevel().setBlock(center.relative(direction), Blocks.WATER.defaultBlockState(), 3);
        }
        check(helper, WasteDrumBlockEntity.adjacentWater(helper.getLevel(), center) == 6,
                "Spent Fuel Pool Drum must count all six adjacent water blocks");
        check(helper, WasteDrumRecipes.rollBound(1) == 72_000
                        && WasteDrumRecipes.rollBound(2) == 36_000
                        && WasteDrumRecipes.rollBound(3) == 24_000
                        && WasteDrumRecipes.rollBound(4) == 18_000
                        && WasteDrumRecipes.rollBound(5) == 14_400
                        && WasteDrumRecipes.rollBound(6) == 12_000,
                "Waste Drum cooling rolls must retain source 72000/water scaling");

        helper.getLevel().setBlock(center.above(), Blocks.OAK_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true), 3);
        check(helper, WasteDrumBlockEntity.adjacentWater(helper.getLevel(), center) == 5,
                "Waterlogged blocks must not substitute for source water blocks");

        WasteDrumBlockEntity drum = new WasteDrumBlockEntity(center,
                ModBlocks.MACHINE_WASTE_DRUM.get().defaultBlockState());
        check(helper, drum.getContainerSize() == 12 && drum.getMaxStackSize() == 1,
                "Spent Fuel Pool Drum must retain twelve one-item cells");
        check(helper, !drum.canPlaceItem(0, new ItemStack(Items.DIAMOND)),
                "Missing FuelPool/RBMK dependencies must remain gated instead of accepting substitutes");
        helper.succeed();
    }

    private static void assertFuel(GameTestHelper helper, ResourceLocation input, int power, int duration,
                                   String outputPath, boolean copyComponents) {
        RadGenFuelRecipes.Definition definition = RadGenFuelRecipes.definition(input);
        boolean outputMatches = outputPath == null ? definition != null && definition.output() == null
                : definition != null && definition.output() != null
                && definition.output().getPath().equals(outputPath);
        check(helper, definition != null && definition.power() == power
                        && definition.duration() == duration && outputMatches
                        && definition.copyComponents() == copyComponents,
                input + " must retain source output, rate and duration");
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
