package com.hbm.ntm.ror;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.RadioTorchBlock;
import com.hbm.ntm.blockentity.RadioTorchBlockEntity;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RadioTorchGameTests {
    private RadioTorchGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void transmitterReachesReceiverOneTickLater(GameTestHelper helper) {
        RadioTorchBlockEntity sender = place(helper, new BlockPos(1, 2, 1), true);
        RadioTorchBlockEntity receiver = place(helper, new BlockPos(4, 2, 1), false);
        sender.configure(true, false, false, channels("dashite"), empty(8), empty(16), new byte[16]);
        receiver.configure(true, false, false, channels("dashite"), empty(8), empty(16), new byte[16]);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(4, () -> {
            check(helper, receiver.output() == 15, "RoR receiver should repeat the transmitter's redstone strength");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void numericBroadcastsPileUpLikeTheOriginal(GameTestHelper helper) {
        RadioTorchBlockEntity first = place(helper, new BlockPos(1, 2, 1), true);
        RadioTorchBlockEntity second = place(helper, new BlockPos(4, 2, 1), true);
        RadioTorchBlockEntity receiver = place(helper, new BlockPos(7, 2, 1), false);
        String[] seven = empty(16), eight = empty(16);
        seven[15] = "7"; eight[15] = "8";
        first.configure(true, true, false, channels("mareconi"), empty(8), seven, new byte[16]);
        second.configure(true, true, false, channels("mareconi"), empty(8), eight, new byte[16]);
        receiver.configure(true, false, false, channels("mareconi"), empty(8), empty(16), new byte[16]);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.REDSTONE_BLOCK);
        helper.setBlock(new BlockPos(5, 1, 1), Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(4, () -> {
            check(helper, receiver.output() == 15, "Concurrent numeric signals should add instead of eating one another");
            helper.succeed();
        });
    }

    private static RadioTorchBlockEntity place(GameTestHelper helper, BlockPos pos, boolean sender) {
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, (sender ? ModBlocks.RADIO_TORCH_SENDER : ModBlocks.RADIO_TORCH_RECEIVER).get()
                .defaultBlockState().setValue(RadioTorchBlock.FACING, Direction.UP));
        if (helper.getBlockEntity(pos) instanceof RadioTorchBlockEntity radio) return radio;
        helper.fail("Radio torch did not create its block entity");
        throw new IllegalStateException();
    }

    private static String[] channels(String channel) {
        String[] values = empty(8); values[0] = channel; return values;
    }
    private static String[] empty(int size) {
        String[] values = new String[size]; java.util.Arrays.fill(values, ""); return values;
    }
    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
