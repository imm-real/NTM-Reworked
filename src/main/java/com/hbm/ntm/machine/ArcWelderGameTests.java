package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.anvil.AnvilRecipes;
import com.hbm.ntm.block.ArcWelderBlock;
import com.hbm.ntm.blockentity.ArcWelderBlockEntity;
import com.hbm.ntm.blockentity.ArcWelderProxyBlockEntity;
import com.hbm.ntm.energy.HeProvider;
import com.hbm.ntm.foundry.FoundryMaterial;
import com.hbm.ntm.item.ArcElectrodeItem;
import com.hbm.ntm.item.BoltItem;
import com.hbm.ntm.item.CastPlateItem;
import com.hbm.ntm.item.CircuitItem;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.WeldedPlateItem;
import com.hbm.ntm.recipe.ArcWelderRecipes;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArcWelderGameTests {
    private ArcWelderGameTests() { }

    @GameTest(template = "empty")
    public static void welderUsesTwelveBlockFootprintAndSourceAutomationLanes(GameTestHelper helper) {
        ArcWelderBlockEntity welder = placeWelder(helper, new BlockPos(4, 1, 4));
        BlockPos core = welder.getBlockPos();
        Direction facing = welder.getBlockState().getValue(ArcWelderBlock.FACING);
        Direction rot = facing.getClockWise();
        var parts = ArcWelderBlock.partPositions(core, facing);
        int proxies = 0;
        int connectionFaces = 0;
        for (BlockPos part : parts) {
            var state = helper.getLevel().getBlockState(part);
            check(helper, state.is(ModBlocks.MACHINE_ARC_WELDER.get()),
                    "Every Arc Welder footprint cell must use the shared machine block");
            if (helper.getLevel().getBlockEntity(part) instanceof ArcWelderProxyBlockEntity) proxies++;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (ArcWelderBlock.canConnectAt(state, side)) connectionFaces++;
            }
        }
        check(helper, parts.size() == 12 && proxies == 11,
                "Arc Welder must remain a 3x2x2 machine with eleven capability proxies");
        check(helper, connectionFaces == 10,
                "Arc Welder lower perimeter must preserve all ten source energy/fluid connection faces");
        check(helper, welder.getBlockState().getDestroySpeed(helper.getLevel(), core) == 5.0F
                        && ModBlocks.MACHINE_ARC_WELDER.get().getExplosionResistance() == 18.0F
                        && ModBlocks.MACHINE_TRANSFORMER.get().defaultDestroyTime() == 5.0F
                        && ModBlocks.MACHINE_TRANSFORMER.get().getExplosionResistance() == 6.0F,
                "Welder and Transformer must preserve converted source hardness/resistance values");

        assertSlots(helper, welder, new int[]{1, 3}, "The core must use generic source automation slots");
        assertSlots(helper, proxy(helper, core.relative(rot)), new int[]{0, 3},
                "Front red lane must expose input 0 and output 3");
        assertSlots(helper, proxy(helper, core.relative(rot.getOpposite()).relative(facing.getOpposite())),
                new int[]{0, 3}, "Rear red lane must expose input 0 and output 3");
        assertSlots(helper, proxy(helper, core.relative(facing.getOpposite())), new int[]{1, 3},
                "Rear yellow lane must expose input 1 and output 3");
        assertSlots(helper, proxy(helper, core.relative(rot.getOpposite())), new int[]{2, 3},
                "Front green lane must expose input 2 and output 3");
        assertSlots(helper, proxy(helper, core.relative(rot).relative(facing.getOpposite())), new int[]{2, 3},
                "Rear green lane must expose input 2 and output 3");
        assertSlots(helper, proxy(helper, core.above()), new int[]{},
                "Upper Arc Welder proxies must not expose automation slots");

        check(helper, helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, core, Direction.NORTH) != null
                        && helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                        core.relative(rot), Direction.NORTH) != null,
                "Core and proxies must expose the source machine capabilities");
        ArcWelderProxyBlockEntity top = proxy(helper, core.above());
        TestProvider provider = new TestProvider(1_000L);
        provider.tryProvide(helper.getLevel(), top.getBlockPos(), Direction.DOWN);
        check(helper, welder.getPower() == 1_000L && provider.getPower() == 0L && top.canConnect(Direction.UP),
                "Every source proxy must delegate direct HE reception to the Arc Welder core");

        welder.setItem(0, new ItemStack(Items.DIRT, 3));
        helper.getLevel().destroyBlock(core.above(), true);
        for (BlockPos part : parts) check(helper, helper.getLevel().getBlockState(part).isAir(),
                "Breaking any Arc Welder cell must dismantle the whole twelve-block machine");
        int machineDrops = 0;
        int inventoryDrops = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(core).inflate(5D))) {
            if (entity.getItem().is(ModItems.MACHINE_ARC_WELDER_ITEM.get())) {
                machineDrops += entity.getItem().getCount();
            }
            if (entity.getItem().is(Items.DIRT)) inventoryDrops += entity.getItem().getCount();
        }
        check(helper, machineDrops == 1 && inventoryDrops == 3,
                "Dismantling from a proxy must drop one machine and its inventory exactly once");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void steelRecipeRequiresOneUnsplitPairAndPreservesMaterialIdentity(GameTestHelper helper) {
        ItemStack steelPair = castSteel(2);
        steelPair.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("matched steel lot"));
        ArcWelderRecipes.ArcWelderRecipe recipe = ArcWelderRecipes.find(
                ItemStack.EMPTY, steelPair, ItemStack.EMPTY);
        check(helper, recipe != null && recipe.duration() == 100 && recipe.consumption() == 500L
                        && recipe.fluid() == null && recipe.ingredients().size() == 1
                        && recipe.ingredients().getFirst().count() == 2
                        && WeldedPlateItem.isSteel(recipe.output()),
                "Two Cast Steel Plates must weld into Steel for 100 ticks at 500 HE/t without fluid");
        check(helper, ArcWelderRecipes.find(castSteel(1), castSteel(1), ItemStack.EMPTY) == null,
                "The source matcher must reject a required pair split over two Arc Welder slots");
        check(helper, ArcWelderRecipes.find(steelPair, new ItemStack(Items.DIRT), ItemStack.EMPTY) == null,
                "An unrelated extra input must invalidate the Arc Welder recipe");
        check(helper, ArcWelderRecipes.find(castSteel(2), ItemStack.EMPTY, ItemStack.EMPTY) != null
                        && ArcWelderRecipes.find(ItemStack.EMPTY, ItemStack.EMPTY, castSteel(2)) != null,
                "The Cast Steel pair must match in any of the three unordered input slots");
        ItemStack namedOutput = WeldedPlateItem.steel(ModItems.PLATE_WELDED.get(), 1);
        namedOutput.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("kept weld lot"));
        check(helper, recipe.matchesOutput(namedOutput),
                "Unrelated output NBT must not invalidate the source item/metadata merge check");

        FoundryMaterial.MaterialAmount remelted = FoundryMaterial.fromItem(recipe.output());
        TagKey<Item> weldedSteel = TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", "plates/welded/steel"));
        check(helper, recipe.output().is(ModItems.PLATE_WELDED.get())
                        && WeldedPlateItem.isSteel(recipe.output())
                        && "item.hbm.plate_welded.steel".equals(recipe.output().getDescriptionId())
                        && remelted != null && remelted.material() == FoundryMaterial.STEEL
                        && remelted.amount() == FoundryMaterial.WELDED_PLATE
                        && remelted.amount() == FoundryMaterial.INGOT * 6
                        && !recipe.output().is(weldedSteel),
                "Welded Steel must preserve metadata 30, remelting, and avoid globally tagging its shared carrier");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void steelWeldCompletesAtTickOneHundredForFiftyThousandHe(GameTestHelper helper) {
        ArcWelderBlockEntity welder = bareWelder(helper, new BlockPos(3, 1, 3));
        welder.setItem(0, castSteel(2));
        long supplied = 0L;
        for (int tick = 0; tick < 99; tick++) {
            if (welder.getPower() < 500L) {
                long charge = Math.min(welder.getMaxPower() - welder.getPower(), 50_000L - supplied);
                check(helper, welder.transferPower(charge) == 0L,
                        "Legal HE refills must fit the current Arc Welder buffer");
                supplied += charge;
            }
            tick(helper, welder);
        }
        check(helper, welder.getItem(ArcWelderBlockEntity.OUTPUT).isEmpty()
                        && welder.getItem(0).getCount() == 2 && welder.progress() == 99
                        && welder.getPower() == 500L && supplied == 50_000L,
                "Arc Welder inputs must remain intact through tick 99 after legally receiving 50,000 HE");
        tick(helper, welder);
        check(helper, WeldedPlateItem.isSteel(welder.getItem(ArcWelderBlockEntity.OUTPUT))
                        && welder.getItem(ArcWelderBlockEntity.OUTPUT).getCount() == 1
                        && welder.getItem(0).isEmpty() && welder.progress() == 0
                        && welder.getPower() == 0L && welder.getMaxPower() == 10_000L,
                "Tick 100 must atomically consume two Cast Steel Plates and output one Welded Steel Plate for 50,000 HE");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void powerOutputAndUpgradesPreserveSourceProcessingRules(GameTestHelper helper) {
        ArcWelderBlockEntity blocked = bareWelder(helper, new BlockPos(2, 1, 2));
        blocked.setItem(0, castSteel(2));
        blocked.setPower(1_000L);
        tick(helper, blocked);
        check(helper, blocked.progress() == 1 && blocked.getPower() == 500L,
                "The output-block reset test must begin from an active weld");
        blocked.setItem(ArcWelderBlockEntity.OUTPUT,
                WeldedPlateItem.steel(ModItems.PLATE_WELDED.get(), 64));
        tick(helper, blocked);
        check(helper, blocked.progress() == 0 && blocked.getPower() == 500L
                        && blocked.getItem(0).getCount() == 2,
                "A newly full output must reset accumulated progress without consuming HE or inputs");

        ArcWelderBlockEntity unpowered = bareWelder(helper, new BlockPos(2, 1, 5));
        unpowered.setItem(0, castSteel(2));
        unpowered.setPower(1_000L);
        tick(helper, unpowered);
        unpowered.setPower(0L);
        tick(helper, unpowered);
        check(helper, unpowered.progress() == 0 && unpowered.getItem(0).getCount() == 2,
                "Losing HE during a weld must reset accumulated progress without consuming inputs");

        ArcWelderBlockEntity overfilled = bareWelder(helper, new BlockPos(2, 1, 7));
        overfilled.setItem(0, castSteel(2));
        overfilled.setPower(50_000L);
        tick(helper, overfilled);
        check(helper, overfilled.getPower() == 1_500L && overfilled.getMaxPower() == 10_000L,
                "Tick start must clamp raw overfill to the prior 2,000-HE source buffer before welding");

        ArcWelderBlockEntity battery = bareWelder(helper, new BlockPos(5, 1, 2));
        battery.setItem(0, castSteel(2));
        battery.setItem(ArcWelderBlockEntity.BATTERY, new ItemStack(ModItems.BATTERY_CREATIVE.get()));
        tick(helper, battery);
        check(helper, battery.progress() == 1 && battery.getPower() == 1_500L,
                "Battery discharge must precede the first 500-HE Arc Welder processing tick");

        ArcWelderBlockEntity upgraded = bareWelder(helper, new BlockPos(7, 1, 2));
        upgraded.setItem(0, castSteel(2));
        upgraded.setItem(ArcWelderBlockEntity.UPGRADE_START,
                new ItemStack(ModItems.MACHINE_UPGRADES.get("upgrade_speed_3").get()));
        tick(helper, upgraded);
        check(helper, upgraded.processTime() == 50 && upgraded.consumption() == 2_000L,
                "Speed III must halve weld duration and quadruple per-tick consumption");
        upgraded.setItem(ArcWelderBlockEntity.UPGRADE_START,
                new ItemStack(ModItems.MACHINE_UPGRADES.get("upgrade_power_3").get()));
        tick(helper, upgraded);
        check(helper, upgraded.processTime() == 200 && upgraded.consumption() == 250L,
                "Power III must double weld duration and halve consumption by the integer source formula");
        upgraded.setItem(ArcWelderBlockEntity.UPGRADE_START,
                new ItemStack(ModItems.MACHINE_UPGRADES.get("upgrade_overdrive_3").get()));
        upgraded.setPower(100_000L);
        tick(helper, upgraded);
        check(helper, upgraded.processTime() == 100 && upgraded.consumption() == 4_000L
                        && upgraded.progress() == 4,
                "Overdrive III must octuple consumption and add four progress per tick");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void identifierGatesTwentyFourBucketTankAndStatePersists(GameTestHelper helper) {
        ArcWelderBlockEntity welder = bareWelder(helper, new BlockPos(2, 1, 2));
        IFluidHandler handler = welder.fluidHandler();
        check(helper, welder.tank().getCapacity() == 24_000
                        && handler.fill(new FluidStack(Fluids.WATER, 1_000),
                        IFluidHandler.FluidAction.EXECUTE) == 0,
                "Arc Welder tank must hold 24 buckets and reject fluid without an identifier");
        ItemStack identifier = identifier(FluidIdentifierItem.Selection.WATER);
        welder.setItem(ArcWelderBlockEntity.FLUID_IDENTIFIER, identifier);
        tick(helper, welder);
        check(helper, handler.fill(new FluidStack(Fluids.LAVA, 1_000),
                        IFluidHandler.FluidAction.EXECUTE) == 0
                        && handler.fill(new FluidStack(Fluids.WATER, 24_000),
                        IFluidHandler.FluidAction.EXECUTE) == 24_000,
                "Only the Arc Welder identifier's primary fluid may fill its tank");

        var saved = welder.saveWithoutMetadata(helper.getLevel().registryAccess());
        ArcWelderBlockEntity loaded = bareWelder(helper, new BlockPos(5, 1, 2));
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(helper, loaded.tank().getFluidAmount() == 24_000 && loaded.tank().getFluid().is(Fluids.WATER)
                        && FluidIdentifierItem.primary(loaded.getItem(ArcWelderBlockEntity.FLUID_IDENTIFIER))
                        == FluidIdentifierItem.Selection.WATER,
                "Arc Welder identifier and full tank must persist together");
        loaded.setItem(ArcWelderBlockEntity.FLUID_IDENTIFIER, ItemStack.EMPTY);
        tick(helper, loaded);
        loaded.tank().drain(1_000, IFluidHandler.FluidAction.EXECUTE);
        check(helper, loaded.tank().getFluidAmount() == 23_000
                        && loaded.fluidHandler().fill(new FluidStack(Fluids.WATER, 1_000),
                        IFluidHandler.FluidAction.EXECUTE) == 1_000,
                "Removing the identifier must preserve the configured source tank type and contents");

        var withoutIdentifier = loaded.saveWithoutMetadata(helper.getLevel().registryAccess());
        ArcWelderBlockEntity remembered = bareWelder(helper, new BlockPos(8, 1, 2));
        remembered.loadWithComponents(withoutIdentifier, helper.getLevel().registryAccess());
        remembered.tank().drain(1_000, IFluidHandler.FluidAction.EXECUTE);
        check(helper, remembered.getItem(ArcWelderBlockEntity.FLUID_IDENTIFIER).isEmpty()
                        && remembered.fluidHandler().fill(new FluidStack(Fluids.WATER, 1_000),
                        IFluidHandler.FluidAction.EXECUTE) == 1_000,
                "The remembered tank type must survive a save with no identifier installed");
        remembered.setItem(ArcWelderBlockEntity.FLUID_IDENTIFIER,
                identifier(FluidIdentifierItem.Selection.LAVA));
        var changedBeforeTick = remembered.saveWithoutMetadata(helper.getLevel().registryAccess());
        remembered.loadWithComponents(changedBeforeTick, helper.getLevel().registryAccess());
        tick(helper, remembered);
        check(helper, remembered.tank().isEmpty(),
                "Retyping the installed Arc Welder identifier must clear incompatible fluid");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void transformerElectrodeAndAnvilConstructionRemainExact(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var transformer = manager.byKey(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID,
                "machine_transformer")).orElseThrow().value();
        ItemStack namedCapacitor = CircuitItem.create(ModItems.CIRCUIT.get(),
                CircuitItem.CircuitType.CAPACITOR, 1);
        namedCapacitor.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("tested capacitor"));
        check(helper, transformer.getResultItem(helper.getLevel().registryAccess())
                        .is(ModItems.MACHINE_TRANSFORMER_ITEM.get())
                        && transformer.getIngredients().size() == 9
                        && transformer.getIngredients().get(1).test(CircuitItem.create(ModItems.CIRCUIT.get(),
                        CircuitItem.CircuitType.CAPACITOR, 1))
                        && transformer.getIngredients().get(1).test(namedCapacitor)
                        && !transformer.getIngredients().get(1).test(CircuitItem.create(ModItems.CIRCUIT.get(),
                        CircuitItem.CircuitType.VACUUM_TUBE, 1))
                        && transformer.getIngredients().get(3).test(new ItemStack(ModItems.COIL_COPPER.get()))
                        && transformer.getIngredients().get(4).test(new ItemStack(ModItems.get("ingot_red_copper").get())),
                "Transformer recipe must remain SCS/MDM/SCS with Capacitors, Copper Coils, Iron, and Mingrade Copper");

        var electrodeRecipe = manager.byKey(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID,
                "arc_electrode_graphite")).orElseThrow().value();
        ItemStack electrodeResult = electrodeRecipe.getResultItem(helper.getLevel().registryAccess());
        check(helper, electrodeRecipe.getIngredients().size() == 3
                        && electrodeRecipe.getIngredients().get(0).test(
                        new ItemStack(ModItems.get("ingot_graphite").get()))
                        && electrodeRecipe.getIngredients().get(1).test(BoltItem.create(ModItems.BOLT.get(),
                        BoltItem.BoltMaterial.STEEL, 1))
                        && !electrodeRecipe.getIngredients().get(1).test(BoltItem.create(ModItems.BOLT.get(),
                        BoltItem.BoltMaterial.TUNGSTEN, 1))
                        && electrodeResult.is(ModItems.ARC_ELECTRODE.get())
                        && ArcElectrodeItem.type(electrodeResult) == ArcElectrodeItem.ElectrodeType.GRAPHITE
                        && ArcElectrodeItem.durability(electrodeResult) == 0,
                "Graphite Electrode must remain the vertical Graphite/Steel Bolt/Graphite recipe");

        AnvilRecipes.Construction construction = AnvilRecipes.byId(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "anvil/machine_arc_welder"));
        ItemStack fresh = ArcElectrodeItem.create(ModItems.ARC_ELECTRODE.get(),
                ArcElectrodeItem.ElectrodeType.GRAPHITE, 1);
        ItemStack worn = fresh.copy();
        ArcElectrodeItem.damage(worn);
        ItemStack namedCastSteel = castSteel(1);
        namedCastSteel.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("anvil lot"));
        check(helper, construction != null && construction.tierLower() == 2
                        && construction.inputs().size() == 4
                        && construction.inputs().get(0).count() == 4
                        && construction.inputs().get(0).matches(castSteel(1))
                        && construction.inputs().get(0).matches(namedCastSteel)
                        && construction.inputs().get(1).count() == 8
                        && construction.inputs().get(1).matches(new ItemStack(ModItems.get("ingot_tungsten").get()))
                        && construction.inputs().get(2).count() == 1
                        && construction.inputs().get(2).matches(new ItemStack(ModItems.MACHINE_TRANSFORMER_ITEM.get()))
                        && construction.inputs().get(3).count() == 2
                        && construction.inputs().get(3).matches(fresh)
                        && construction.inputs().get(3).matches(worn)
                        && construction.outputs().getFirst().stack().get().is(ModItems.MACHINE_ARC_WELDER_ITEM.get()),
                "Tier-2 construction must require 4 Cast Steel, 8 Tungsten, 1 Transformer, and 2 metadata-zero Graphite Electrodes while ignoring their durability NBT");
        helper.succeed();
    }

    private static ArcWelderBlockEntity placeWelder(GameTestHelper helper, BlockPos relativeCore) {
        ArcWelderBlock block = ModBlocks.MACHINE_ARC_WELDER.get();
        var state = block.defaultBlockState().setValue(ArcWelderBlock.FACING, Direction.NORTH);
        helper.setBlock(relativeCore, state);
        BlockPos absolute = helper.absolutePos(relativeCore);
        block.setPlacedBy(helper.getLevel(), absolute, state, helper.makeMockPlayer(GameType.SURVIVAL),
                new ItemStack(ModItems.MACHINE_ARC_WELDER_ITEM.get()));
        return (ArcWelderBlockEntity) helper.getLevel().getBlockEntity(absolute);
    }

    private static ArcWelderBlockEntity bareWelder(GameTestHelper helper, BlockPos relativePos) {
        helper.setBlock(relativePos, ModBlocks.MACHINE_ARC_WELDER.get().defaultBlockState());
        return helper.getBlockEntity(relativePos);
    }

    private static ArcWelderProxyBlockEntity proxy(GameTestHelper helper, BlockPos absolutePos) {
        return (ArcWelderProxyBlockEntity) helper.getLevel().getBlockEntity(absolutePos);
    }

    private static ItemStack castSteel(int count) {
        return CastPlateItem.create(ModItems.PLATE_CAST.get(), CastPlateItem.CastPlateMaterial.STEEL, count);
    }

    private static ItemStack identifier(FluidIdentifierItem.Selection selection) {
        ItemStack stack = new ItemStack(ModItems.FLUID_IDENTIFIER_MULTI.get());
        FluidIdentifierItem.set(stack, selection, true);
        return stack;
    }

    private static void tick(GameTestHelper helper, ArcWelderBlockEntity welder) {
        ArcWelderBlockEntity.tick(helper.getLevel(), welder.getBlockPos(), welder.getBlockState(), welder);
    }

    private static void assertSlots(GameTestHelper helper, WorldlyContainer container, int[] expected,
                                    String message) {
        check(helper, Arrays.equals(container.getSlotsForFace(Direction.NORTH), expected), message);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    private static final class TestProvider implements HeProvider {
        private long power;

        private TestProvider(long power) { this.power = power; }
        @Override public long getPower() { return power; }
        @Override public void setPower(long power) { this.power = power; }
        @Override public long getMaxPower() { return 100_000L; }
        @Override public boolean isHeLoaded() { return true; }
    }
}
