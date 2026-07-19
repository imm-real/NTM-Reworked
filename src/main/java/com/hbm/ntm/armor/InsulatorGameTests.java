package com.hbm.ntm.armor;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InsulatorGameTests {
    private InsulatorGameTests() { }

    @GameTest(template = "empty")
    public static void boundedInsulatorAndCladdingRecipesArePresent(GameTestHelper helper) {
        Map<String, Integer> expected = Map.ofEntries(
                Map.entry("plate_polymer_from_wool", 4),
                Map.entry("plate_polymer_from_brick", 4),
                Map.entry("plate_polymer_from_nether_brick", 4),
                Map.entry("plate_polymer_from_asbestos", 16),
                Map.entry("plate_polymer_from_rubber", 8),
                Map.entry("ball_resin", 1),
                Map.entry("ingot_biorubber", 1),
                Map.entry("cladding_iron", 1),
                Map.entry("cladding_obsidian", 1),
                Map.entry("machine_armor_table", 1),
                Map.entry("block_insulator", 1),
                Map.entry("plate_polymer_from_block", 9)
        );
        for (var entry : expected.entrySet()) {
            var recipe = helper.getLevel().getRecipeManager().byKey(
                    ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, entry.getKey()));
            if (recipe.isEmpty()) helper.fail("Missing recipe hbm:" + entry.getKey());
            var result = recipe.orElseThrow().value().getResultItem(helper.getLevel().registryAccess());
            if (result.getCount() != entry.getValue())
                helper.fail("Recipe hbm:" + entry.getKey() + " must output " + entry.getValue());
        }
        if (!helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "plate_polymer_from_plastic")).isEmpty()
                || !helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "plate_polymer_from_fiberglass")).isEmpty()) {
            helper.fail("Plastic and fiberglass Insulator recipes must wait for their ingredients");
        }
        ItemStack latexBar = new ItemStack(ModItems.get("ingot_biorubber").get());
        if (!latexBar.is(TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", "ingots/latex")))
                || !latexBar.is(TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "ingots/any_rubber")))) {
            helper.fail("Latex Bar must retain the source Latex and AnyRubber ingot identities");
        }
        ItemStack latex = new ItemStack(ModItems.get("ball_resin").get());
        if (!latex.is(TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", "gems/latex")))) {
            helper.fail("Latex must retain the source Latex gem identity");
        }
        if (ModItems.PLATE_POLYMER.get().getDefaultMaxStackSize() != 64)
            helper.fail("Insulator must preserve the ordinary source stack limit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void insulationRollPreservesPillarAndBlockProperties(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        var block = ModBlocks.BLOCK_INSULATOR.get();
        var state = block.defaultBlockState();
        if (state.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y)
            helper.fail("Roll of Insulation must default to a vertical pillar");
        helper.setBlock(pos, state.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        if (helper.getBlockState(pos).getValue(RotatedPillarBlock.AXIS) != Direction.Axis.X)
            helper.fail("Roll of Insulation must preserve horizontal placement axes");
        if (helper.getBlockState(pos).getDestroySpeed(helper.getLevel(), helper.absolutePos(pos)) != 5.0F)
            helper.fail("Roll of Insulation must preserve source hardness 5");
        if (block.getExplosionResistance() != 6.0F)
            helper.fail("Roll of Insulation must preserve effective source resistance 6");
        helper.succeed();
    }
}
