package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.CombinationOvenBlock;
import com.hbm.ntm.blockentity.CombinationOvenBlockEntity;
import com.hbm.ntm.blockentity.CombinationOvenProxyBlockEntity;
import com.hbm.ntm.blockentity.ElectricHeaterBlockEntity;
import com.hbm.ntm.recipe.CombinationOvenRecipes;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.thermal.FireboxFuel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombinationOvenGameTests {
    private CombinationOvenGameTests() { }

    @GameTest(template = "empty")
    public static void placementBuildsExactEighteenCellsWithUniversalProxies(GameTestHelper helper) {
        CombinationOvenBlock block = ModBlocks.FURNACE_COMBINATION.get();
        BlockPos clicked = new BlockPos(4, 1, 4);
        Direction facing = Direction.SOUTH;
        BlockPos core = clicked.relative(facing.getOpposite());
        BlockState placed = block.stateForPart(clicked, core, facing);
        helper.setBlock(clicked, placed);
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(clicked), placed,
                helper.makeMockPlayer(GameType.SURVIVAL),
                new ItemStack(ModItems.FURNACE_COMBINATION_ITEM.get()));

        int cores = 0;
        int proxies = 0;
        for (BlockPos part : CombinationOvenBlock.partPositions(core)) {
            BlockState state = helper.getBlockState(part);
            check(helper, state.is(block) && CombinationOvenBlock.corePosition(part, state).equals(core),
                    "Every 3x2x3 Combination Oven cell must resolve to one bottom-center core");
            BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(part));
            if (entity instanceof CombinationOvenBlockEntity) cores++;
            if (entity instanceof CombinationOvenProxyBlockEntity) {
                proxies++;
                check(helper, helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                                helper.absolutePos(part), Direction.NORTH) != null
                                && helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                                helper.absolutePos(part), Direction.NORTH) != null,
                        "Each source ProxyCombo cell must expose item and output-fluid capabilities");
            }
        }
        check(helper, cores == 1 && proxies == 17,
                "Combination Oven must preserve one core plus seventeen universal proxy cells");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void heatDiffusionAndCoolingMatchSource(GameTestHelper helper) {
        BlockPos position = new BlockPos(4, 2, 4);
        CombinationOvenBlockEntity oven = bareOven(helper, position);
        helper.setBlock(position.below(), ModBlocks.HEATER_ELECTRIC.get().defaultBlockState());
        ElectricHeaterBlockEntity source = helper.getBlockEntity(position.below());

        setSourceHeat(helper, source, 100_000);
        oven.setHeatForTest(0);
        oven.pullHeatForTest();
        check(helper, oven.heat() == 25_000 && source.getHeatStored() == 75_000,
                "A 100,000 TU delta must transfer ceil(delta*0.25)=25,000 TU");

        setSourceHeat(helper, source, 5_000);
        oven.setHeatForTest(10_000);
        oven.pullHeatForTest();
        check(helper, oven.heat() == 9_990 && source.getHeatStored() == 5_000,
                "A colder source must not receive heat and the oven must cool by heat/1000");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cokeCyclePreservesProgressHeatAndCreosoteAmounts(GameTestHelper helper) {
        CombinationOvenBlockEntity oven = bareOven(helper, new BlockPos(4, 1, 4));
        oven.setItem(CombinationOvenBlockEntity.INPUT, new ItemStack(Items.COAL));
        oven.setHeatForTest(CombinationOvenBlockEntity.MAX_HEAT);
        oven.setProgressForTest(CombinationOvenBlockEntity.PROCESS_TIME - 1);
        tick(helper, oven);

        check(helper, oven.getItem(CombinationOvenBlockEntity.INPUT).isEmpty()
                        && oven.getItem(CombinationOvenBlockEntity.OUTPUT).is(ModItems.COKE_COAL.get()),
                "One Coal must become exactly one Coal Coke");
        check(helper, oven.progress() == 999 && oven.heat() == 99_000 && oven.wasOn(),
                "A full oven must advance by heat/100=1,000, preserve overshoot and spend 1,000 TU");
        check(helper, oven.tank().getFluid().is(ModFluids.COALCREOSOTE.get())
                        && oven.tank().getFluidAmount() == 100,
                "One Coal must emit exactly 100 mB Coal Tar Creosote");
        IFluidHandler output = oven.fluidHandler();
        check(helper, output.fill(new FluidStack(ModFluids.COALCREOSOTE.get(), 1),
                        IFluidHandler.FluidAction.EXECUTE) == 0
                        && output.drain(50, IFluidHandler.FluidAction.EXECUTE).getAmount() == 50,
                "Combination Oven fluid capability must remain output-only");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dependencyCompleteRecipeAndFuelTablesMatchSource(GameTestHelper helper) {
        check(helper, CombinationOvenRecipes.all().size() == 9,
                "The Combination Oven table must contain nine registered source recipes");
        var coal = CombinationOvenRecipes.find(new ItemStack(Items.COAL));
        var lignite = CombinationOvenRecipes.find(
                new ItemStack(ModItems.legacyOreResourceItem("lignite").get()));
        var tar = CombinationOvenRecipes.find(new ItemStack(ModItems.OIL_TAR.get()));
        check(helper, coal != null && coal.result().is(ModItems.COKE_COAL.get())
                        && coal.resultFluid().isSame(ModFluids.COALCREOSOTE.get()) && coal.fluidAmount() == 100,
                "Coal must preserve the 1 Coke plus 100 mB Creosote recipe");
        check(helper, lignite != null && lignite.result().is(ModItems.COKE_LIGNITE.get())
                        && lignite.fluidAmount() == 50,
                "Lignite must preserve the 1 Coke plus 50 mB Creosote recipe");
        check(helper, tar != null && tar.result().is(ModItems.COKE_PETROLEUM.get())
                        && tar.resultFluid() == null,
                "Oil Tar must preserve its dry Petroleum Coke recipe");
        check(helper, FireboxFuel.rawBurnTime(new ItemStack(ModItems.COKE_COAL.get())) == 3_200
                        && FireboxFuel.rawBurnTime(new ItemStack(ModItems.BLOCK_COKE_COAL_ITEM.get())) == 32_000
                        && FireboxFuel.rawBurnTime(new ItemStack(
                        ModItems.legacyOreResourceItem("lignite").get())) == 1_200
                        && FireboxFuel.rawBurnTime(new ItemStack(ModItems.get("powder_coal").get())) == 1_600,
                "Coke, Coke blocks, Lignite and Coal Powder must retain their exact source burn times");
        helper.succeed();
    }

    private static CombinationOvenBlockEntity bareOven(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.FURNACE_COMBINATION.get().defaultBlockState());
        return helper.getBlockEntity(position);
    }

    private static void tick(GameTestHelper helper, CombinationOvenBlockEntity oven) {
        CombinationOvenBlockEntity.tick(helper.getLevel(), oven.getBlockPos(), oven.getBlockState(), oven);
    }

    private static void setSourceHeat(GameTestHelper helper, ElectricHeaterBlockEntity source, int heat) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("heatEnergy", heat);
        source.loadWithComponents(tag, helper.getLevel().registryAccess());
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
