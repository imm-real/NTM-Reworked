package com.hbm.ntm.blockentity;

import com.hbm.ntm.energy.HeReceiver;
import com.hbm.ntm.inventory.CentrifugeMenu;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.item.MachineUpgradeItem;
import com.hbm.ntm.recipe.CentrifugeRecipes;
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
import org.jetbrains.annotations.Nullable;

/** Source solid Centrifuge: one input, four atomic outputs, two upgrades, and HE power. */
public final class CentrifugeBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, HeReceiver {
    public static final int INPUT = 0;
    public static final int BATTERY = 1;
    public static final int OUTPUT_START = 2;
    public static final int OUTPUT_END = 6;
    public static final int UPGRADE_START = 6;
    public static final int UPGRADE_END = 8;
    public static final int SLOT_COUNT = 8;
    public static final long MAX_POWER = 100_000L;
    public static final int PROCESSING_SPEED = 200;
    public static final int BASE_CONSUMPTION = 200;
    private static final int[] AUTOMATION_SLOTS = {INPUT, 2, 3, 4, 5};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> (int) power;
                case 2 -> (int) (power >>> 32);
                case 3 -> active ? 1 : 0;
                default -> 0;
            };
        }

        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = value;
                case 1 -> power = power & 0xFFFFFFFF00000000L | value & 0xFFFFFFFFL;
                case 2 -> power = power & 0xFFFFFFFFL | (long) value << 32;
                case 3 -> active = value != 0;
                default -> { }
            }
        }

        @Override public int getCount() { return 4; }
    };

    private Component customName;
    private long power;
    private int progress;
    private boolean active;
    private long lastSyncedPower = Long.MIN_VALUE;
    private int lastSyncedProgress = Integer.MIN_VALUE;
    private boolean lastSyncedActive;

    public CentrifugeBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_CENTRIFUGE.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, CentrifugeBlockEntity centrifuge) {
        if (!level.isClientSide) centrifuge.serverTick((ServerLevel) level, position, state);
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        for (Direction direction : Direction.values()) {
            trySubscribe(level, position.relative(direction), direction);
        }
        dischargeBattery();

        int speedLevel = upgradeLevel(MachineUpgradeItem.Type.SPEED);
        int powerLevel = upgradeLevel(MachineUpgradeItem.Type.POWER);
        int overdriveLevel = upgradeLevel(MachineUpgradeItem.Type.OVERDRIVE);
        int consumption = BASE_CONSUMPTION + speedLevel * BASE_CONSUMPTION;
        int speed = 1 + speedLevel;
        speed *= 1 + overdriveLevel * 5;
        consumption += overdriveLevel * BASE_CONSUMPTION * 50;
        consumption /= 1 + powerLevel;

        // First progress tick is free; every later tick attempts the full draw and clamps at zero.
        if (hasPower() && isProcessing()) power = Math.max(0L, power - consumption);

        active = hasPower() && canProcess();
        if (active) {
            progress += speed;
            if (progress >= PROCESSING_SPEED) {
                progress = 0;
                processItem();
            }
        } else {
            progress = 0;
        }

        if (power != lastSyncedPower || progress != lastSyncedProgress || active != lastSyncedActive
                || level.getGameTime() % 50L == 0L) {
            lastSyncedPower = power;
            lastSyncedProgress = progress;
            lastSyncedActive = active;
            level.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS);
        }
        setChanged();
    }

    private void dischargeBattery() {
        ItemStack stack = items.get(BATTERY);
        if (!(stack.getItem() instanceof HeBatteryItem battery)) return;
        long amount = Math.min(Math.min(MAX_POWER - power, battery.getDischargeRate(stack)),
                battery.getCharge(stack));
        if (amount > 0L) {
            battery.discharge(stack, amount);
            power += amount;
        }
    }

    private int upgradeLevel(MachineUpgradeItem.Type type) {
        int level = 0;
        for (int slot = UPGRADE_START; slot < UPGRADE_END; slot++) {
            if (items.get(slot).getItem() instanceof MachineUpgradeItem upgrade && upgrade.type() == type) {
                level += upgrade.level();
            }
        }
        return Math.min(level, 3);
    }

    public boolean canProcess() {
        ItemStack[] outputs = CentrifugeRecipes.getOutput(items.get(INPUT));
        if (outputs == null) return false;
        for (int index = 0; index < Math.min(4, outputs.length); index++) {
            ItemStack produced = outputs[index];
            if (produced.isEmpty()) continue;
            ItemStack existing = items.get(OUTPUT_START + index);
            if (existing.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(existing, produced)
                    || existing.getCount() + produced.getCount() > produced.getMaxStackSize()) return false;
        }
        return true;
    }

    private void processItem() {
        ItemStack[] outputs = CentrifugeRecipes.getOutput(items.get(INPUT));
        if (outputs == null) return;
        for (int index = 0; index < Math.min(4, outputs.length); index++) {
            ItemStack produced = outputs[index];
            if (produced.isEmpty()) continue;
            int slot = OUTPUT_START + index;
            if (items.get(slot).isEmpty()) items.set(slot, produced.copy());
            else items.get(slot).grow(produced.getCount());
        }
        removeItem(INPUT, 1);
    }

    public boolean hasPower() { return power > 0L; }
    public boolean isProcessing() { return progress > 0; }
    public boolean active() { return active; }
    public int progress() { return progress; }
    public int consumption() {
        int speed = upgradeLevel(MachineUpgradeItem.Type.SPEED);
        int overdrive = upgradeLevel(MachineUpgradeItem.Type.OVERDRIVE);
        int result = BASE_CONSUMPTION + speed * BASE_CONSUMPTION
                + overdrive * BASE_CONSUMPTION * 50;
        return result / (1 + upgradeLevel(MachineUpgradeItem.Type.POWER));
    }

    @Override public long getPower() { return power; }
    @Override public void setPower(long power) { this.power = Math.max(0L, Math.min(power, MAX_POWER)); }
    @Override public long getMaxPower() { return MAX_POWER; }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putLong("power", power);
        tag.putInt("progress", progress);
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        power = Math.max(0L, Math.min(tag.getLong("power"), MAX_POWER));
        progress = tag.getInt("progress");
        customName = tag.contains("name")
                ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("power", power);
        tag.putInt("progress", progress);
        tag.putBoolean("active", active);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        power = tag.getLong("power");
        progress = tag.getInt("progress");
        active = tag.getBoolean("active");
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.centrifuge");
    }

    public void setCustomName(Component customName) {
        this.customName = customName;
        setChanged();
    }

    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CentrifugeMenu(id, inventory, this, data);
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getCenter()) <= 64D;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == INPUT) return true;
        if (slot == BATTERY) return stack.getItem() instanceof HeBatteryItem;
        return slot >= UPGRADE_START && slot < UPGRADE_END
                && stack.getItem() instanceof MachineUpgradeItem;
    }

    @Override public int[] getSlotsForFace(Direction side) { return AUTOMATION_SLOTS.clone(); }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot == INPUT;
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= OUTPUT_START && slot < OUTPUT_END;
    }
}
