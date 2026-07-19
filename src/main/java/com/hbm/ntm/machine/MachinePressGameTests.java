package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.MachinePressBlock;
import com.hbm.ntm.blockentity.MachinePressBlockEntity;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MachinePressGameTests {
    private MachinePressGameTests() {
    }

    @GameTest(template = "empty")
    public static void coalProvidesStoredOperationsAndDiamondPowderPresses(GameTestHelper helper) {
        BlockPos position = new BlockPos(1, 1, 1);
        MachinePressBlockEntity press = placePress(helper, position);
        press.setItem(MachinePressBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL));
        press.setItem(MachinePressBlockEntity.SLOT_STAMP,
                new ItemStack(ModItems.STAMPS.get("stamp_stone_flat").get()));
        press.setItem(MachinePressBlockEntity.SLOT_INPUT,
                new ItemStack(ModItems.get("powder_diamond").get()));

        MachinePressBlockEntity.tick(helper.getLevel(), helper.absolutePos(position),
                helper.getBlockState(position), press);
        check(helper, press.burnTime() == 1_600, "Coal should load its full furnace burn value as operation credit");
        check(helper, press.speed() == 0, "Fuel loaded at the end of a tick must not accelerate the press until the next tick");

        for (int tick = 0; tick < 120; tick++) {
            MachinePressBlockEntity.tick(helper.getLevel(), helper.absolutePos(position),
                    helper.getBlockState(position), press);
        }

        check(helper, press.getItem(MachinePressBlockEntity.SLOT_OUTPUT).is(Items.DIAMOND),
                "A flat stamp should press diamond powder into one diamond");
        check(helper, press.getItem(MachinePressBlockEntity.SLOT_INPUT).isEmpty(),
                "A successful operation should consume exactly one input");
        check(helper, press.getItem(MachinePressBlockEntity.SLOT_STAMP).getDamageValue() == 1,
                "A finite stamp should take exactly one durability damage per operation");
        check(helper, press.burnTime() == 1_400,
                "A successful operation should consume exactly 200 stored burn ticks");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void flatStampExtractsLatexOnlyFromExactJungleLog(GameTestHelper helper) {
        ItemStack stamp = new ItemStack(ModItems.STAMPS.get("stamp_stone_flat").get());
        ItemStack latex = com.hbm.ntm.recipe.PressRecipes.getOutput(new ItemStack(Items.JUNGLE_LOG), stamp);
        check(helper, latex.is(ModItems.get("ball_resin").get()) && latex.getCount() == 1,
                "The source jungle-log Flat Stamp recipe must produce one Latex");
        check(helper, com.hbm.ntm.recipe.PressRecipes.getOutput(new ItemStack(Items.OAK_LOG), stamp).isEmpty(),
                "The source Latex pressing recipe must not broaden to non-jungle logs");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void adjacentPreheaterQuadruplesAcceleration(GameTestHelper helper) {
        BlockPos normalPosition = new BlockPos(1, 1, 1);
        BlockPos heatedPosition = new BlockPos(5, 1, 1);
        MachinePressBlockEntity normal = placePress(helper, normalPosition);
        MachinePressBlockEntity heated = placePress(helper, heatedPosition);
        prepareRunnablePress(normal);
        prepareRunnablePress(heated);
        helper.setBlock(heatedPosition.east(), ModBlocks.PRESS_PREHEATER.get());

        for (int tick = 0; tick < 11; tick++) {
            MachinePressBlockEntity.tick(helper.getLevel(), helper.absolutePos(normalPosition),
                    helper.getBlockState(normalPosition), normal);
            MachinePressBlockEntity.tick(helper.getLevel(), helper.absolutePos(heatedPosition),
                    helper.getBlockState(heatedPosition), heated);
        }

        check(helper, normal.speed() == 10, "A normal Burner Press should accelerate by one speed unit per active tick");
        check(helper, heated.speed() == 40, "An adjacent preheater should accelerate the Burner Press by four per active tick");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void removingAnyPartTearsDownTheThreeBlockColumn(GameTestHelper helper) {
        BlockPos position = new BlockPos(1, 1, 1);
        placePress(helper, position);
        helper.getLevel().setBlock(helper.absolutePos(position.above(2)), Blocks.AIR.defaultBlockState(), 3);

        check(helper, helper.getBlockState(position).isAir(), "Removing the upper press block should remove the core");
        check(helper, helper.getBlockState(position.above()).isAir(), "Removing one part should remove the middle block");
        check(helper, helper.getBlockState(position.above(2)).isAir(), "The directly removed upper block should remain air");
        helper.succeed();
    }

    private static MachinePressBlockEntity placePress(GameTestHelper helper, BlockPos position) {
        MachinePressBlock block = ModBlocks.MACHINE_PRESS.get();
        helper.setBlock(position, block.defaultBlockState().setValue(MachinePressBlock.PART, MachinePressBlock.PressPart.LOWER));
        helper.setBlock(position.above(), block.defaultBlockState().setValue(MachinePressBlock.PART, MachinePressBlock.PressPart.MIDDLE));
        helper.setBlock(position.above(2), block.defaultBlockState().setValue(MachinePressBlock.PART, MachinePressBlock.PressPart.UPPER));
        return helper.getBlockEntity(position);
    }

    private static void prepareRunnablePress(MachinePressBlockEntity press) {
        press.setItem(MachinePressBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL));
        press.setItem(MachinePressBlockEntity.SLOT_STAMP,
                new ItemStack(ModItems.STAMPS.get("stamp_stone_flat").get()));
        press.setItem(MachinePressBlockEntity.SLOT_INPUT,
                new ItemStack(ModItems.get("powder_diamond").get()));
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
