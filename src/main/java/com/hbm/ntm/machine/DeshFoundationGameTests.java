package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.item.OreChunkItem;
import com.hbm.ntm.recipe.ShredderRecipes;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.worldgen.ConfigOreCountPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeshFoundationGameTests {
    private DeshFoundationGameTests() {
    }

    @GameTest(template = "empty")
    public static void rareEarthOreStartsExactNormalDeshDependencyChain(GameTestHelper helper) {
        check(helper, HbmConfig.RARE_EARTH_SPAWN_RATE.getDefault() == 6
                        && new ConfigOreCountPlacement(ConfigOreCountPlacement.OreType.RARE_EARTH)
                        .configuredCount() == HbmConfig.RARE_EARTH_SPAWN_RATE.get(),
                "Rare Earth Ore must preserve the source default of six veins per chunk");

        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "ore_rare");
        var configuredFeatures = helper.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        var placedFeatures = helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        ConfiguredFeature<?, ?> configured = configuredFeatures.get(id);
        check(helper, configured != null && configured.config() instanceof OreConfiguration ore && ore.size == 5
                        && placedFeatures.containsKey(id),
                "Rare Earth Ore must load as the source five-block vein and a placed overworld feature");

        BlockPos relative = new BlockPos(2, 1, 2);
        helper.setBlock(relative, ModBlocks.ORE_RARE.get());
        BlockPos absolute = helper.absolutePos(relative);
        var state = helper.getLevel().getBlockState(absolute);
        var drops = Block.getDrops(state, helper.getLevel(), absolute, null);
        check(helper, state.getDestroySpeed(helper.getLevel(), absolute) == 5.0F
                        && ModBlocks.ORE_RARE.get().getExplosionResistance() == 6.0F
                        && drops.size() == 1 && drops.getFirst().is(ModItems.CHUNK_ORE.get())
                        && drops.getFirst().getCount() == 1
                        && OreChunkItem.type(drops.getFirst()) == OreChunkItem.ChunkType.RARE,
                "Rare Earth Ore must preserve hardness 5, effective resistance 6, and one Rare Earth Ore Chunk drop");

        ItemStack silkPick = new ItemStack(Items.DIAMOND_PICKAXE);
        silkPick.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH), 1);
        var silkDrops = Block.getDrops(state, helper.getLevel(), absolute, null, null, silkPick);
        check(helper, silkDrops.size() == 1 && silkDrops.getFirst().is(ModItems.ORE_RARE_ITEM.get()),
                "Silk Touch must preserve Rare Earth Ore instead of dropping its chunk");

        ItemStack carrier = OreChunkItem.rare(ModItems.CHUNK_ORE.get(), 1);
        CustomModelData model = carrier.get(DataComponents.CUSTOM_MODEL_DATA);
        ItemStack shreddedOre = ShredderRecipes.getResult(new ItemStack(ModItems.ORE_RARE_ITEM.get()));
        ItemStack shreddedChunk = ShredderRecipes.getResult(carrier);
        check(helper, model != null && model.value() == 0
                        && "item.hbm.chunk_ore.rare".equals(carrier.getDescriptionId())
                        && shreddedOre.is(ModItems.get("powder_desh_mix").get())
                        && shreddedOre.getCount() == 1
                        && shreddedChunk.is(ModItems.get("powder_desh_mix").get())
                        && shreddedChunk.getCount() == 1,
                "Rare Earth metadata zero and both exact Shredder routes must yield one Desh Blend");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}
