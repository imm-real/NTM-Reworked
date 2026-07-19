package com.hbm.ntm.energy;

import com.hbm.ntm.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

public final class HeNetworkManager {
    private final Map<BlockPos, HeNode> nodes = new LinkedHashMap<>();
    private final Set<HeNetwork> activeNetworks = new HashSet<>();
    private LongSupplier clock = System::currentTimeMillis;
    private int reapTimer;

    public static HeNetworkManager get(ServerLevel level) {
        return level.getData(ModAttachments.HE_NETWORKS);
    }

    public HeNode getNode(BlockPos position) {
        return nodes.get(position);
    }

    public void createNode(HeNode node) {
        for (BlockPos position : node.positions()) {
            // Duplicate positions replace the old node without asking questions.
            nodes.put(position.immutable(), node);
        }
    }

    public void destroyNode(BlockPos position) {
        HeNode node = getNode(position);
        if (node != null) {
            destroyNode(node);
        }
    }

    public void destroyNode(HeNode node) {
        if (node.network() != null) {
            node.network().destroy();
        }
        for (BlockPos position : node.positions()) {
            if (nodes.get(position) == node) {
                nodes.remove(position);
            }
        }
        node.expire();
    }

    public void tick() {
        for (HeNode node : new ArrayList<>(nodes.values())) {
            if (!node.hasValidNetwork() || node.recentlyChanged()) {
                checkNodeConnections(node);
                node.clearRecentlyChanged();
            }
        }

        List<HeNetwork> networks = new ArrayList<>(activeNetworks);
        for (HeNetwork network : networks) {
            network.resetTrackers();
        }
        for (HeNetwork network : networks) {
            if (network.isValid()) {
                network.update();
            }
        }

        if (reapTimer <= 0) {
            for (HeNetwork network : new ArrayList<>(activeNetworks)) {
                network.links().removeIf(HeNode::expired);
            }
            activeNetworks.removeIf(network -> network.links().isEmpty());
            reapTimer = 5 * 60 * 20;
        } else {
            reapTimer--;
        }
    }

    public int nodeCount() {
        return new HashSet<>(nodes.values()).size();
    }

    public int networkCount() {
        return activeNetworks.size();
    }

    long nowMillis() {
        return clock.getAsLong();
    }

    void setClock(LongSupplier clock) {
        this.clock = clock;
    }

    void addNetwork(HeNetwork network) {
        activeNetworks.add(network);
    }

    void removeNetwork(HeNetwork network) {
        activeNetworks.remove(network);
    }

    private void checkNodeConnections(HeNode node) {
        for (HeNodeConnection connection : node.connections()) {
            HeNode connectedNode = getNode(connection.position());
            if (connectedNode == null) {
                continue;
            }
            if (connectedNode.hasValidNetwork() && connectedNode.network() == node.network()) {
                continue;
            }
            if (checkConnection(connectedNode, connection)) {
                connectNodes(node, connectedNode);
            }
        }

        if (!node.hasValidNetwork()) {
            new HeNetwork(this).joinLink(node);
        }
    }

    private boolean checkConnection(HeNode connectsTo, HeNodeConnection connectFrom) {
        for (HeNodeConnection reverse : connectsTo.connections()) {
            Direction direction = reverse.direction();
            if (reverse.position().relative(direction.getOpposite()).equals(connectFrom.position())
                    && direction == connectFrom.direction().getOpposite()) {
                return true;
            }
        }
        return false;
    }

    private void connectNodes(HeNode origin, HeNode connection) {
        if (origin.hasValidNetwork() && connection.hasValidNetwork()) {
            if (origin.network().linkCount() > connection.network().linkCount()) {
                origin.network().joinNetworks(connection.network());
            } else {
                connection.network().joinNetworks(origin.network());
            }
        } else if (!origin.hasValidNetwork() && connection.hasValidNetwork()) {
            connection.network().joinLink(origin);
        } else if (origin.hasValidNetwork() && !connection.hasValidNetwork()) {
            origin.network().joinLink(connection);
        }
    }
}
