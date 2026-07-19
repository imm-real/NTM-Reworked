package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.ZirnoxBlock;
import com.hbm.ntm.blockentity.ZirnoxBlockEntity;
import com.hbm.ntm.item.ZirnoxRodItem;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZirnoxGameTests {
    private ZirnoxGameTests() { }

    @GameTest(template = "empty")
    public static void sourceVolumeAndPortsArePreserved(GameTestHelper helper) {
        BlockPos core = new BlockPos(4, 2, 4);
        check(helper, ZirnoxBlock.partPositions(core, Direction.NORTH).size() == 83,
                "ZIRNOX must occupy the source 83-cell volume");
        check(helper, ZirnoxBlock.connections(core, Direction.NORTH).size() == 4
                        && ZirnoxBlock.connections(core, Direction.NORTH).stream()
                        .map(ZirnoxBlock.Connection::port).distinct().count() == 4,
                "ZIRNOX must retain four distinct lateral gas/fluid ports");
        check(helper, ModBlocks.REACTOR_ZIRNOX.get().defaultDestroyTime() == 5F
                        && ModBlocks.REACTOR_ZIRNOX.get().getExplosionResistance() == 60F,
                "Live ZIRNOX must retain source hardness 5 and effective resistance 60");
        check(helper, ModBlocks.ZIRNOX_DESTROYED.get().defaultDestroyTime() == 100F
                        && ModBlocks.ZIRNOX_DESTROYED.get().getExplosionResistance() == 480F,
                "The destroyed ZIRNOX must retain source hardness 100 and effective resistance 480");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void neighborFluxAndBreedingMatchSource(GameTestHelper helper) {
        ZirnoxBlockEntity reactor = place(helper);
        ItemStack uranium = new ItemStack(ModItems.ROD_ZIRNOX_URANIUM_FUEL.get());
        ItemStack adjacentUranium = new ItemStack(ModItems.ROD_ZIRNOX_URANIUM_FUEL.get());
        ItemStack thorium = new ItemStack(ModItems.ROD_ZIRNOX_TH232.get());
        reactor.setItem(0, uranium);
        reactor.setItem(1, adjacentUranium);
        reactor.setItem(8, thorium);
        reactor.toggle();
        tick(helper, reactor);
        check(helper, reactor.heat() == 190,
                "Fuel must receive self plus adjacent fuel flux, but breeder rods must not add flux");
        check(helper, ((ZirnoxRodItem) uranium.getItem()).life(uranium) == 2
                        && ((ZirnoxRodItem) adjacentUranium.getItem()).life(adjacentUranium) == 2
                        && ((ZirnoxRodItem) thorium.getItem()).life(thorium) == 1,
                "Fuel and breeder life must advance from the exact source neighbor graph");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gasCoolingProducesSuperhotSteam(GameTestHelper helper) {
        ZirnoxBlockEntity reactor = place(helper);
        reactor.fluidHandler().fill(new FluidStack(ModFluids.CARBONDIOXIDE.get(), 14_000),
                IFluidHandler.FluidAction.EXECUTE);
        reactor.fluidHandler().fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.EXECUTE);
        for (int slot = 0; slot < ZirnoxBlockEntity.FUEL_SLOTS; slot++)
            reactor.setItem(slot, new ItemStack(ModItems.ROD_ZIRNOX_LES_FUEL.get()));
        reactor.toggle();
        tick(helper, reactor);
        check(helper, reactor.steamTank().getFluidAmount() > 0
                        && reactor.steamTank().getFluid().getFluid().isSame(ModFluids.SUPERHOTSTEAM.get()),
                "A fueled CO2/water ZIRNOX must produce Superheated Steam");
        check(helper, reactor.pressure() > 28_000 && reactor.pressure() < ZirnoxBlockEntity.MAX_PRESSURE,
                "Pressure must combine the source CO2 baseline with its heat contribution");
        helper.succeed();
    }

    private static ZirnoxBlockEntity place(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 2, 4);
        helper.setBlock(pos, ModBlocks.REACTOR_ZIRNOX.get().defaultBlockState());
        return helper.getBlockEntity(pos);
    }

    private static void tick(GameTestHelper helper, ZirnoxBlockEntity reactor) {
        ZirnoxBlockEntity.tick(helper.getLevel(), reactor.getBlockPos(), reactor.getBlockState(), reactor);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
