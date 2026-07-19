package com.hbm.ntm.radiation;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.GeigerCounterBlock;
import com.hbm.ntm.blockentity.GeigerCounterBlockEntity;
import com.hbm.ntm.item.GeigerCounterItem;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RadiationObservabilityGameTests {
    private RadiationObservabilityGameTests() {
    }

    @GameTest(template = "empty")
    public static void geigerClickBandsPreserveOverlappingSourceWeights(GameTestHelper helper) {
        helper.assertTrue(Arrays.equals(RadiationClicker.geigerCandidates(0.5F), new int[]{0, 0, 1}),
                "Sub-one RAD/s Geiger readings must retain two silent entries and one level-one click");
        helper.assertTrue(Arrays.equals(RadiationClicker.geigerCandidates(5.0F), new int[]{1})
                        && Arrays.equals(RadiationClicker.geigerCandidates(Math.nextUp(5.0F)), new int[]{1, 2}),
                "The strict five-RAD boundary must transition from level one to the weighted one/two overlap");
        helper.assertTrue(Arrays.equals(RadiationClicker.geigerCandidates(10.0F), new int[]{2})
                        && Arrays.equals(RadiationClicker.geigerCandidates(Math.nextUp(10.0F)), new int[]{2, 3}),
                "The strict ten-RAD boundary must transition from level two to the weighted two/three overlap");
        helper.assertTrue(Arrays.equals(RadiationClicker.geigerCandidates(25.0F), new int[]{5})
                        && Arrays.equals(RadiationClicker.geigerCandidates(Math.nextUp(25.0F)), new int[]{5, 6})
                        && Arrays.equals(RadiationClicker.geigerCandidates(30.0F), new int[]{6}),
                "The high click bands must preserve strict level-five and level-six boundaries");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dosimeterPreservesSourceThresholdQuirk(GameTestHelper helper) {
        helper.assertTrue(Arrays.equals(RadiationClicker.dosimeterCandidates(0.25F), new int[]{0, 1}),
                "Low Dosimeter readings must alternate between silence and level one");
        helper.assertTrue(Arrays.equals(RadiationClicker.dosimeterCandidates(0.75F), new int[]{1, 2}),
                "Mid-low Dosimeter readings must overlap levels one and two");
        helper.assertTrue(Arrays.equals(RadiationClicker.dosimeterCandidates(1.5F), new int[]{2}),
                "The source's duplicated comparison must leave 1-2 RAD/s at level two");
        helper.assertTrue(Arrays.equals(RadiationClicker.dosimeterCandidates(2.0F), new int[]{3}),
                "The source's x>=1 && x>=2 condition must become level three at exactly two");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dosimeterReadoutTruncatesBeforeClamping(GameTestHelper helper) {
        RadiationReadout.DosimeterReading below = RadiationReadout.dosimeterReading(3.69F);
        RadiationReadout.DosimeterReading above = RadiationReadout.dosimeterReading(3.79F);
        helper.assertTrue(below.environmentalRadiation() == 3.6D && !below.overLimit(),
                "A raw 3.69 reading must truncate to 3.6 without showing the over-range marker");
        helper.assertTrue(above.environmentalRadiation() == 3.6D && above.overLimit(),
                "A displayed value above 3.6 must clamp and show the over-range marker");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void geigerReadoutPreservesSourceTruncationAndResistanceMath(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        RadiationData data = RadiationSystem.data(player);
        data.setRadiation(234.59F);
        data.addEnvironmentalRadiation(3.71F);
        data.finishExposureInterval();
        data.refreshRadXTicks(200);
        ChunkRadiationData.get(helper.getLevel()).set(player.blockPosition(), 12.39F);

        RadiationReadout.GeigerReading reading = RadiationReadout.geigerReading(player);
        helper.assertTrue(reading.chunkRadiation() == 12.3D
                        && reading.environmentalRadiation() == 3.7D
                        && reading.playerRadiation() == 234.5D,
                "Geiger values must truncate to one decimal instead of rounding");
        helper.assertTrue(reading.resistanceCoefficient() == 0.2D
                        && reading.resistancePercent() == 36.9D,
                "Rad-X must display coefficient 0.2 and the source 36.9% blocked-radiation value");
        helper.assertTrue(GeigerCounterItem.check(helper.getLevel(), player.blockPosition()) == 13,
                "The source check helper must ceil 12.39 chunk radiation to 13");
        ChunkRadiationData.get(helper.getLevel()).set(player.blockPosition(), 0.0F);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void instrumentsRetainSingleStackIdentity(GameTestHelper helper) {
        ItemStack geiger = new ItemStack(ModItems.GEIGER_COUNTER.get());
        ItemStack dosimeter = new ItemStack(ModItems.DOSIMETER.get());
        helper.assertTrue(geiger.getMaxStackSize() == 1 && dosimeter.getMaxStackSize() == 1,
                "Both source instruments must retain stack size one");
        helper.assertTrue(ModBlocks.GEIGER.get().asItem() == ModItems.GEIGER_BLOCK_ITEM.get(),
                "The placed Geiger must expose its stable hbm:geiger block item identity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void contaminationGraceStillFeedsEnvironmentalMeter(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.tickCount = 199;
        RadiationData data = RadiationSystem.data(player);
        RadiationSystem.contaminate(player, 5.0F, false);
        helper.assertTrue(data.radiation() == 0.0F,
                "The original 200-tick player grace period must block accumulated contamination");
        data.finishExposureInterval();
        helper.assertTrue(data.radBuf() == 5.0F,
                "Attempted exposure must still feed the instrument environmental buffer during grace");
        player.tickCount = 200;
        RadiationSystem.contaminate(player, 5.0F, false);
        helper.assertTrue(data.radiation() == 5.0F,
                "Exposure must begin accumulating at source tick 200");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ironResistanceAndBypassExposureRemainExact(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.tickCount = 200;
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        float resistance = RadiationSystem.calculateResistance(player);
        helper.assertTrue(Math.abs(resistance - 0.0225F) < 0.000001F,
                "A full iron suit must retain the original total resistance coefficient 0.0225");

        RadiationData data = RadiationSystem.data(player);
        RadiationSystem.contaminate(player, 10.0F, false);
        float shielded = 10.0F * (float) Math.pow(10.0D, -0.0225D);
        helper.assertTrue(Math.abs(data.radiation() - shielded) < 0.0001F,
                "Ordinary contamination must apply the original 10^-resistance multiplier");
        RadiationSystem.contaminate(player, 10.0F, true);
        helper.assertTrue(Math.abs(data.radiation() - (shielded + 10.0F)) < 0.0001F,
                "Bypass contamination must ignore armor while still recording exposure");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void placedGeigerSamplesChunkFieldAndDrivesComparator(GameTestHelper helper) {
        BlockPos position = new BlockPos(2, 1, 2);
        var state = ModBlocks.GEIGER.get().defaultBlockState().setValue(GeigerCounterBlock.FACING, Direction.NORTH);
        helper.setBlock(position, state);
        ChunkRadiationData.get(helper.getLevel()).set(helper.absolutePos(position), 12.0F);
        GeigerCounterBlockEntity geiger = helper.getBlockEntity(position);
        for (int tick = 0; tick < 10; tick++) {
            GeigerCounterBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(position), state, geiger);
        }
        helper.assertTrue(geiger.lastRadiation() == 12.0F,
                "The placed Geiger must refresh its chunk reading every ten ticks");
        helper.assertTrue(ModBlocks.GEIGER.get().getAnalogOutputSignal(
                        state, helper.getLevel(), helper.absolutePos(position)) == 3,
                "Twelve RAD/s must produce ceil(12/5)=3 comparator strength");
        ChunkRadiationData.get(helper.getLevel()).set(helper.absolutePos(position), 70.01F);
        helper.assertTrue(ModBlocks.GEIGER.get().getAnalogOutputSignal(
                        state, helper.getLevel(), helper.absolutePos(position)) == 15,
                "Comparator queries must read the live field and clamp values above 70 RAD/s to 15");
        helper.assertTrue(GeigerCounterBlock.comparatorSignal(0.0F) == 0
                        && GeigerCounterBlock.comparatorSignal(Math.nextUp(5.0F)) == 2
                        && GeigerCounterBlock.comparatorSignal(75.0F) == 15,
                "Comparator boundary math must preserve ceil(rad/5) with a strength-15 cap");
        ChunkRadiationData.get(helper.getLevel()).set(helper.absolutePos(position), 0.0F);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void placedGeigerUsesSourceFacingShapes(GameTestHelper helper) {
        BlockPos position = new BlockPos(2, 1, 2);
        var block = ModBlocks.GEIGER.get();
        helper.setBlock(position, block.defaultBlockState().setValue(GeigerCounterBlock.FACING, Direction.NORTH));
        AABB north = helper.getBlockState(position).getShape(helper.getLevel(), helper.absolutePos(position)).bounds();
        helper.assertTrue(north.minX == 1.5D / 16.0D && north.maxY == 9.0D / 16.0D
                        && north.maxZ == 14.0D / 16.0D,
                "North-facing Geiger bounds must preserve the source 1.5/16, 9/16 and 14/16 edges");

        helper.setBlock(position, block.defaultBlockState().setValue(GeigerCounterBlock.FACING, Direction.EAST));
        AABB east = helper.getBlockState(position).getShape(helper.getLevel(), helper.absolutePos(position)).bounds();
        helper.assertTrue(east.minX == 2.0D / 16.0D && east.minZ == 1.5D / 16.0D
                        && east.maxX == 1.0D && east.maxZ == 1.0D,
                "East-facing Geiger bounds must preserve the source asymmetric footprint");
        helper.succeed();
    }
}
