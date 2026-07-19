package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.SteamCondenserBlockEntity;
import com.hbm.ntm.item.CastPlateItem;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SteamCondenserGameTests {
    private SteamCondenserGameTests() { }

    @GameTest(template = "empty")
    public static void fullTankConvertsOneToOneInOneTick(GameTestHelper helper) {
        BlockPos position = new BlockPos(3, 2, 3);
        helper.setBlock(position, ModBlocks.MACHINE_CONDENSER.get());
        SteamCondenserBlockEntity condenser = helper.getBlockEntity(position);
        check(helper, condenser.fluidHandler().fill(new FluidStack(ModFluids.SPENTSTEAM.get(), 100),
                        IFluidHandler.FluidAction.EXECUTE) == 100,
                "Default Condenser input must hold exactly 100mB LPS");
        SteamCondenserBlockEntity.tick(helper.getLevel(), helper.absolutePos(position),
                helper.getBlockState(position), condenser);
        check(helper, condenser.spentSteamTank().isEmpty() && condenser.waterTank().getFluidAmount() == 100,
                "A full passive Condenser must convert 100mB LPS into 100mB Water in one tick");
        check(helper, condenser.throughput() == 100 && condenser.waterTimer() == 20,
                "Source throughput and 20-tick conversion indicator must reflect that batch");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void handlerAcceptsOnlyLpsAndDrainsOnlyWater(GameTestHelper helper) {
        BlockPos position = new BlockPos(3, 2, 3);
        helper.setBlock(position, ModBlocks.MACHINE_CONDENSER.get());
        SteamCondenserBlockEntity condenser = helper.getBlockEntity(position);
        IFluidHandler handler = condenser.fluidHandler();
        check(helper, handler.fill(new FluidStack(ModFluids.SPENTSTEAM.get(), 40),
                        IFluidHandler.FluidAction.EXECUTE) == 40
                        && handler.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 40),
                        IFluidHandler.FluidAction.EXECUTE) == 0,
                "All-side input must accept only Low-Pressure Steam");
        condenser.waterTank().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 30),
                IFluidHandler.FluidAction.EXECUTE);
        check(helper, handler.drain(new FluidStack(ModFluids.SPENTSTEAM.get(), 30),
                        IFluidHandler.FluidAction.EXECUTE).isEmpty()
                        && handler.drain(30, IFluidHandler.FluidAction.EXECUTE).getAmount() == 30,
                "All-side output must expose only Water");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void craftingRecipeRequiresExactCastCopperPlate(GameTestHelper helper) {
        ItemStack steel = new ItemStack(ModItems.get("ingot_steel").get());
        ItemStack iron = new ItemStack(ModItems.get("plate_iron").get());
        ItemStack copperCast = CastPlateItem.create(ModItems.PLATE_CAST.get(),
                CastPlateItem.CastPlateMaterial.COPPER, 1);
        CraftingInput input = CraftingInput.of(3, 3, List.of(
                steel.copy(), iron.copy(), steel.copy(),
                iron.copy(), copperCast, iron.copy(),
                steel.copy(), iron.copy(), steel.copy()));
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input,
                helper.getLevel()).orElseThrow();
        check(helper, recipe.value().assemble(input, helper.getLevel().registryAccess())
                        .is(ModItems.MACHINE_CONDENSER_ITEM.get()),
                "SIS/ICI/SIS with exact Cast Copper Plate must craft the Steam Condenser");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
