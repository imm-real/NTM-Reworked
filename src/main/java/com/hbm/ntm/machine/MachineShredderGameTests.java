package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.MachineShredderBlockEntity;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MachineShredderGameTests {
    private MachineShredderGameTests() {
    }

    @GameTest(template = "empty")
    public static void infiniteBatteryChargesAfterProcessingCheck(GameTestHelper helper) {
        BlockPos position = new BlockPos(1, 1, 1);
        MachineShredderBlockEntity shredder = placeShredder(helper, position);
        installSteelBlades(shredder);
        shredder.setItem(0, new ItemStack(Items.STONE));
        shredder.setItem(MachineShredderBlockEntity.BATTERY, new ItemStack(ModItems.BATTERY_CREATIVE.get()));

        tick(helper, position, shredder);
        check(helper, shredder.getPower() == MachineShredderBlockEntity.MAX_POWER,
                "The Infinite Battery should fill the Shredder at the end of the tick");
        check(helper, shredder.progress() == 0,
                "A battery filling an empty Shredder must not begin processing until the following tick");

        tick(helper, position, shredder);
        check(helper, shredder.progress() == 1, "The charged Shredder should begin on the next tick");
        check(helper, shredder.getPower() == MachineShredderBlockEntity.MAX_POWER,
                "The Infinite Battery should refill the five HE spent during the tick");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nineLanesShareOneCycleAndOneBladeWear(GameTestHelper helper) {
        BlockPos position = new BlockPos(1, 1, 1);
        MachineShredderBlockEntity shredder = placeShredder(helper, position);
        installSteelBlades(shredder);
        for (int slot = 0; slot < 9; slot++) {
            shredder.setItem(slot, new ItemStack(Items.STONE));
        }
        shredder.setPower(300);

        for (int tick = 0; tick < MachineShredderBlockEntity.PROCESSING_SPEED; tick++) {
            tick(helper, position, shredder);
        }

        check(helper, shredder.getPower() == 0, "A complete batch should consume exactly 300 HE");
        for (int slot = 0; slot < 9; slot++) {
            check(helper, shredder.getItem(slot).isEmpty(), "Every occupied input lane should consume one item");
        }
        check(helper, shredder.getItem(9).is(Items.GRAVEL) && shredder.getItem(9).getCount() == 9,
                "Nine stone lanes should merge into nine gravel in the pooled output");
        check(helper, shredder.getItem(MachineShredderBlockEntity.BLADE_LEFT).getDamageValue() == 1,
                "The left blade should take one damage per batch, not per lane");
        check(helper, shredder.getItem(MachineShredderBlockEntity.BLADE_RIGHT).getDamageValue() == 1,
                "The right blade should take one damage per batch, not per lane");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownItemsBecomeScrapAndInterruptedWorkResets(GameTestHelper helper) {
        BlockPos position = new BlockPos(1, 1, 1);
        MachineShredderBlockEntity shredder = placeShredder(helper, position);
        installSteelBlades(shredder);
        shredder.setItem(0, new ItemStack(Items.FEATHER));
        shredder.setPower(400);

        for (int tick = 0; tick < 10; tick++) {
            tick(helper, position, shredder);
        }
        shredder.setItem(MachineShredderBlockEntity.BLADE_RIGHT, ItemStack.EMPTY);
        tick(helper, position, shredder);
        check(helper, shredder.progress() == 0, "Removing a blade must discard all partial progress");
        check(helper, shredder.getPower() == 350, "Interrupted work must not refund the 50 HE already spent");

        shredder.setItem(MachineShredderBlockEntity.BLADE_RIGHT, new ItemStack(ModItems.BLADES_STEEL.get()));
        for (int tick = 0; tick < MachineShredderBlockEntity.PROCESSING_SPEED; tick++) {
            tick(helper, position, shredder);
        }
        check(helper, shredder.getItem(9).is(ModItems.get("scrap").get()),
                "An item without an explicit Shredder recipe must become one Scrap");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeHeTransferClampsToCapacity(GameTestHelper helper) {
        BlockPos position = new BlockPos(1, 1, 1);
        MachineShredderBlockEntity shredder = placeShredder(helper, position);
        long remainder = shredder.transferPower(12_500);
        check(helper, shredder.getPower() == MachineShredderBlockEntity.MAX_POWER,
                "Native HE transfer should fill the 10,000 HE buffer");
        check(helper, remainder == 2_500, "Native HE transfer should return the unaccepted remainder");
        helper.succeed();
    }

    private static MachineShredderBlockEntity placeShredder(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.MACHINE_SHREDDER.get());
        return helper.getBlockEntity(position);
    }

    private static void installSteelBlades(MachineShredderBlockEntity shredder) {
        shredder.setItem(MachineShredderBlockEntity.BLADE_LEFT, new ItemStack(ModItems.BLADES_STEEL.get()));
        shredder.setItem(MachineShredderBlockEntity.BLADE_RIGHT, new ItemStack(ModItems.BLADES_STEEL.get()));
    }

    private static void tick(
            GameTestHelper helper,
            BlockPos relativePosition,
            MachineShredderBlockEntity shredder
    ) {
        MachineShredderBlockEntity.tick(helper.getLevel(), helper.absolutePos(relativePosition),
                helper.getBlockState(relativePosition), shredder);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
