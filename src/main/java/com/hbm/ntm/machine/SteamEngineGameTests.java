package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.anvil.AnvilRecipes;
import com.hbm.ntm.block.SteamEngineBlock;
import com.hbm.ntm.blockentity.SteamEngineBlockEntity;
import com.hbm.ntm.blockentity.SteamEngineProxyBlockEntity;
import com.hbm.ntm.item.ShellItem;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SteamEngineGameTests {
    private SteamEngineGameTests() { }

    @GameTest(template = "empty")
    public static void structureAndThreeUpperPortsMatchSource(GameTestHelper helper) {
        BlockPos core = new BlockPos(8, 2, 8);
        var parts = SteamEngineBlock.partPositions(core, Direction.SOUTH);
        check(helper, parts.size() == 42 && new HashSet<>(parts).size() == 42,
                "Steam Engine must occupy exactly seven by two by three cells");
        var connections = SteamEngineBlock.connections(core, Direction.SOUTH);
        check(helper, connections.size() == 3, "Source Steam Engine must expose exactly three ports");
        for (SteamEngineBlock.Connection connection : connections) {
            check(helper, connection.outward() == Direction.WEST && connection.port().getY() == core.getY() + 1,
                    "All SOUTH-facing source ports must be upper WEST-side cells");
            check(helper, connection.target().equals(connection.port().west()),
                    "Each external connection target must sit immediately outside its proxy cell");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullSteamBatchProducesExactSpentSteamAndHe(GameTestHelper helper) {
        BlockPos position = new BlockPos(3, 2, 3);
        SteamEngineBlockEntity engine = placeEngine(helper, position);
        engine.portFluidHandler().fill(new FluidStack(ModFluids.STEAM.get(), 2_000),
                IFluidHandler.FluidAction.EXECUTE);
        SteamEngineBlockEntity.tick(helper.getLevel(), helper.absolutePos(position),
                helper.getBlockState(position), engine);
        check(helper, engine.steamTank().isEmpty() && engine.spentSteamTank().getFluidAmount() == 20,
                "2,000 mB Steam must cool into exactly 20 mB Spent Steam");
        check(helper, engine.getPower() == 3_400L,
                "Twenty 200-HE operations at 85% efficiency must generate exactly 3,400 HE");
        check(helper, engine.acceleration() == 0.1F && engine.rotor() == 0.1F,
                "A productive first tick must accelerate and advance the flywheel by 0.1 degrees");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullSpentSteamTankStopsConversionAndDecelerates(GameTestHelper helper) {
        BlockPos position = new BlockPos(3, 2, 3);
        SteamEngineBlockEntity engine = placeEngine(helper, position);
        engine.portFluidHandler().fill(new FluidStack(ModFluids.STEAM.get(), 100),
                IFluidHandler.FluidAction.EXECUTE);
        engine.spentSteamTank().fill(new FluidStack(ModFluids.SPENTSTEAM.get(), 20),
                IFluidHandler.FluidAction.EXECUTE);
        SteamEngineBlockEntity.tick(helper.getLevel(), helper.absolutePos(position),
                helper.getBlockState(position), engine);
        check(helper, engine.steamTank().getFluidAmount() == 100 && engine.getPower() == 0L,
                "A full low-pressure tank must prevent Steam consumption and HE generation");
        check(helper, engine.acceleration() == 0.0F,
                "An idle engine must reduce acceleration by 0.1 and clamp at zero");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void proxyCapabilityIsTypedAndOutwardOnly(GameTestHelper helper) {
        BlockPos core = new BlockPos(4, 2, 4);
        BlockState coreState = coreState(Direction.SOUTH);
        helper.setBlock(core, coreState);
        BlockPos port = core.west().above();
        BlockState portState = coreState.setValue(SteamEngineBlock.PART_SIDE, 2)
                .setValue(SteamEngineBlock.PART_LENGTH, 1).setValue(SteamEngineBlock.PART_Y, 1);
        helper.setBlock(port, portState);
        SteamEngineProxyBlockEntity proxy = helper.getBlockEntity(port);
        check(helper, proxy.fluidHandler(Direction.WEST) != null && proxy.fluidHandler(Direction.EAST) == null,
                "The source ProxyCombo must connect only through its outward face");
        IFluidHandler handler = proxy.fluidHandler(Direction.WEST);
        check(helper, handler != null && handler.fill(new FluidStack(ModFluids.STEAM.get(), 100),
                        IFluidHandler.FluidAction.EXECUTE) == 100,
                "Port input must accept Steam");
        check(helper, handler.fill(new FluidStack(ModFluids.SPENTSTEAM.get(), 1),
                        IFluidHandler.FluidAction.EXECUTE) == 0,
                "Port input must reject Spent Steam");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void constructionRetainsExactTierAndComponentIdentities(GameTestHelper helper) {
        AnvilRecipes.Construction recipe = AnvilRecipes.byId(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "anvil/machine_steam_engine"));
        check(helper, recipe != null && recipe.validForTier(2) && !recipe.validForTier(1),
                "Steam Engine construction must remain exact Tier 2");
        check(helper, recipe.inputs().size() == 5
                        && recipe.inputs().get(0).count() == 16
                        && recipe.inputs().get(0).matches(new ItemStack(ModItems.REINFORCED_STONE_ITEM.get()))
                        && recipe.inputs().get(1).count() == 12
                        && recipe.inputs().get(2).count() == 2
                        && recipe.inputs().get(2).matches(ShellItem.steel(ModItems.SHELL.get(), 1))
                        && recipe.inputs().get(3).count() == 4
                        && recipe.inputs().get(3).matches(new ItemStack(ModItems.COIL_COPPER.get()))
                        && recipe.inputs().get(4).count() == 1
                        && recipe.inputs().get(4).matches(new ItemStack(ModItems.GEAR_LARGE.get())),
                "Construction must keep 16 Dense Stone, 12 Steel Plates, two Steel Shells, four Copper Coils and one Large Gear");
        helper.succeed();
    }

    private static SteamEngineBlockEntity placeEngine(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, coreState(Direction.SOUTH));
        return helper.getBlockEntity(position);
    }

    private static BlockState coreState(Direction facing) {
        return ModBlocks.MACHINE_STEAM_ENGINE.get().defaultBlockState()
                .setValue(SteamEngineBlock.FACING, facing)
                .setValue(SteamEngineBlock.PART_LENGTH, 1)
                .setValue(SteamEngineBlock.PART_SIDE, 1)
                .setValue(SteamEngineBlock.PART_Y, 0);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
