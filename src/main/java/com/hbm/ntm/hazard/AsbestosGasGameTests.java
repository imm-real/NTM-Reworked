package com.hbm.ntm.hazard;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.AsbestosGasBlock;
import com.hbm.ntm.block.StoneResourceBlock;
import com.hbm.ntm.item.HazmatArmorItem;
import com.hbm.ntm.item.StoneResourceBlockItem;
import com.hbm.ntm.radiation.RadiationSystem;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.RenderShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AsbestosGasGameTests {
    private AsbestosGasGameTests() { }

    @GameTest(template = "empty")
    public static void harvestedChrysotileLeavesAsbestosGas(GameTestHelper helper) {
        StoneResourceBlock resource = ModBlocks.STONE_RESOURCE.get();
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos asbestosPos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos hematitePos = helper.absolutePos(new BlockPos(4, 2, 2));
        BlockPos orePos = helper.absolutePos(new BlockPos(6, 2, 2));
        BlockPos gneissOrePos = helper.absolutePos(new BlockPos(8, 2, 2));

        resource.playerDestroy(helper.getLevel(), player, asbestosPos,
                resource.defaultBlockState().setValue(StoneResourceBlock.TYPE, StoneResourceBlock.Type.ASBESTOS),
                null, ItemStack.EMPTY);
        resource.playerDestroy(helper.getLevel(), player, hematitePos,
                resource.defaultBlockState().setValue(StoneResourceBlock.TYPE, StoneResourceBlock.Type.HEMATITE),
                null, ItemStack.EMPTY);
        ModBlocks.legacy("ore_asbestos").get().playerDestroy(helper.getLevel(), player, orePos,
                ModBlocks.legacy("ore_asbestos").get().defaultBlockState(), null, ItemStack.EMPTY);
        ModBlocks.legacy("ore_gneiss_asbestos").get().playerDestroy(helper.getLevel(), player, gneissOrePos,
                ModBlocks.legacy("ore_gneiss_asbestos").get().defaultBlockState(), null, ItemStack.EMPTY);

        check(helper, helper.getLevel().getBlockState(asbestosPos).is(ModBlocks.legacy("gas_asbestos").get()),
                "Harvested Chrysotile must leave Airborne Asbestos Particles in its former position");
        check(helper, helper.getLevel().getBlockState(hematitePos).isAir(),
                "Non-asbestos stone resources must not leave asbestos gas");
        check(helper, helper.getLevel().getBlockState(orePos).is(ModBlocks.legacy("gas_asbestos").get())
                        && helper.getLevel().getBlockState(gneissOrePos)
                        .is(ModBlocks.legacy("gas_asbestos").get()),
                "Both standalone source Asbestos Ores must outgas when harvested");
        check(helper, ModBlocks.legacy("ore_asbestos").get().defaultBlockState().isRandomlyTicking()
                        && ModBlocks.legacy("ore_gneiss_asbestos").get().defaultBlockState().isRandomlyTicking(),
                "Both source Asbestos Ores must retain random adjacent outgassing");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void asbestosGasAppliesExactUnprotectedDose(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        AsbestosGasBlock gas = (AsbestosGasBlock) ModBlocks.legacy("gas_asbestos").get();
        gas.defaultBlockState().entityInside(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), player);
        check(helper, RadiationSystem.data(player).asbestos() == 1,
                "One unprotected Asbestos Gas collision must add exactly one asbestos unit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fineFilterBlocksAsbestosGasWithoutWear(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack helmet = new ItemStack(ModItems.HAZMAT_HELMET.get());
        HazmatArmorItem.installFilter(helmet, new ItemStack(ModItems.GAS_MASK_FILTER.get()));
        player.setItemSlot(EquipmentSlot.HEAD, helmet);

        AsbestosGasBlock gas = (AsbestosGasBlock) ModBlocks.legacy("gas_asbestos").get();
        gas.defaultBlockState().entityInside(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), player);
        check(helper, RadiationSystem.data(player).asbestos() == 0,
                "Fine-particle protection must block Airborne Asbestos Particles");
        check(helper, HazmatArmorItem.installedFilter(helmet).getDamageValue() == 0,
                "Source airborne asbestos must not wear the blocking filter");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chrysotileItemAndGasPreserveSourceTraits(GameTestHelper helper) {
        ItemStack chrysotile = StoneResourceBlockItem.create(ModItems.STONE_RESOURCE_ITEM.get(),
                StoneResourceBlock.Type.ASBESTOS, 1);
        check(helper, chrysotile.getItem() instanceof HazardCarrier carrier
                        && carrier.hbm$getHazards(chrysotile).asbestos() == 1.0F,
                "The Chrysotile block item must carry the source asbestos-one hazard");

        var gas = ModBlocks.legacy("gas_asbestos").get().defaultBlockState();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        check(helper, gas.getRenderShape() == RenderShape.INVISIBLE
                        && gas.getShape(helper.getLevel(), pos).isEmpty()
                        && gas.getCollisionShape(helper.getLevel(), pos).isEmpty(),
                "Asbestos Gas must remain invisible, shapeless, and non-colliding");
        check(helper, ModBlocks.legacy("gas_asbestos").get().getExplosionResistance() == 0.0F,
                "Asbestos Gas must preserve zero source resistance");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
