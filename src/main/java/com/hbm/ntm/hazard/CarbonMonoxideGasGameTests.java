package com.hbm.ntm.hazard;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.CarbonMonoxideGasBlock;
import com.hbm.ntm.block.NetherCoalOreBlock;
import com.hbm.ntm.item.HazmatArmorItem;
import com.hbm.ntm.recipe.ShredderRecipes;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.thermal.FireboxFuel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CarbonMonoxideGasGameTests {
    private CarbonMonoxideGasGameTests() { }

    @GameTest(template = "empty")
    public static void harvestedNetherCoalDropsInfernalCoalAndLeavesMonoxide(GameTestHelper helper) {
        NetherCoalOreBlock ore = (NetherCoalOreBlock) ModBlocks.legacy("ore_nether_coal").get();
        var state = ore.defaultBlockState();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var drops = ore.getDrops(state, new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY));
        check(helper, drops.size() == 1 && drops.getFirst().is(ModItems.legacyOreResourceItem("coal_infernal").get())
                        && drops.getFirst().getCount() == 1,
                "Nether Coal Ore must drop exactly one source Infernal Coal without Fortune");

        ore.playerDestroy(helper.getLevel(), helper.makeMockPlayer(GameType.SURVIVAL), pos,
                state, null, ItemStack.EMPTY);
        check(helper, helper.getLevel().getBlockState(pos).is(ModBlocks.legacy("gas_monoxide").get()),
                "Harvested Nether Coal Ore must leave Carbon Monoxide in its former position");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void monoxideDealsExactAbsoluteDamage(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 4));
        float initialHealth = player.getHealth();
        CarbonMonoxideGasBlock gas = (CarbonMonoxideGasBlock) ModBlocks.legacy("gas_monoxide").get();
        gas.defaultBlockState().entityInside(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), player);
        check(helper, player.getHealth() == initialHealth - 1.0F,
                "One unprotected Carbon Monoxide collision must deal exactly one absolute damage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void monoxideFilterBlocksDamageAndTakesExactWear(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack helmet = new ItemStack(ModItems.HAZMAT_HELMET.get());
        HazmatArmorItem.installFilter(helmet, new ItemStack(ModItems.GAS_MASK_FILTER_MONO.get()));
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        float initialHealth = player.getHealth();

        CarbonMonoxideGasBlock gas = (CarbonMonoxideGasBlock) ModBlocks.legacy("gas_monoxide").get();
        gas.defaultBlockState().entityInside(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), player);
        check(helper, player.getHealth() == initialHealth,
                "Monoxide protection must block the collision damage");
        check(helper, HazmatArmorItem.installedFilter(helmet).getDamageValue() == 1,
                "Each protected Carbon Monoxide collision must wear the filter by exactly one");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void infernalCoalAndMonoxidePreserveSourceTraits(GameTestHelper helper) {
        ItemStack infernal = new ItemStack(ModItems.legacyOreResourceItem("coal_infernal").get());
        ItemStack shredded = ShredderRecipes.getResult(infernal.copy());
        check(helper, FireboxFuel.rawBurnTime(infernal) == 4_800,
                "Infernal Coal must preserve its source 4,800-tick fuel value");
        check(helper, shredded.is(ModItems.get("powder_coal").get()) && shredded.getCount() == 2,
                "Shredding one Infernal Coal must yield exactly two Coal Powders");

        NetherCoalOreBlock ore = (NetherCoalOreBlock) ModBlocks.legacy("ore_nether_coal").get();
        var walker = helper.makeMockPlayer(GameType.SURVIVAL);
        ore.stepOn(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)),
                ore.defaultBlockState(), walker);
        check(helper, walker.isOnFire() && walker.getRemainingFireTicks() == 60,
                "Walking on Nether Coal Ore must ignite entities for exactly three seconds");

        var gas = ModBlocks.legacy("gas_monoxide").get().defaultBlockState();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 2, 2));
        check(helper, gas.getRenderShape() == RenderShape.INVISIBLE
                        && gas.getShape(helper.getLevel(), pos).isEmpty()
                        && gas.getCollisionShape(helper.getLevel(), pos).isEmpty(),
                "Carbon Monoxide must remain invisible, shapeless, and non-colliding");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
