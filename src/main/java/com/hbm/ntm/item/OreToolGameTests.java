package com.hbm.ntm.item;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OreToolGameTests {
    private OreToolGameTests() {
    }

    @GameTest(template = "empty")
    public static void sourceToolTiersAndRecipesArePresent(GameTestHelper helper) {
        check(helper, ModToolTiers.STEEL.getUses() == 750 && ModToolTiers.STEEL.getSpeed() == 8.0F
                        && ModToolTiers.STEEL.getEnchantmentValue() == 10,
                "Steel must retain the source 750-use, speed-8, enchantability-10 material");
        check(helper, ModToolTiers.TITANIUM.getUses() == 1_000 && ModToolTiers.TITANIUM.getSpeed() == 9.0F
                        && ModToolTiers.TITANIUM.getEnchantmentValue() == 15,
                "Titanium must retain the source 1000-use, speed-9, enchantability-15 material");
        check(helper, ModToolTiers.COBALT.getUses() == 750 && ModToolTiers.COBALT.getSpeed() == 9.0F
                        && ModToolTiers.COBALT.getEnchantmentValue() == 60,
                "Cobalt must retain the source 750-use, speed-9, enchantability-60 material");

        for (String material : new String[]{"steel", "titanium", "cobalt"}) {
            for (String tool : new String[]{"sword", "pickaxe", "axe", "shovel", "hoe"}) {
                String id = material + "_" + tool;
                check(helper, helper.getLevel().getRecipeManager().byKey(
                                ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id)).isPresent(),
                        "Source tool recipe hbm:" + id + " must load");
            }
        }
        for (String id : new String[]{"survey_scanner", "ore_density_scanner",
                "circuit_controller_chassis", "crt_display"}) {
            check(helper, helper.getLevel().getRecipeManager().byKey(
                            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id)).isPresent(),
                    "Ore-tool dependency recipe hbm:" + id + " must load");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bismuthAloneBreaksDepthRock(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 1, 2);
        helper.setBlock(relative, ModBlocks.legacy("stone_depth").get());
        BlockPos absolute = helper.absolutePos(relative);
        var state = helper.getLevel().getBlockState(absolute);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        check(helper, state.getDestroyProgress(player, helper.getLevel(), absolute) == 0.0F,
                "Depth rock must remain unbreakable without an IDepthRockTool equivalent");

        ItemStack bismuth = new ItemStack(ModItems.BISMUTH_PICKAXE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, bismuth);
        check(helper, bismuth.getItem() instanceof DepthRockTool && !bismuth.isDamageableItem(),
                "Bismuth Pickaxe must be an unbreakable depth-rock tool");
        check(helper, state.getDestroyProgress(player, helper.getLevel(), absolute) == 1.0F / 50.0F,
                "Bismuth Pickaxe must preserve the source fixed 1/50 depth-rock progress");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void densityFieldIsDeterministicAndTiered(GameTestHelper helper) {
        OreDensityScannerItem.Reading first = OreDensityScannerItem.reading(12_345, -54_321);
        OreDensityScannerItem.Reading second = OreDensityScannerItem.reading(12_345, -54_321);
        check(helper, first.tier() >= 1 && first.tier() <= 4,
                "Bedrock density average must select a source tier from one through four");
        check(helper, first.densities().length == OreDensityScannerItem.OreType.values().length,
                "All six source Bedrock Ore classes must be sampled");
        for (int index = 0; index < first.densities().length; index++) {
            check(helper, first.densities()[index] >= 0.0D && first.densities()[index] <= 2.0D,
                    "Bedrock density must stay within the source zero-to-two clamp");
            check(helper, first.densities()[index] == second.densities()[index],
                    "Bedrock density must be deterministic at a fixed X/Z coordinate");
        }
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
