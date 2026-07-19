package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.anvil.AnvilRecipes;
import com.hbm.ntm.block.PumpBlock;
import com.hbm.ntm.blockentity.PumpBlockEntity;
import com.hbm.ntm.blockentity.PumpProxyBlockEntity;
import com.hbm.ntm.item.PipeItem;
import com.hbm.ntm.foundry.FoundryMaterial;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PumpGameTests {
    private PumpGameTests() { }

    @GameTest(template = "empty")
    public static void structureAndFourCardinalPortsMatchSource(GameTestHelper helper) {
        BlockPos core = new BlockPos(8, 3, 8);
        var parts = PumpBlock.partPositions(core);
        check(helper, parts.size() == 36 && new HashSet<>(parts).size() == 36,
                "Groundwater Pump must occupy exactly 3x4x3 cells");
        var connections = PumpBlock.connections(core);
        check(helper, connections.size() == 4, "Pump must expose all four source cardinal ports");
        for (PumpBlock.Connection connection : connections) {
            check(helper, connection.port().equals(core.relative(connection.outward()))
                            && connection.target().equals(core.relative(connection.outward(), 2)),
                    "Each external target must sit one block beyond its lower proxy");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void steamPumpConsumesAndProducesOneExactOperation(GameTestHelper helper) {
        BlockPos core = new BlockPos(5, 6, 5);
        PumpBlockEntity pump = placePump(helper, core, false);
        makeValidGround(helper, core);
        pump.portFluidHandler().fill(new FluidStack(ModFluids.STEAM.get(), 100),
                IFluidHandler.FluidAction.EXECUTE);
        PumpBlockEntity.tick(helper.getLevel(), helper.absolutePos(core), helper.getBlockState(core), pump);
        check(helper, pump.steamTank().isEmpty() && pump.spentSteamTank().getFluidAmount() == 1
                        && pump.waterTank().getFluidAmount() == 1_000 && pump.isOn(),
                "Steam Pump must trade 100mB Steam for 1mB LPS and 1,000mB Water");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void electricPumpConsumesAndProducesOneExactOperation(GameTestHelper helper) {
        BlockPos core = new BlockPos(5, 6, 5);
        PumpBlockEntity pump = placePump(helper, core, true);
        makeValidGround(helper, core);
        pump.setPower(1_000L);
        PumpBlockEntity.tick(helper.getLevel(), helper.absolutePos(core), helper.getBlockState(core), pump);
        check(helper, pump.getPower() == 0L && pump.waterTank().getFluidAmount() == 10_000 && pump.isOn(),
                "Electric Pump must trade 1,000HE for exactly 10,000mB Water");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void proxyCapabilitiesAreTypedAndOutwardOnly(GameTestHelper helper) {
        BlockPos core = new BlockPos(5, 3, 5);
        placePump(helper, core, false);
        BlockPos west = core.west();
        BlockState proxyState = steamCore().setValue(PumpBlock.PART_X, 0);
        helper.setBlock(west, proxyState);
        PumpProxyBlockEntity proxy = helper.getBlockEntity(west);
        IFluidHandler handler = proxy.fluidHandler(Direction.WEST);
        check(helper, handler != null && proxy.fluidHandler(Direction.EAST) == null,
                "Pump ProxyCombo must expose only its outward face");
        check(helper, handler.fill(new FluidStack(ModFluids.STEAM.get(), 100),
                        IFluidHandler.FluidAction.EXECUTE) == 100
                        && handler.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 100),
                        IFluidHandler.FluidAction.EXECUTE) == 0,
                "Steam port must accept Steam and reject Water input");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void leadPipeAndBothConstructionsKeepSourceIdentities(GameTestHelper helper) {
        AnvilRecipes.Construction lead = recipe("pipe_lead");
        AnvilRecipes.Construction steam = recipe("pump_steam");
        AnvilRecipes.Construction electric = recipe("pump_electric");
        check(helper, lead != null && lead.validForTier(1)
                        && PipeItem.isLead(lead.outputs().getFirst().stack().get()),
                "Three Lead Plates must produce the metadata-8200 Lead Pipe");
        ItemStack leadPipe = PipeItem.lead(ModItems.PIPE.get(), 1);
        FoundryMaterial.MaterialAmount remelted = FoundryMaterial.fromItem(leadPipe);
        check(helper, remelted != null && remelted.material() == FoundryMaterial.LEAD
                        && remelted.amount() == FoundryMaterial.CAST_PLATE,
                "Lead Pipe must remelt as its exact three-ingot 216-unit Lead identity");
        check(helper, steam != null && steam.validForTier(2) && !steam.validForTier(1)
                        && steam.inputs().size() == 4 && steam.inputs().get(0).count() == 8
                        && steam.inputs().get(0).matches(new ItemStack(Items.COBBLESTONE))
                        && steam.inputs().get(1).count() == 16 && steam.inputs().get(2).count() == 8
                        && steam.inputs().get(3).count() == 2 && steam.inputs().get(3).matches(leadPipe),
                "Steam Pump must preserve its exact Tier-2 source construction");
        check(helper, electric != null && electric.validForTier(3) && !electric.validForTier(2)
                        && electric.inputs().size() == 5
                        && electric.inputs().get(0).matches(new ItemStack(Items.STONE_BRICKS))
                        && electric.inputs().get(1).count() == 16
                        && electric.inputs().get(2).count() == 4 && electric.inputs().get(2).matches(leadPipe)
                        && electric.inputs().get(3).count() == 2 && electric.inputs().get(4).count() == 4,
                "Electric Pump must preserve its exact Tier-3 source construction");
        helper.succeed();
    }

    private static PumpBlockEntity placePump(GameTestHelper helper, BlockPos core, boolean electric) {
        helper.setBlock(core, electric ? electricCore() : steamCore());
        return helper.getBlockEntity(core);
    }

    private static BlockState steamCore() {
        return ModBlocks.PUMP_STEAM.get().defaultBlockState().setValue(PumpBlock.FACING, Direction.SOUTH);
    }

    private static BlockState electricCore() {
        return ModBlocks.PUMP_ELECTRIC.get().defaultBlockState().setValue(PumpBlock.FACING, Direction.SOUTH);
    }

    private static void makeValidGround(GameTestHelper helper, BlockPos core) {
        for (int y = -1; y >= -4; y--) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) helper.setBlock(core.offset(x, y, z), Blocks.DIRT);
            }
        }
    }

    private static AnvilRecipes.Construction recipe(String path) {
        return AnvilRecipes.byId(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "anvil/" + path));
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
