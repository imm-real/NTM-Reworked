package com.hbm.ntm.ror;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.RadioTelexBlock;
import com.hbm.ntm.blockentity.RadioTelexBlockEntity;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RadioTelexGameTests {
    private RadioTelexGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void fiveLineMessageCrossesTheRadio(GameTestHelper helper) {
        RadioTelexBlockEntity sender = place(helper, new BlockPos(1, 2, 1));
        RadioTelexBlockEntity receiver = place(helper, new BlockPos(5, 2, 1));
        String[] lines = {"HELLO", "PONYVILLE", "", "", ""};
        sender.handleCommand("sve", lines, "twilight", "");
        receiver.handleCommand("sve", emptyLines(), "", "twilight");
        sender.handleCommand("snd", lines, "twilight", "");

        helper.runAfterDelay(20, () -> {
            check(helper, receiver.rxLine(0).equals("HELLO"), "Telex lost its first line");
            check(helper, receiver.rxLine(1).equals("PONYVILLE"), "Telex lost its second line");
            check(helper, !sender.isSending(), "Telex should stop after the fifth line");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void footprintFollowsTheSourceSidewaysDummy(GameTestHelper helper) {
        BlockPos core = new BlockPos(2, 2, 2);
        var state = ModBlocks.RADIO_TELEX.get().defaultBlockState()
                .setValue(RadioTelexBlock.FACING, Direction.NORTH);
        helper.setBlock(core, state);
        ModBlocks.RADIO_TELEX.get().setPlacedBy(helper.getLevel(), helper.absolutePos(core),
                state, null, ItemStack.EMPTY);
        BlockPos side = core.east();
        check(helper, helper.getBlockState(side).is(ModBlocks.RADIO_TELEX.get()),
                "North-facing Telex should reserve the source east-side dummy");
        check(helper, RadioTelexBlock.corePosition(helper.absolutePos(side),
                        helper.getBlockState(side)).equals(helper.absolutePos(core)),
                "Telex dummy should resolve back to its core");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 45)
    public static void pauseWaitsAndPrintSignalMakesPaper(GameTestHelper helper) {
        RadioTelexBlockEntity sender = place(helper, new BlockPos(1, 2, 1));
        RadioTelexBlockEntity receiver = place(helper, new BlockPos(5, 2, 1));
        String[] lines = {"" + RadioTelexBlockEntity.PRINT + "A"
                + RadioTelexBlockEntity.PAUSE + "B", "", "", "", ""};
        sender.handleCommand("sve", lines, "rarity", "");
        receiver.handleCommand("sve", emptyLines(), "", "rarity");
        sender.handleCommand("snd", lines, "rarity", "");

        helper.runAfterDelay(12, () ->
                check(helper, receiver.rxLine(0).equals("A"), "Pause character should wait instead of transmitting"));
        helper.runAfterDelay(36, () -> {
            check(helper, receiver.rxLine(0).equals("AB"), "Telex did not resume after its one-second pause");
            List<ItemEntity> papers = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(helper.absolutePos(new BlockPos(5, 2, 1))).inflate(3D),
                    item -> item.getItem().is(Items.PAPER));
            check(helper, papers.size() == 1, "Print control should make one paper after EOT");
            if (!papers.isEmpty()) {
                check(helper, papers.getFirst().getItem().getHoverName().getString().equals("Message"),
                        "Telex printout should be named Message");
                var lore = papers.getFirst().getItem().get(DataComponents.LORE);
                check(helper, lore != null && !lore.lines().isEmpty()
                                && lore.lines().getFirst().getString().equals("AB"),
                        "Telex printout should contain the received text");
            }
            helper.succeed();
        });
    }

    private static RadioTelexBlockEntity place(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.RADIO_TELEX.get().defaultBlockState()
                .setValue(RadioTelexBlock.FACING, Direction.NORTH));
        if (helper.getBlockEntity(position) instanceof RadioTelexBlockEntity telex) return telex;
        helper.fail("Telex did not create its block entity");
        throw new IllegalStateException();
    }

    private static String[] emptyLines() {
        return new String[]{"", "", "", "", ""};
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
