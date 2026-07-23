package com.hbm.ntm.autocal;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.RadioAutocalBlock;
import com.hbm.ntm.blockentity.RadioAutocalBlockEntity;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.ror.RttySystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RadioAutocalGameTests {
    private RadioAutocalGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void footprintMatchesTheTwoBlockSourceMachine(GameTestHelper helper) {
        BlockPos core = new BlockPos(2, 2, 2);
        var state = ModBlocks.RADIO_AUTOCAL.get().defaultBlockState()
                .setValue(RadioAutocalBlock.FACING, Direction.NORTH);
        helper.setBlock(core, state);
        ModBlocks.RADIO_AUTOCAL.get().setPlacedBy(helper.getLevel(), helper.absolutePos(core),
                state, null, ItemStack.EMPTY);

        BlockPos top = core.above();
        check(helper, helper.getBlockState(top).is(ModBlocks.RADIO_AUTOCAL.get()),
                "AUTOCAL should reserve the block above its core");
        check(helper, helper.getBlockState(top).getValue(RadioAutocalBlock.PART) == 1,
                "AUTOCAL's upper block should be its dummy");
        check(helper, RadioAutocalBlock.corePosition(helper.absolutePos(top),
                        helper.getBlockState(top)).equals(helper.absolutePos(core)),
                "AUTOCAL's dummy should resolve back to its core");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void calculatorRunsTheExtendedInstructionSetAndTransmits(GameTestHelper helper) {
        BlockPos position = new BlockPos(2, 2, 2);
        helper.setBlock(position, ModBlocks.RADIO_AUTOCAL.get().defaultBlockState()
                .setValue(RadioAutocalBlock.FACING, Direction.NORTH));
        if (!(helper.getBlockEntity(position) instanceof RadioAutocalBlockEntity autocal)) {
            helper.fail("AUTOCAL did not create its block entity");
            return;
        }

        autocal.loadScript(String.join("\n",
                "clockspeed 20",
                "buffer 4",
                "save apple",
                "evalr $apple$ * 3",
                "push",
                "buffer rarity;twilight",
                "splitter ;",
                "split 2",
                "length",
                "pop",
                "send autocal-test",
                "shutdown"));
        autocal.handleCommand("on", "");

        helper.runAfterDelay(4, () -> {
            RttySystem.Message message = RttySystem.listen(helper.getLevel(), "autocal-test");
            check(helper, message != null && message.value().equals("12"),
                    "AUTOCAL should transmit the restored stack value");
            check(helper, !autocal.isOn(), "Shutdown should turn AUTOCAL off");
            check(helper, autocal.history(5).equals("Program requested shutdown"),
                    "AUTOCAL should report the source shutdown reason");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void comparisonsAndJumpsKeepTheSourceSemantics(GameTestHelper helper) {
        ParseMSES1Ext1 parser = new ParseMSES1Ext1();
        IParse.ParseContext context = new IParse.ParseContext(helper.getLevel());
        parser.generateJumpPoints(context, "dest winner", 7);

        check(helper, parser.eval(context, "buffer 5") == IParse.StatementResult.OK,
                "Buffer command failed");
        check(helper, parser.eval(context, "gtb 6") == IParse.StatementResult.OK
                        && context.readBuffer().equals("true"),
                "gtb should compare its supplied value against the buffer");
        check(helper, parser.eval(context, "jmpif winner") == IParse.StatementResult.OK
                        && context.current() == 7,
                "jmpif should use a true buffer and the generated destination");

        context.writeBuffer("apple;jack;rarity");
        check(helper, parser.eval(context, "split 2") == IParse.StatementResult.OK
                        && context.readBuffer().equals("jack"),
                "The default semicolon splitter should select one-based fragments");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
