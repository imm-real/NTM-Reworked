package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.BlastFurnaceBlock;
import com.hbm.ntm.block.StoneResourceBlock;
import com.hbm.ntm.blockentity.BlastFurnaceBlockEntity;
import com.hbm.ntm.blockentity.BlastFurnaceProxyBlockEntity;
import com.hbm.ntm.foundry.FoundryMaterial;
import com.hbm.ntm.item.FoundryIngotItem;
import com.hbm.ntm.item.StoneResourceBlockItem;
import com.hbm.ntm.recipe.BlastFurnaceRecipes;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlastFurnaceGameTests {
    private BlastFurnaceGameTests() { }

    @GameTest(template = "empty")
    public static void sourceStructureHasSixtyThreeCellsAndSevenPorts(GameTestHelper helper) {
        BlockPos clicked = new BlockPos(4, 1, 3);
        Direction facing = Direction.NORTH;
        BlastFurnaceBlock block = ModBlocks.MACHINE_BLAST_FURNACE.get();
        BlockPos core = clicked.relative(facing.getOpposite());
        BlockState clickedState = block.stateForPart(clicked, core, facing);
        helper.setBlock(clicked, clickedState);
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(clicked), clickedState,
                helper.makeMockPlayer(GameType.SURVIVAL),
                new ItemStack(ModItems.MACHINE_BLAST_FURNACE_ITEM.get()));

        BlockPos absoluteCore = helper.absolutePos(core);
        int cells = 0;
        int ports = 0;
        for (BlockPos part : BlastFurnaceBlock.partPositions(absoluteCore)) {
            BlockState state = helper.getLevel().getBlockState(part);
            if (state.is(block)) cells++;
            if (helper.getLevel().getBlockEntity(part) instanceof BlastFurnaceProxyBlockEntity) ports++;
            check(helper, !state.is(block) || BlastFurnaceBlock.corePosition(part, state).equals(absoluteCore),
                    "Every Blast Furnace part must resolve to the same bottom-center core");
        }
        check(helper, cells == 63 && ports == 7,
                "Blast Furnace must preserve its complete 3x7x3 body and seven source access proxies");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void steelBootstrapConsumesTwoIronSandAndHalfCoal(GameTestHelper helper) {
        BlastFurnaceBlockEntity furnace = bareFurnace(helper, new BlockPos(3, 1, 3));
        furnace.setItem(BlastFurnaceBlockEntity.INPUT_FIRST, new ItemStack(Items.IRON_INGOT, 2));
        furnace.setItem(BlastFurnaceBlockEntity.INPUT_SECOND, new ItemStack(Items.SAND));
        furnace.setFuelForTest(BlastFurnaceBlockEntity.FUEL_RATE);
        furnace.setProgressForTest(0.9995D);
        tick(helper, furnace);

        ItemStack steel = furnace.getItem(BlastFurnaceBlockEntity.OUTPUT_FIRST);
        ItemStack slag = furnace.getItem(BlastFurnaceBlockEntity.OUTPUT_SECOND);
        check(helper, steel.is(ModItems.get("ingot_steel").get()) && steel.getCount() == 2
                        && FoundryIngotItem.material(slag) == FoundryMaterial.SLAG && slag.getCount() == 1,
                "Two Iron Ingots plus Sand must produce two Steel Ingots and one source Slag ingot");
        check(helper, furnace.getItem(BlastFurnaceBlockEntity.INPUT_FIRST).isEmpty()
                        && furnace.getItem(BlastFurnaceBlockEntity.INPUT_SECOND).isEmpty()
                        && furnace.fuel() == 0 && furnace.flueTank().getFluidAmount() == 100,
                "One Steel operation must consume exact inputs, 800 fuel points, and emit 100mB Flue Gas");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recipesAreOrderIndependentAndPreserveSourceTable(GameTestHelper helper) {
        check(helper, BlastFurnaceRecipes.all().size() == 9,
                "All nine registered source Blast Furnace recipes must be active");
        var reversed = BlastFurnaceRecipes.find(new ItemStack(Items.SAND), new ItemStack(Items.IRON_INGOT, 2));
        check(helper, reversed != null && reversed.name().equals("blast.steelFromIngot")
                        && BlastFurnaceRecipes.inputsMatch(reversed,
                        new ItemStack(Items.SAND), new ItemStack(Items.IRON_INGOT, 2)),
                "Both Blast Furnace input slots must accept either recipe ordering");
        ItemStack limestone = StoneResourceBlockItem.create(ModItems.STONE_RESOURCE_ITEM.get(),
                StoneResourceBlock.Type.LIMESTONE, 1);
        var firebrick = BlastFurnaceRecipes.find(limestone, new ItemStack(Items.CLAY_BALL, 6));
        check(helper, firebrick != null && firebrick.name().equals("blast.firebrickLimestone")
                        && firebrick.primary().is(ModItems.get("ingot_firebrick").get())
                        && firebrick.primary().getCount() == 8,
                "Limestone plus six Clay Balls must preserve the eight-Firebrick source recipe");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void airBlastUsesSourceSpeedCurveAndFivePercentDecay(GameTestHelper helper) {
        BlastFurnaceBlockEntity furnace = bareFurnace(helper, new BlockPos(3, 1, 3));
        furnace.setItem(BlastFurnaceBlockEntity.INPUT_FIRST, new ItemStack(Items.IRON_INGOT, 2));
        furnace.setItem(BlastFurnaceBlockEntity.INPUT_SECOND, new ItemStack(Items.SAND));
        furnace.setFuelForTest(BlastFurnaceBlockEntity.FUEL_RATE);
        furnace.airTank().fill(new FluidStack(ModFluids.AIRBLAST.get(), 4_000),
                IFluidHandler.FluidAction.EXECUTE);
        tick(helper, furnace);
        check(helper, furnace.speed() == 5D && Math.abs(furnace.progress() - 5D / 800D) < 0.000001D,
                "A full Air Blast tank must clamp Blast Furnace speed to the source 500%");
        check(helper, furnace.airTank().getFluidAmount() == 3_800,
                "Air Blast must lose exactly five percent of its remaining amount every tick");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fuelAndPortCapabilitiesPreserveSourceRules(GameTestHelper helper) {
        check(helper, BlastFurnaceBlockEntity.burnTime(new ItemStack(Items.COAL)) == 1_600
                        && BlastFurnaceBlockEntity.burnTime(new ItemStack(Items.OAK_LOG)) == 300
                        && BlastFurnaceBlockEntity.burnTime(new ItemStack(Items.OAK_PLANKS)) == 0
                        && BlastFurnaceBlockEntity.burnTime(new ItemStack(Items.STICK)) == 0
                        && BlastFurnaceBlockEntity.burnTime(new ItemStack(Items.CHARCOAL)) == 0
                        && BlastFurnaceBlockEntity.burnTime(new ItemStack(Items.LAVA_BUCKET)) == 0,
                "Blast Furnace fuel must retain the source module's named-category filter and Wood heat rule");

        BlastFurnaceBlockEntity furnace = placeFurnace(helper, new BlockPos(4, 1, 4));
        BlockPos eastPort = furnace.getBlockPos().east();
        check(helper, helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                        eastPort, Direction.EAST) != null,
                "Every source access proxy must expose sided item automation");
        IFluidHandler handler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                eastPort, Direction.EAST);
        check(helper, handler != null
                        && handler.fill(new FluidStack(ModFluids.AIRBLAST.get(), 1_000),
                        IFluidHandler.FluidAction.EXECUTE) == 1_000
                        && handler.fill(new FluidStack(ModFluids.FLUE.get(), 1),
                        IFluidHandler.FluidAction.EXECUTE) == 0,
                "Blast Furnace ports must accept only Hot Air Blast as their fluid input");
        furnace.flueTank().fill(new FluidStack(ModFluids.FLUE.get(), 500), IFluidHandler.FluidAction.EXECUTE);
        FluidStack drained = handler.drain(250, IFluidHandler.FluidAction.EXECUTE);
        check(helper, drained.is(ModFluids.FLUE.get()) && drained.getAmount() == 250,
                "The same source port must expose Flue Gas as output without draining Air Blast");
        helper.succeed();
    }

    private static BlastFurnaceBlockEntity bareFurnace(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.MACHINE_BLAST_FURNACE.get().defaultBlockState());
        return helper.getBlockEntity(position);
    }

    private static BlastFurnaceBlockEntity placeFurnace(GameTestHelper helper, BlockPos core) {
        BlastFurnaceBlock block = ModBlocks.MACHINE_BLAST_FURNACE.get();
        Direction facing = Direction.SOUTH;
        BlockPos clicked = core.relative(facing);
        BlockState clickedState = block.stateForPart(clicked, core, facing);
        helper.setBlock(clicked, clickedState);
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(clicked), clickedState,
                helper.makeMockPlayer(GameType.SURVIVAL),
                new ItemStack(ModItems.MACHINE_BLAST_FURNACE_ITEM.get()));
        return helper.getBlockEntity(core);
    }

    private static void tick(GameTestHelper helper, BlastFurnaceBlockEntity furnace) {
        BlastFurnaceBlockEntity.tick(helper.getLevel(), furnace.getBlockPos(), furnace.getBlockState(), furnace);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
