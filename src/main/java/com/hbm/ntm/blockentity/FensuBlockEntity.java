package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.FensuBlock;
import com.hbm.ntm.energy.HeConductor;
import com.hbm.ntm.energy.HeNetwork;
import com.hbm.ntm.energy.HeNetworkManager;
import com.hbm.ntm.energy.HeNode;
import com.hbm.ntm.energy.HeNodeConnection;
import com.hbm.ntm.energy.HeProvider;
import com.hbm.ntm.energy.HeReceiver;
import com.hbm.ntm.inventory.FensuMenu;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;

/** BigInteger battery for people who consider long overflow a personal insult. */
public final class FensuBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, HeConductor, HeProvider, HeReceiver {
    public static final String ITEM_POWER = "power";
    public static final long MAX_TRANSFER = Long.MAX_VALUE / 100L;
    public static final int MODE_INPUT = 0;
    public static final int MODE_BUFFER = 1;
    public static final int MODE_OUTPUT = 2;
    public static final int MODE_NONE = 3;
    private static final BigInteger PROVISION_LIMIT = BigInteger.valueOf(MAX_TRANSFER / 2L);
    private static final int[] NO_SIDED_SLOTS = {};

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private final BigInteger[] log = new BigInteger[20];
    private BigInteger power = BigInteger.ZERO;
    private BigInteger delta = BigInteger.ZERO;
    private HeNode node;
    private short redLow = MODE_INPUT;
    private short redHigh = MODE_OUTPUT;
    private ConnectionPriority priority = ConnectionPriority.LOW;
    private int lastComparator;
    private Component customName;
    private float previousRotation;
    private float rotation;

    private BigInteger lastSyncedPower;
    private BigInteger lastSyncedDelta;
    private short lastSyncedLow = -1;
    private short lastSyncedHigh = -1;
    private ConnectionPriority lastSyncedPriority;

    public FensuBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_BATTERY_REDD.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, FensuBlockEntity fensu) {
        if (level.isClientSide) fensu.clientTick();
        else fensu.serverTick((ServerLevel) level, state);
    }

    private void serverTick(ServerLevel level, BlockState state) {
        BigInteger previousPower = power;
        ensureNode(level);
        updateNetworkMode();
        dischargeInputBattery();
        chargeOutputBattery();

        BigInteger average = power.add(previousPower).divide(BigInteger.TWO);
        delta = average.subtract(log[0] == null ? BigInteger.ZERO : log[0]);
        System.arraycopy(log, 1, log, 0, log.length - 1);
        log[log.length - 1] = average;

        int comparator = comparatorOutput();
        if (comparator != lastComparator) {
            for (BlockPos port : FensuBlock.portPositions(worldPosition, facing())) {
                level.updateNeighbourForOutputSignal(port, state.getBlock());
            }
            lastComparator = comparator;
        }
        setChanged();
        syncIfChanged(level, state);
    }

    private void clientTick() {
        previousRotation = rotation;
        rotation += speed();
        if (rotation >= 360F) {
            rotation -= 360F;
            previousRotation -= 360F;
        }
    }

    private void dischargeInputBattery() {
        ItemStack stack = items.get(0);
        if (stack.is(ModItems.BATTERY_CREATIVE.get())) {
            power = power.add(BigInteger.valueOf(MAX_TRANSFER));
            return;
        }
        if (!(stack.getItem() instanceof HeBatteryItem battery)) return;
        long amount = Math.min(battery.getDischargeRate(stack), battery.getCharge(stack));
        if (amount <= 0L) return;
        battery.discharge(stack, amount);
        power = power.add(BigInteger.valueOf(amount));
    }

    private void chargeOutputBattery() {
        ItemStack stack = items.get(1);
        long available = getPower();
        if (available <= 0L || !(stack.getItem() instanceof HeBatteryItem battery)) return;
        long amount;
        if (stack.is(ModItems.BATTERY_CREATIVE.get())) {
            amount = available;
        } else {
            amount = Math.min(Math.min(available, battery.getChargeRate(stack)),
                    Math.max(battery.getMaxCharge(stack) - battery.getCharge(stack), 0L));
            if (amount > 0L) battery.charge(stack, amount);
        }
        if (amount > 0L) power = power.subtract(BigInteger.valueOf(amount));
    }

    private void ensureNode(ServerLevel level) {
        if (node != null && !node.expired()) return;
        HeNetworkManager manager = HeNetworkManager.get(level);
        BlockPos[] ports = FensuBlock.portPositions(worldPosition, facing());
        node = manager.getNode(ports[0]);
        if (node == null || node.expired()) {
            HeNodeConnection[] connections = new HeNodeConnection[ports.length];
            for (int index = 0; index < ports.length; index++) {
                Direction outward = outwardDirection(index);
                connections[index] = new HeNodeConnection(ports[index].relative(outward), outward);
            }
            node = new HeNode(ports, connections);
            manager.createNode(node);
        }
    }

    private void updateNetworkMode() {
        if (node == null || !node.hasValidNetwork()) return;
        HeNetwork network = node.network();
        switch (relevantMode()) {
            case MODE_INPUT -> {
                network.removeProvider(this);
                network.addReceiver(this);
            }
            case MODE_BUFFER -> {
                network.addProvider(this);
                network.addReceiver(this);
            }
            case MODE_OUTPUT -> {
                network.addProvider(this);
                network.removeReceiver(this);
            }
            default -> {
                network.removeProvider(this);
                network.removeReceiver(this);
            }
        }
    }

    private Direction outwardDirection(int port) {
        Direction facing = facing();
        Direction side = facing.getClockWise();
        return switch (port) {
            case 0, 1 -> facing;
            case 2, 3 -> facing.getOpposite();
            case 4 -> side;
            default -> side.getOpposite();
        };
    }

    private Direction facing() {
        return getBlockState().getValue(FensuBlock.FACING);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) ensureNode(server);
    }

    @Override
    public void onChunkUnloaded() {
        // The multi-block UNINOS node survives chunk unload.
    }

    public void removeEnergyNode(ServerLevel level) {
        if (node != null) HeNetworkManager.get(level).destroyNode(node);
        else HeNetworkManager.get(level).destroyNode(FensuBlock.portPositions(worldPosition, facing())[0]);
        node = null;
    }

    public int relevantMode() {
        if (level == null) return redLow;
        for (BlockPos port : FensuBlock.portPositions(worldPosition, facing())) {
            if (level.hasNeighborSignal(port)) return redHigh;
        }
        return redLow;
    }

    public int comparatorOutput() {
        double fraction = (double) getPower() / Math.max(getMaxPower(), 1L) * 15.0D;
        return Mth.clamp((int) Math.round(fraction), 0, 15);
    }

    public void cycleLowMode() {
        redLow = (short) ((redLow + 1) & 3);
        settingsChanged();
    }

    public void cycleHighMode() {
        redHigh = (short) ((redHigh + 1) & 3);
        settingsChanged();
    }

    public void cyclePriority() {
        priority = switch (priority) {
            case LOW -> ConnectionPriority.NORMAL;
            case NORMAL -> ConnectionPriority.HIGH;
            default -> ConnectionPriority.LOW;
        };
        settingsChanged();
    }

    private void settingsChanged() {
        setChanged();
        if (level instanceof ServerLevel server) {
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void syncIfChanged(ServerLevel level, BlockState state) {
        if (!power.equals(lastSyncedPower) || !delta.equals(lastSyncedDelta)
                || redLow != lastSyncedLow || redHigh != lastSyncedHigh || priority != lastSyncedPriority) {
            lastSyncedPower = power;
            lastSyncedDelta = delta;
            lastSyncedLow = redLow;
            lastSyncedHigh = redHigh;
            lastSyncedPriority = priority;
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override public long getPower() { return power.min(PROVISION_LIMIT).max(BigInteger.ZERO).longValue(); }
    @Override public void setPower(long value) { }
    @Override public long getMaxPower() { return MAX_TRANSFER; }
    @Override public long getProviderSpeed() { return MAX_TRANSFER; }
    @Override public long getReceiverSpeed() { return MAX_TRANSFER; }
    @Override public boolean allowDirectProvision() { return false; }
    @Override public ConnectionPriority getPriority() { return priority; }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }

    @Override
    public void usePower(long amount) {
        if (amount <= 0L) return;
        power = power.subtract(BigInteger.valueOf(amount)).max(BigInteger.ZERO);
    }

    @Override
    public long transferPower(long amount) {
        if (amount > 0L) power = power.add(BigInteger.valueOf(amount));
        return 0L;
    }

    public BigInteger storedPower() { return power; }
    public BigInteger delta() { return delta; }
    public int lowMode() { return redLow; }
    public int highMode() { return redHigh; }
    public float rotation(float partialTick) { return previousRotation + (rotation - previousRotation) * partialTick; }

    public float speed() {
        double logarithm = Math.log(power.doubleValue() * 0.05D + 1.0D) * 0.05D;
        return (float) Math.min(Math.pow(logarithm, 5.0D), 15.0D);
    }

    public ItemStack machineDrop() {
        ItemStack stack = new ItemStack(ModItems.MACHINE_BATTERY_REDD_ITEM.get());
        if (customName != null) stack.set(DataComponents.CUSTOM_NAME, customName);
        if (power.signum() == 0) return stack;
        CompoundTag data = new CompoundTag();
        data.putByteArray(ITEM_POWER, power.toByteArray());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    public void restoreFromItem(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains(ITEM_POWER)) return;
        byte[] bytes = data.getByteArray(ITEM_POWER);
        power = bytes.length == 0 ? BigInteger.ZERO : new BigInteger(bytes).max(BigInteger.ZERO);
        setChanged();
    }

    public void setCustomName(@Nullable Component name) { customName = name; setChanged(); }

    @Override
    public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.batteryREDD");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new FensuMenu(id, inventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveState(tag, registries, false);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadState(tag, registries, false);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveState(tag, registries, true);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadState(tag, registries, true);
    }

    private void saveState(CompoundTag tag, HolderLookup.Provider registries, boolean includeDelta) {
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putByteArray("power", power.toByteArray());
        if (includeDelta) tag.putByteArray("delta", delta.toByteArray());
        tag.putShort("redLow", redLow);
        tag.putShort("redHigh", redHigh);
        tag.putByte("priority", (byte) priority.ordinal());
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    private void loadState(CompoundTag tag, HolderLookup.Provider registries, boolean includeDelta) {
        ContainerHelper.loadAllItems(tag, items, registries);
        power = positiveBigInteger(tag.getByteArray("power"));
        if (includeDelta) delta = signedBigInteger(tag.getByteArray("delta"));
        redLow = tag.contains("redLow") ? (short) Mth.clamp(tag.getShort("redLow"), MODE_INPUT, MODE_NONE)
                : MODE_INPUT;
        redHigh = tag.contains("redHigh") ? (short) Mth.clamp(tag.getShort("redHigh"), MODE_INPUT, MODE_NONE)
                : MODE_OUTPUT;
        int ordinal = Mth.clamp(tag.getByte("priority"), ConnectionPriority.LOW.ordinal(),
                ConnectionPriority.HIGH.ordinal());
        priority = ConnectionPriority.values()[ordinal];
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    private static BigInteger positiveBigInteger(byte[] bytes) {
        return bytes.length == 0 ? BigInteger.ZERO : new BigInteger(bytes).max(BigInteger.ZERO);
    }

    private static BigInteger signedBigInteger(byte[] bytes) {
        return bytes.length == 0 ? BigInteger.ZERO : new BigInteger(bytes);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) settingsChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > 1) stack.setCount(1);
        settingsChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) <= 128.0D;
    }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return stack.getItem() instanceof HeBatteryItem; }
    @Override public void clearContent() { items.clear(); settingsChanged(); }
    @Override public int[] getSlotsForFace(Direction side) { return NO_SIDED_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }

    public AABB renderBounds() {
        return new AABB(worldPosition.getX() - 4, worldPosition.getY(), worldPosition.getZ() - 4,
                worldPosition.getX() + 5, worldPosition.getY() + 10, worldPosition.getZ() + 5);
    }
}
