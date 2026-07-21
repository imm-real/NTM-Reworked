package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.anvil.AnvilRecipes;
import com.hbm.ntm.block.ResearchReactorBlock;
import com.hbm.ntm.blockentity.ResearchReactorBlockEntity;
import com.hbm.ntm.blockentity.ResearchReactorProxyBlockEntity;
import com.hbm.ntm.hazard.HazardProfile;
import com.hbm.ntm.item.DepletedPlateFuelItem;
import com.hbm.ntm.item.PlateFuelItem;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.recipe.WasteDrumRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResearchReactorGameTests {
    private ResearchReactorGameTests() { }

    @GameTest(template = "empty")
    public static void plateCurvesAndLifeThresholdMatchSource(GameTestHelper helper) {
        checkReaction(helper, PlateFuelItem.Type.U233, 100, 50, 100);
        checkReaction(helper, PlateFuelItem.Type.U235, 100, 40, 100);
        checkReaction(helper, PlateFuelItem.Type.MOX, 99, 50, 99);
        checkReaction(helper, PlateFuelItem.Type.PU239, 100, 49, 100);
        checkReaction(helper, PlateFuelItem.Type.SA326, 100, 80, 100);
        checkReaction(helper, PlateFuelItem.Type.RA226BE, 0, 30, 30);
        checkReaction(helper, PlateFuelItem.Type.PU238BE, 9_999, 50, 50);

        PlateFuelItem strict = fuel(PlateFuelItem.Type.U233);
        ItemStack stack = new ItemStack(strict);
        strict.setLife(stack, strict.type().maxLife());
        check(helper, !strict.depleted(stack), "Plate fuel must survive exactly at maxLife");
        strict.react(stack, 1);
        check(helper, strict.depleted(stack), "Plate fuel must deplete only after life exceeds maxLife");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void plateHazardsInterpolateAndHotWasteCoolsExactly(GameTestHelper helper) {
        PlateFuelItem uranium = fuel(PlateFuelItem.Type.U233);
        ItemStack plate = new ItemStack(uranium);
        checkClose(helper, uranium.hbm$getHazards(plate).radiation(), 5.0F,
                "Fresh HEU-233 plate radiation");
        uranium.setLife(plate, uranium.type().maxLife());
        checkClose(helper, uranium.hbm$getHazards(plate).radiation(), 195.0F,
                "Depleted HEU-233 plate radiation");

        PlateFuelItem schrabidium = fuel(PlateFuelItem.Type.SA326);
        checkClose(helper, schrabidium.hbm$getHazards(new ItemStack(schrabidium)).blinding(), 20.0F,
                "HES-326 plate blinding hazard");

        DepletedPlateFuelItem waste = ModItems.WASTE_PLATE_U233.get();
        ItemStack hot = DepletedPlateFuelItem.hot(waste);
        HazardProfile hotHazard = waste.hbm$getHazards(hot);
        checkClose(helper, hotHazard.radiation(), 195.0F, "Hot HEU-233 waste radiation");
        checkClose(helper, hotHazard.heat(), 5.0F, "Hot plate heat hazard");
        check(helper, WasteDrumRecipes.isInput(hot) && !WasteDrumRecipes.mayExtract(hot),
                "Every hot depleted plate must enter the Waste Drum and remain locked while hot");
        ItemStack cooled = WasteDrumRecipes.cooledResult(hot);
        check(helper, !DepletedPlateFuelItem.isHot(cooled) && WasteDrumRecipes.mayExtract(cooled),
                "The Waste Drum recipe must produce the cooled metadata-equivalent plate");
        checkClose(helper, waste.hbm$getHazards(cooled).radiation(), 14.625F,
                "Cooled HEU-233 waste radiation");
        checkClose(helper, waste.hbm$getHazards(cooled).heat(), 0.0F,
                "Cooled plate heat hazard");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void upperDummiesProxyTheTwelveSlotInventory(GameTestHelper helper) {
        Block registered = BuiltInRegistries.BLOCK.get(ResearchReactorBlock.BLOCK_ID);
        check(helper, registered instanceof ResearchReactorBlock,
                "Research Reactor block must be registered");
        if (!(registered instanceof ResearchReactorBlock reactorBlock)) return;

        BlockPos relative = new BlockPos(2, 2, 2);
        BlockState lower = reactorBlock.defaultBlockState();
        helper.setBlock(relative, lower);
        helper.setBlock(relative.above(), lower.setValue(ResearchReactorBlock.PART, 1));
        helper.setBlock(relative.above(2), lower.setValue(ResearchReactorBlock.PART, 2));

        check(helper, helper.getBlockEntity(relative.above()) instanceof ResearchReactorProxyBlockEntity
                        && helper.getBlockEntity(relative.above(2)) instanceof ResearchReactorProxyBlockEntity,
                "Both upper source dummies must proxy the research reactor inventory");
        BlockPos middle = helper.absolutePos(relative.above());
        check(helper, helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                        middle, Direction.NORTH) != null,
                "Automation must reach the research inventory through either upper dummy");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allSevenPlateFuelsKeepTheirTierFourSourceRecipes(GameTestHelper helper) {
        String[] names = {"u233", "u235", "mox", "pu239", "sa326", "ra226be", "pu238be"};
        for (String name : names) {
            AnvilRecipes.Construction recipe = AnvilRecipes.byId(ResourceLocation.fromNamespaceAndPath(
                    HbmNtm.MOD_ID, "anvil/plate_fuel_" + name));
            check(helper, recipe != null && !recipe.validForTier(3) && recipe.validForTier(4)
                            && recipe.inputs().size() == 1 && recipe.inputs().getFirst().count() == 1
                            && recipe.icon().is(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(
                            HbmNtm.MOD_ID, "plate_fuel_" + name))),
                    "Plate fuel " + name + " must retain its one-input Tier-4 Anvil recipe");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void coolingAndAutomationRetainSourceQuirks(GameTestHelper helper) {
        check(helper, ResearchReactorBlockEntity.coolHeat(10_000, 0) == 9_999,
                "No-water cooling must remove exactly one heat per tick");
        check(helper, ResearchReactorBlockEntity.coolHeat(10_000, 12) == 9_300,
                "Twelve water positions must cool seven percent");
        check(helper, ResearchReactorBlockEntity.coolHeat(10_000, 14) == 9_183,
                "All fourteen water positions must keep the old divide-by-twelve overcooling");

        Block registered = BuiltInRegistries.BLOCK.get(ResearchReactorBlock.BLOCK_ID);
        check(helper, registered instanceof ResearchReactorBlock,
                "hbm:machine_reactor_small must be registered as ResearchReactorBlock");
        if (!(registered instanceof ResearchReactorBlock reactorBlock)) return;
        ResearchReactorBlockEntity reactor = new ResearchReactorBlockEntity(
                BlockPos.ZERO, reactorBlock.defaultBlockState());
        PlateFuelItem fuel = fuel(PlateFuelItem.Type.U235);
        ItemStack stack = new ItemStack(fuel);
        check(helper, reactor.getSlotsForFace(Direction.NORTH).length == 12,
                "Sided automation must see all twelve source slots");
        check(helper, reactor.canPlaceItemThroughFace(0, stack, Direction.NORTH),
                "Sided automation must insert plate fuel into slot zero");
        check(helper, !reactor.canPlaceItemThroughFace(1, stack, Direction.NORTH),
                "Sided automation must reject insertion into the other eleven slots");
        check(helper, !reactor.canTakeItemThroughFace(0, stack, Direction.NORTH),
                "The source ItemStack identity bug must prevent sided extraction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void deshBlockCountsAsReactorRadiationShielding(GameTestHelper helper) {
        Block desh = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block_desh"));
        check(helper, desh != Blocks.AIR, "block_desh must be registered before it can shield the reactor");
        check(helper, ResearchReactorBlockEntity.isRadiationShieldingBlock(desh.defaultBlockState()),
                "The Research Reactor must treat a Reinforced Block of Desh as radiation shielding");
        Block lead = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block_lead"));
        check(helper, ResearchReactorBlockEntity.isRadiationShieldingBlock(lead.defaultBlockState()),
                "Lead must remain reactor shielding");
        check(helper, !ResearchReactorBlockEntity.isRadiationShieldingBlock(Blocks.DIRT.defaultBlockState()),
                "Plain dirt must never count as reactor shielding");
        helper.succeed();
    }

    private static void checkReaction(GameTestHelper helper, PlateFuelItem.Type type,
                                      int input, int output, int life) {
        PlateFuelItem fuel = fuel(type);
        ItemStack stack = new ItemStack(fuel);
        check(helper, fuel.react(stack, input) == output,
                type + " must produce exact source output " + output + " for flux " + input);
        check(helper, fuel.life(stack) == life,
                type + " must accumulate exact source life " + life + " for flux " + input);
    }

    private static PlateFuelItem fuel(PlateFuelItem.Type type) {
        return switch (type) {
            case U233 -> ModItems.PLATE_FUEL_U233.get();
            case U235 -> ModItems.PLATE_FUEL_U235.get();
            case MOX -> ModItems.PLATE_FUEL_MOX.get();
            case PU239 -> ModItems.PLATE_FUEL_PU239.get();
            case SA326 -> ModItems.PLATE_FUEL_SA326.get();
            case RA226BE -> ModItems.PLATE_FUEL_RA226BE.get();
            case PU238BE -> ModItems.PLATE_FUEL_PU238BE.get();
        };
    }

    private static void checkClose(GameTestHelper helper, float actual, float expected, String name) {
        check(helper, Math.abs(actual - expected) < 0.0001F,
                name + " must be " + expected + ", got " + actual);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
