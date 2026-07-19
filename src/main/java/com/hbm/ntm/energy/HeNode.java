package com.hbm.ntm.energy;

import net.minecraft.core.BlockPos;

import java.util.Arrays;
import java.util.List;

public final class HeNode {
    private final List<BlockPos> positions;
    private final List<HeNodeConnection> connections;
    private HeNetwork network;
    private boolean expired;
    private boolean recentlyChanged = true;

    public HeNode(BlockPos[] positions, HeNodeConnection[] connections) {
        this.positions = List.copyOf(Arrays.asList(positions));
        this.connections = List.copyOf(Arrays.asList(connections));
    }

    public List<BlockPos> positions() {
        return positions;
    }

    public List<HeNodeConnection> connections() {
        return connections;
    }

    public HeNetwork network() {
        return network;
    }

    public void setNetwork(HeNetwork network) {
        this.network = network;
        this.recentlyChanged = true;
    }

    public boolean expired() {
        return expired;
    }

    public void expire() {
        this.expired = true;
    }

    public boolean recentlyChanged() {
        return recentlyChanged;
    }

    public void clearRecentlyChanged() {
        this.recentlyChanged = false;
    }

    public boolean hasValidNetwork() {
        return network != null && network.isValid();
    }
}
