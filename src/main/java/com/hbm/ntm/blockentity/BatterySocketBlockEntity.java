package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.BatterySocketBlock;
import com.hbm.ntm.energy.HeConductor;
import com.hbm.ntm.energy.HeNetwork;
import com.hbm.ntm.energy.HeNetworkManager;
import com.hbm.ntm.energy.HeNode;
import com.hbm.ntm.energy.HeNodeConnection;
import com.hbm.ntm.energy.HeProvider;
import com.hbm.ntm.energy.HeReceiver;
import com.hbm.ntm.inventory.BatterySocketMenu;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
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
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public final class BatterySocketBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, HeConductor, HeProvider, HeReceiver {
    public static final int MODE_INPUT = 0;
    public static final int MODE_BUFFER = 1;
    public static final int MODE_OUTPUT = 2;
    public static final int MODE_NONE = 3;
    private static final int[] SLOT = {0};

    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private final long[] log = new long[20];
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> lowBits(getPower());
                case 1 -> highBits(getPower());
                case 2 -> lowBits(getMaxPower());
                case 3 -> highBits(getMaxPower());
                case 4 -> lowBits(delta);
                case 5 -> highBits(delta);
                case 6 -> redLow;
                case 7 -> redHigh;
                case 8 -> priority.ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return BatterySocketMenu.DATA_COUNT;
        }
    };

    private HeNode node;
    private short redLow = MODE_INPUT;
    private short redHigh = MODE_OUTPUT;
    private ConnectionPriority priority = ConnectionPriority.LOW;
    private long delta;
    private int lastComparator;
    private long lastSyncedPower = Long.MIN_VALUE;
    private Component customName;

    public BatterySocketBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.BATTERY_SOCKET.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, BatterySocketBlockEntity socket) {
        if (level instanceof ServerLevel serverLevel) {
            socket.serverTick(serverLevel, position, state);
        }
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        long previousPower = getPower();
        ensureNode(level);
        updateNetworkMode();

        long average = (getPower() + previousPower) / 2L;
        delta = average - log[0];
        System.arraycopy(log, 1, log, 0, log.length - 1);
        log[log.length - 1] = average;

        int comparator = comparatorOutput();
        if (comparator != lastComparator) {
            for (BlockPos part : BatterySocketBlock.partPositions(position, state.getValue(BatterySocketBlock.FACING))) {
                level.updateNeighbourForOutputSignal(part, state.getBlock());
            }
            lastComparator = comparator;
        }

        long power = getPower();
        if (power != lastSyncedPower || level.getGameTime() % 20L == 0L) {
            lastSyncedPower = power;
            level.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS);
        }
        setChanged();
    }

    private void ensureNode(ServerLevel level) {
        if (node != null && !node.expired()) return;
        HeNetworkManager manager = HeNetworkManager.get(level);
        node = manager.getNode(worldPosition);
        if (node == null || node.expired()) {
            node = createSocketNode();
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

    private HeNode createSocketNode() {
        Direction facing = getBlockState().getValue(BatterySocketBlock.FACING);
        Direction clockwise = facing.getClockWise();
        BlockPos core = worldPosition;
        BlockPos back = core.relative(facing.getOpposite());
        BlockPos side = core.relative(clockwise);
        BlockPos backSide = back.relative(clockwise);
        return new HeNode(
                new BlockPos[]{core, back, side, backSide},
                new HeNodeConnection[]{
                        new HeNodeConnection(core.relative(facing), facing),
                        new HeNodeConnection(side.relative(facing), facing),
                        new HeNodeConnection(back.relative(facing.getOpposite()), facing.getOpposite()),
                        new HeNodeConnection(backSide.relative(facing.getOpposite()), facing.getOpposite()),
                        new HeNodeConnection(side.relative(clockwise), clockwise),
                        new HeNodeConnection(backSide.relative(clockwise), clockwise),
                        new HeNodeConnection(core.relative(clockwise.getOpposite()), clockwise.getOpposite()),
                        new HeNodeConnection(back.relative(clockwise.getOpposite()), clockwise.getOpposite())
                }
        );
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) ensureNode(serverLevel);
    }

    @Override
    public void onChunkUnloaded() {
        // UNINOS nodes survive chunk unload. They have abandonment issues.
    }

    public int relevantMode() {
        if (level == null) return redLow;
        Direction facing = getBlockState().getValue(BatterySocketBlock.FACING);
        for (BlockPos part : BatterySocketBlock.partPositions(worldPosition, facing)) {
            if (level.hasNeighborSignal(part)) return redHigh;
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
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public long getPower() {
        ItemStack stack = items.getFirst();
        return stack.getItem() instanceof HeBatteryItem battery ? battery.getCharge(stack) : 0L;
    }

    @Override
    public void setPower(long power) {
        ItemStack stack = items.getFirst();
        if (stack.getItem() instanceof HeBatteryItem battery) battery.setCharge(stack, power);
    }

    @Override
    public long getMaxPower() {
        ItemStack stack = items.getFirst();
        return stack.getItem() instanceof HeBatteryItem battery ? battery.getMaxCharge(stack) : 0L;
    }

    @Override
    public long getProviderSpeed() {
        ItemStack stack = items.getFirst();
        if (!(stack.getItem() instanceof HeBatteryItem battery)) return 0L;
        int mode = relevantMode();
        return mode == MODE_OUTPUT || mode == MODE_BUFFER ? battery.getDischargeRate(stack) : 0L;
    }

    @Override
    public long getReceiverSpeed() {
        ItemStack stack = items.getFirst();
        if (!(stack.getItem() instanceof HeBatteryItem battery)) return 0L;
        int mode = relevantMode();
        return mode == MODE_INPUT || mode == MODE_BUFFER ? battery.getChargeRate(stack) : 0L;
    }

    @Override
    public boolean allowDirectProvision() {
        return false;
    }

    @Override
    public ConnectionPriority getPriority() {
        return priority;
    }

    @Override
    public boolean canConnect(Direction side) {
        return side != null && BatterySocketBlock.canConnectAt(getBlockState(), side);
    }

    @Override
    public boolean isHeLoaded() {
        return hasLevel() && !isRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveState(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadState(tag, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveState(tag, registries);
        tag.putLong("delta", delta);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadState(tag, registries);
        delta = tag.getLong("delta");
    }

    private void saveState(CompoundTag tag, HolderLookup.Provider registries) {
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putShort("redLow", redLow);
        tag.putShort("redHigh", redHigh);
        tag.putByte("priority", (byte) priority.ordinal());
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    private void loadState(CompoundTag tag, HolderLookup.Provider registries) {
        ContainerHelper.loadAllItems(tag, items, registries);
        redLow = tag.contains("redLow") ? tag.getShort("redLow") : MODE_INPUT;
        redHigh = tag.contains("redHigh") ? tag.getShort("redHigh") : MODE_OUTPUT;
        int ordinal = Mth.clamp(tag.getByte("priority"), ConnectionPriority.LOW.ordinal(), ConnectionPriority.HIGH.ordinal());
        priority = ConnectionPriority.values()[ordinal];
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.batterySocket");
    }

    public void setCustomName(@Nullable Component name) {
        customName = name;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new BatterySocketMenu(containerId, inventory, this, data);
    }

    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return items.getFirst().isEmpty(); }
    @Override public ItemStack getItem(int slot) { return items.getFirst(); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, 0, amount);
        if (!removed.isEmpty()) settingsChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, 0);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(0, stack);
        if (stack.getCount() > 1) stack.setCount(1);
        settingsChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.getItem() instanceof HeBatteryItem;
    }

    @Override
    public void clearContent() {
        items.set(0, ItemStack.EMPTY);
        settingsChanged();
    }

    @Override public int[] getSlotsForFace(Direction direction) { return SLOT; }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        if (!(stack.getItem() instanceof HeBatteryItem battery)) return false;
        // Slot zero is compared to mode constants and only passes the input/full branch.
        if (slot == MODE_OUTPUT && battery.getCharge(stack) == 0L) return true;
        return slot == MODE_INPUT && battery.getCharge(stack) == battery.getMaxCharge(stack);
    }

    public long delta() { return delta; }
    public int lowMode() { return redLow; }
    public int highMode() { return redHigh; }
    public AABB renderBounds() { return new AABB(worldPosition.getX() - 1, worldPosition.getY(), worldPosition.getZ() - 1,
            worldPosition.getX() + 2, worldPosition.getY() + 2, worldPosition.getZ() + 2); }

    private static int lowBits(long value) { return (int) value; }
    private static int highBits(long value) { return (int) (value >>> 32); }
}
