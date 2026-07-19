package com.hbm.ntm.energy;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.HeCableBlock;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeNetworkGameTests {
    private HeNetworkGameTests() {
    }

    @GameTest(template = "empty")
    public static void transferRespectsEndpointRates(GameTestHelper helper) {
        HeNetworkManager manager = managerWithOneNode();
        TestEndpoint provider = new TestEndpoint(1_000, 1_000, 1_000, HeReceiver.ConnectionPriority.NORMAL);
        TestEndpoint receiver = new TestEndpoint(0, 1_000, 250, HeReceiver.ConnectionPriority.NORMAL);
        HeNode node = manager.getNode(BlockPos.ZERO);
        node.network().addProvider(provider);
        node.network().addReceiver(receiver);

        manager.tick();
        check(helper, receiver.getPower() == 250, "Receiver should accept exactly its 250 HE/t rate");
        check(helper, provider.getPower() == 750, "Provider should lose exactly the accepted 250 HE");
        check(helper, node.network().energyTracker() == 250, "Network tracker should record accepted energy");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void priorityLoopPreservesLegacyCumulativeSubtraction(GameTestHelper helper) {
        HeNetworkManager manager = managerWithOneNode();
        TestEndpoint provider = new TestEndpoint(1_000, 1_000, 1_000, HeReceiver.ConnectionPriority.NORMAL);
        TestEndpoint highest = new TestEndpoint(0, 200, 200, HeReceiver.ConnectionPriority.HIGHEST);
        TestEndpoint high = new TestEndpoint(0, 200, 200, HeReceiver.ConnectionPriority.HIGH);
        TestEndpoint normal = new TestEndpoint(0, 1_000, 1_000, HeReceiver.ConnectionPriority.NORMAL);
        HeNetwork network = manager.getNode(BlockPos.ZERO).network();
        network.addProvider(provider);
        network.addReceiver(highest);
        network.addReceiver(high);
        network.addReceiver(normal);

        manager.tick();
        check(helper, highest.getPower() == 200, "Highest priority receiver should fill first");
        check(helper, high.getPower() == 200, "High priority receiver should fill second");
        check(helper, normal.getPower() == 400, "Legacy cumulative subtraction should leave only 400 HE for normal priority");
        check(helper, provider.getPower() == 200, "Legacy priority quirk should leave 200 HE unused");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void samePriorityDemandIsSharedProportionally(GameTestHelper helper) {
        HeNetworkManager manager = managerWithOneNode();
        TestEndpoint provider = new TestEndpoint(100, 100, 100, HeReceiver.ConnectionPriority.NORMAL);
        TestEndpoint first = new TestEndpoint(0, 100, 100, HeReceiver.ConnectionPriority.NORMAL);
        TestEndpoint second = new TestEndpoint(0, 300, 300, HeReceiver.ConnectionPriority.NORMAL);
        HeNetwork network = manager.getNode(BlockPos.ZERO).network();
        network.addProvider(provider);
        network.addReceiver(first);
        network.addReceiver(second);

        manager.tick();
        check(helper, first.getPower() == 25, "One-quarter demand should receive 25 HE");
        check(helper, second.getPower() == 75, "Three-quarter demand should receive 75 HE");
        check(helper, provider.getPower() == 0, "Provider debit should equal accepted energy");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void straightCableSelectsDedicatedMeshes(GameTestHelper helper) {
        HeCableBlock cable = ModBlocks.RED_CABLE.get();
        BlockPos westPosition = new BlockPos(0, 0, 0);
        BlockPos middlePosition = new BlockPos(1, 0, 0);
        BlockPos eastPosition = new BlockPos(2, 0, 0);
        helper.setBlock(westPosition, cable);
        helper.setBlock(middlePosition, cable);
        helper.setBlock(eastPosition, cable);

        var placedMiddle = helper.getBlockState(middlePosition);
        check(helper, placedMiddle.getValue(HeCableBlock.EAST) && placedMiddle.getValue(HeCableBlock.WEST),
                "The middle cable in a three-cable row should connect to both neighbors");
        check(helper, placedMiddle.getValue(HeCableBlock.RENDER_SHAPE) == HeCableBlock.CableRenderShape.X,
                "The middle cable in a three-cable row should use the original CX mesh");
        check(helper, helper.getBlockState(westPosition).getValue(HeCableBlock.RENDER_SHAPE)
                        == HeCableBlock.CableRenderShape.JUNCTION,
                "A cable at the end of a row should retain the original core bulb");

        var base = cable.defaultBlockState();
        var x = cable.updateRenderShape(base
                .setValue(HeCableBlock.EAST, true)
                .setValue(HeCableBlock.WEST, true));
        var y = cable.updateRenderShape(base
                .setValue(HeCableBlock.UP, true)
                .setValue(HeCableBlock.DOWN, true));
        var z = cable.updateRenderShape(base
                .setValue(HeCableBlock.NORTH, true)
                .setValue(HeCableBlock.SOUTH, true));
        var branch = cable.updateRenderShape(x.setValue(HeCableBlock.UP, true));

        check(helper, x.getValue(HeCableBlock.RENDER_SHAPE) == HeCableBlock.CableRenderShape.X,
                "East-west-only cable should use the original CX mesh");
        check(helper, y.getValue(HeCableBlock.RENDER_SHAPE) == HeCableBlock.CableRenderShape.Y,
                "Up-down-only cable should use the original CY mesh");
        check(helper, z.getValue(HeCableBlock.RENDER_SHAPE) == HeCableBlock.CableRenderShape.Z,
                "North-south-only cable should use the original CZ mesh");
        check(helper, branch.getValue(HeCableBlock.RENDER_SHAPE) == HeCableBlock.CableRenderShape.JUNCTION,
                "A third connection should restore the original core bulb");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void removingMiddleNodeRebuildsTwoNetworks(GameTestHelper helper) {
        HeNetworkManager manager = new HeNetworkManager();
        BlockPos west = BlockPos.ZERO;
        BlockPos middle = BlockPos.ZERO.east();
        BlockPos east = middle.east();
        manager.createNode(new TestConductor().createNode(west));
        manager.createNode(new TestConductor().createNode(middle));
        manager.createNode(new TestConductor().createNode(east));
        manager.tick();
        check(helper, manager.networkCount() == 1, "Three adjacent conductors should merge into one network");

        manager.destroyNode(middle);
        check(helper, manager.networkCount() == 0, "Destroying one link should invalidate the entire old network");
        manager.tick();
        check(helper, manager.nodeCount() == 2, "Only the removed conductor node should expire");
        check(helper, manager.networkCount() == 2, "Remaining disconnected conductors should rebuild as two networks");
        helper.succeed();
    }

    private static HeNetworkManager managerWithOneNode() {
        HeNetworkManager manager = new HeNetworkManager();
        manager.createNode(new TestConductor().createNode(BlockPos.ZERO));
        manager.tick();
        return manager;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    private static final class TestConductor implements HeConductor {
    }

    private static final class TestEndpoint implements HeProvider, HeReceiver {
        private long power;
        private final long maxPower;
        private final long rate;
        private final ConnectionPriority priority;

        private TestEndpoint(long power, long maxPower, long rate, ConnectionPriority priority) {
            this.power = power;
            this.maxPower = maxPower;
            this.rate = rate;
            this.priority = priority;
        }

        @Override
        public long getPower() {
            return power;
        }

        @Override
        public void setPower(long power) {
            this.power = power;
        }

        @Override
        public long getMaxPower() {
            return maxPower;
        }

        @Override
        public long getProviderSpeed() {
            return rate;
        }

        @Override
        public long getReceiverSpeed() {
            return rate;
        }

        @Override
        public ConnectionPriority getPriority() {
            return priority;
        }

        @Override
        public boolean isHeLoaded() {
            return true;
        }
    }
}
