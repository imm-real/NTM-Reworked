package com.hbm.ntm.energy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class HeNetwork {
    private static final Random RANDOM = new Random();
    private static final long TIMEOUT_MILLIS = 3_000L;

    private final HeNetworkManager manager;
    private final Set<HeNode> links = new LinkedHashSet<>();
    private final Map<HeReceiver, Long> receiverEntries = new HashMap<>();
    private final Map<HeProvider, Long> providerEntries = new HashMap<>();
    private boolean valid = true;
    private long energyTracker;

    HeNetwork(HeNetworkManager manager) {
        this.manager = manager;
        manager.addNetwork(this);
    }

    public boolean isValid() {
        return valid;
    }

    public int linkCount() {
        return links.size();
    }

    public long energyTracker() {
        return energyTracker;
    }

    public Set<HeNode> links() {
        return links;
    }

    public void resetTrackers() {
        energyTracker = 0;
    }

    public void addReceiver(HeReceiver receiver) {
        receiverEntries.put(receiver, manager.nowMillis());
    }

    public void removeReceiver(HeReceiver receiver) {
        receiverEntries.remove(receiver);
    }

    public void addProvider(HeProvider provider) {
        providerEntries.put(provider, manager.nowMillis());
    }

    public void removeProvider(HeProvider provider) {
        providerEntries.remove(provider);
    }

    public boolean isSubscribed(HeReceiver receiver) {
        return receiverEntries.containsKey(receiver);
    }

    public boolean isProvider(HeProvider provider) {
        return providerEntries.containsKey(provider);
    }

    public void joinNetworks(HeNetwork network) {
        if (network == this) {
            return;
        }

        List<HeNode> oldNodes = new ArrayList<>(network.links);
        for (HeNode conductor : oldNodes) {
            forceJoinLink(conductor);
        }
        network.links.clear();

        for (HeReceiver receiver : network.receiverEntries.keySet()) {
            addReceiver(receiver);
        }
        for (HeProvider provider : network.providerEntries.keySet()) {
            addProvider(provider);
        }
        network.destroy();
    }

    public HeNetwork joinLink(HeNode node) {
        if (node.network() != null) {
            node.network().leaveLink(node);
        }
        return forceJoinLink(node);
    }

    private HeNetwork forceJoinLink(HeNode node) {
        links.add(node);
        node.setNetwork(this);
        return this;
    }

    private void leaveLink(HeNode node) {
        node.setNetwork(null);
        links.remove(node);
    }

    public void update() {
        if (providerEntries.isEmpty() || receiverEntries.isEmpty()) {
            return;
        }

        long timestamp = manager.nowMillis();
        List<AvailableProvider> providers = new ArrayList<>();
        long powerAvailable = 0;

        Iterator<Map.Entry<HeProvider, Long>> providerIterator = providerEntries.entrySet().iterator();
        while (providerIterator.hasNext()) {
            Map.Entry<HeProvider, Long> entry = providerIterator.next();
            if (timestamp - entry.getValue() > TIMEOUT_MILLIS || isBadLink(entry.getKey())) {
                providerIterator.remove();
                continue;
            }
            long source = Math.min(entry.getKey().getPower(), entry.getKey().getProviderSpeed());
            if (source > 0) {
                providers.add(new AvailableProvider(entry.getKey(), source));
                powerAvailable += source;
            }
        }

        List<List<DemandingReceiver>> receivers = receiverBuckets();
        long[] demand = new long[receivers.size()];
        long totalDemand = 0;

        Iterator<Map.Entry<HeReceiver, Long>> receiverIterator = receiverEntries.entrySet().iterator();
        while (receiverIterator.hasNext()) {
            Map.Entry<HeReceiver, Long> entry = receiverIterator.next();
            if (timestamp - entry.getValue() > TIMEOUT_MILLIS || isBadLink(entry.getKey())) {
                receiverIterator.remove();
                continue;
            }
            long requested = Math.min(
                    entry.getKey().getMaxPower() - entry.getKey().getPower(),
                    entry.getKey().getReceiverSpeed()
            );
            if (requested > 0) {
                int priority = entry.getKey().getPriority().ordinal();
                receivers.get(priority).add(new DemandingReceiver(entry.getKey(), requested));
                demand[priority] += requested;
                totalDemand += requested;
            }
        }

        long toTransfer = Math.min(powerAvailable, totalDemand);
        long energyUsed = 0;

        for (int priority = receivers.size() - 1; priority >= 0; priority--) {
            List<DemandingReceiver> list = receivers.get(priority);
            long priorityDemand = demand[priority];
            for (DemandingReceiver entry : list) {
                double weight = (double) entry.demand / (double) priorityDemand;
                long toSend = (long) Math.min(Math.max(toTransfer * weight, 0D), entry.demand);
                energyUsed += toSend - entry.receiver.transferPower(toSend);
            }
            // Preserved 1.7.10 quirk: energyUsed is cumulative across priority bands.
            toTransfer -= energyUsed;
        }

        energyTracker += energyUsed;
        long leftover = energyUsed;

        for (AvailableProvider entry : providers) {
            double weight = (double) entry.available / (double) powerAvailable;
            long toUse = (long) Math.max(energyUsed * weight, 0D);
            entry.provider.usePower(toUse);
            leftover -= toUse;
        }

        int iterationsLeft = 100;
        while (iterationsLeft-- > 0 && leftover > 0 && !providers.isEmpty()) {
            HeProvider scapegoat = providers.get(RANDOM.nextInt(providers.size())).provider;
            long toUse = Math.min(leftover, scapegoat.getPower());
            scapegoat.usePower(toUse);
            leftover -= toUse;
        }
    }

    public long sendPowerDiode(long power) {
        if (receiverEntries.isEmpty()) {
            return power;
        }

        long timestamp = manager.nowMillis();
        List<List<DemandingReceiver>> receivers = receiverBuckets();
        long[] demand = new long[receivers.size()];
        long totalDemand = 0;

        Iterator<Map.Entry<HeReceiver, Long>> iterator = receiverEntries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<HeReceiver, Long> entry = iterator.next();
            if (timestamp - entry.getValue() > TIMEOUT_MILLIS) {
                iterator.remove();
                continue;
            }
            long requested = Math.min(
                    entry.getKey().getMaxPower() - entry.getKey().getPower(),
                    entry.getKey().getReceiverSpeed()
            );
            int priority = entry.getKey().getPriority().ordinal();
            receivers.get(priority).add(new DemandingReceiver(entry.getKey(), requested));
            demand[priority] += requested;
            totalDemand += requested;
        }

        long toTransfer = Math.min(power, totalDemand);
        long energyUsed = 0;
        for (int priority = receivers.size() - 1; priority >= 0; priority--) {
            long priorityDemand = demand[priority];
            for (DemandingReceiver entry : receivers.get(priority)) {
                double weight = (double) entry.demand / (double) priorityDemand;
                long toSend = (long) Math.max(toTransfer * weight, 0D);
                energyUsed += toSend - entry.receiver.transferPower(toSend);
            }
            toTransfer -= energyUsed;
        }

        energyTracker += energyUsed;
        return power - energyUsed;
    }

    void destroy() {
        valid = false;
        manager.removeNetwork(this);
        for (HeNode link : links) {
            if (link.network() == this) {
                link.setNetwork(null);
            }
        }
        links.clear();
        receiverEntries.clear();
        providerEntries.clear();
    }

    private static boolean isBadLink(HeLoaded endpoint) {
        if (!endpoint.isHeLoaded()) {
            return true;
        }
        return endpoint instanceof net.minecraft.world.level.block.entity.BlockEntity blockEntity
                && blockEntity.isRemoved();
    }

    private static List<List<DemandingReceiver>> receiverBuckets() {
        int count = HeReceiver.ConnectionPriority.values().length;
        List<List<DemandingReceiver>> receivers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            receivers.add(new ArrayList<>());
        }
        return receivers;
    }

    private record AvailableProvider(HeProvider provider, long available) {
    }

    private record DemandingReceiver(HeReceiver receiver, long demand) {
    }
}
