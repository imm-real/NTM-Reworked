package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.WoodBurnerBlock;
import com.hbm.ntm.blockentity.WoodBurnerBlockEntity;
import com.hbm.ntm.blockentity.WoodBurnerProxyBlockEntity;
import com.hbm.ntm.item.AshItem;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WoodBurnerGameTests {
    private WoodBurnerGameTests() { }

    @GameTest(template = "empty")
    public static void exactEightCellFootprintAndRearPorts(GameTestHelper helper) {
        BlockPos core = new BlockPos(4, 1, 4);
        List<BlockPos> parts = WoodBurnerBlock.partPositions(core, Direction.SOUTH);
        check(helper, parts.size() == 8 && new HashSet<>(parts).size() == 8,
                "Wood Burner must retain its exact 2x2x2 source dummy volume");
        List<WoodBurnerBlock.Connection> connections = WoodBurnerBlock.connections(core, Direction.SOUTH);
        check(helper, connections.size() == 2,
                "Only the two lower rear source extras may be HE/fluid ports");
        check(helper, connections.get(0).port().equals(core.north())
                        && connections.get(0).target().equals(core.north(2))
                        && connections.get(0).outward() == Direction.NORTH
                        && connections.get(1).port().equals(core.north().west()),
                "SOUTH-facing rear ports must match the source -dir and -dir+rot positions");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void solidFuelLoadAndWoodBonusesMatchSource(GameTestHelper helper) {
        check(helper, WoodBurnerBlockEntity.burnTime(new ItemStack(Items.OAK_LOG)) == 1_200,
                "A 300-tick Log must receive the exact 4x source burn-time bonus");
        check(helper, WoodBurnerBlockEntity.burnTime(new ItemStack(Items.OAK_PLANKS)) == 600
                        && WoodBurnerBlockEntity.burnTime(new ItemStack(Items.STICK)) == 200,
                "Planks and OreDict-equivalent wooden sticks must receive the exact 2x bonus");

        WoodBurnerBlockEntity burner = bareBurner(helper, new BlockPos(3, 1, 3), Direction.SOUTH);
        burner.setItem(WoodBurnerBlockEntity.SOLID_FUEL, new ItemStack(Items.OAK_LOG));
        burner.setOnForTest(true);
        tick(helper, burner);
        check(helper, burner.burnTime() == 1_200 && burner.maxBurnTime() == 1_200
                        && burner.getPower() == 0 && burner.powerGeneration() == 0,
                "The first source tick must load a full fuel item without burning or generating");
        tick(helper, burner);
        check(helper, burner.burnTime() == 1_199 && burner.getPower() == 100
                        && burner.powerGeneration() == 100,
                "The following enabled tick must consume one burn tick and generate exactly 100 HE");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ashWhileLoopAndContainerRemainderMatchSource(GameTestHelper helper) {
        WoodBurnerBlockEntity burner = bareBurner(helper, new BlockPos(3, 1, 3), Direction.SOUTH);
        burner.setItem(WoodBurnerBlockEntity.SOLID_FUEL, new ItemStack(Items.LAVA_BUCKET));
        tick(helper, burner);
        ItemStack ash = burner.getItem(WoodBurnerBlockEntity.ASH_OUTPUT);
        check(helper, burner.burnTime() == 20_000 && burner.maxBurnTime() == 20_000
                        && burner.getItem(WoodBurnerBlockEntity.SOLID_FUEL).is(Items.BUCKET),
                "A Lava Bucket must load 20,000 raw ticks and leave its Bucket");
        check(helper, ash.is(ModItems.POWDER_ASH.get()) && AshItem.type(ash) == AshItem.AshType.MISC
                        && ash.getCount() == 10 && burner.ashLevel(AshItem.AshType.MISC) == 0,
                "Unlike the Bricked Furnace, the Wood Burner must drain all ten available ash thresholds");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void woodOilModeUsesExactHalfEfficiency(GameTestHelper helper) {
        WoodBurnerBlockEntity burner = bareBurner(helper, new BlockPos(3, 1, 3), Direction.SOUTH);
        burner.setSelectedForTest(FluidIdentifierItem.Selection.WOODOIL);
        burner.setLiquidForTest(true);
        burner.setOnForTest(true);
        check(helper, burner.addFluidForTest(2) == 2
                        && FluidIdentifierItem.Selection.WOODOIL.fluid().isSame(ModFluids.WOODOIL.get()),
                "Wood Oil must be a registered selectable source liquid");
        tick(helper, burner);
        check(helper, burner.tankAmount() == 0 && burner.powerGeneration() == 110
                        && burner.getPower() == 110,
                "Two mB of 110-TU/mB Wood Oil at 50% efficiency must generate exactly 110 HE");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void proxyCapabilitiesAndCraftingIdentityMatchSource(GameTestHelper helper) {
        BlockPos core = new BlockPos(4, 1, 4);
        BlockState coreState = coreState(Direction.SOUTH);
        helper.setBlock(core, coreState);
        BlockPos port = core.north();
        BlockState portState = coreState.setValue(WoodBurnerBlock.PART_REAR, 1);
        helper.setBlock(port, portState);
        WoodBurnerProxyBlockEntity proxy = helper.getBlockEntity(port);
        check(helper, proxy.fluidHandler(Direction.NORTH) != null
                        && proxy.fluidHandler(Direction.SOUTH) == null
                        && proxy.canConnect(Direction.NORTH) && !proxy.canConnect(Direction.SOUTH),
                "The lower rear ProxyCombo must expose HE/fluid only on its outward face");
        IFluidHandler fluid = proxy.fluidHandler(Direction.NORTH);
        check(helper, fluid != null && fluid.fill(new FluidStack(ModFluids.WOODOIL.get(), 100),
                        IFluidHandler.FluidAction.EXECUTE) == 100,
                "The default source tank must accept Wood Oil through a rear proxy");
        check(helper, helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                        helper.absolutePos(port), Direction.UP) != null,
                "Every source dummy ProxyCombo must delegate the sided inventory");

        CraftingInput input = CraftingInput.of(3, 3, List.of(
                new ItemStack(ModItems.get("plate_steel").get()),
                new ItemStack(ModItems.get("plate_steel").get()),
                new ItemStack(ModItems.get("plate_steel").get()),
                new ItemStack(ModItems.COIL_COPPER.get()), new ItemStack(Items.FURNACE),
                new ItemStack(ModItems.COIL_COPPER.get()), new ItemStack(Items.IRON_INGOT),
                ItemStack.EMPTY, new ItemStack(Items.IRON_INGOT)));
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                input, helper.getLevel()).orElseThrow();
        check(helper, recipe.value().assemble(input, helper.getLevel().registryAccess())
                        .is(ModItems.MACHINE_WOOD_BURNER_ITEM.get()),
                "PPP/CFC/I I must retain Steel Plates, Copper Coils, Furnace and Iron Ingots");
        helper.succeed();
    }

    private static WoodBurnerBlockEntity bareBurner(GameTestHelper helper, BlockPos position, Direction facing) {
        helper.setBlock(position, coreState(facing));
        return helper.getBlockEntity(position);
    }

    private static BlockState coreState(Direction facing) {
        return ModBlocks.MACHINE_WOOD_BURNER.get().defaultBlockState()
                .setValue(WoodBurnerBlock.FACING, facing)
                .setValue(WoodBurnerBlock.PART_SIDE, 0)
                .setValue(WoodBurnerBlock.PART_REAR, 0)
                .setValue(WoodBurnerBlock.PART_Y, 0);
    }

    private static void tick(GameTestHelper helper, WoodBurnerBlockEntity burner) {
        WoodBurnerBlockEntity.tick(helper.getLevel(), burner.getBlockPos(), burner.getBlockState(), burner);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
