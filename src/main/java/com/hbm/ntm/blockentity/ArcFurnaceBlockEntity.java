package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.ArcFurnaceBlock;
import com.hbm.ntm.energy.HeReceiver;
import com.hbm.ntm.inventory.ArcFurnaceMenu;
import com.hbm.ntm.item.ArcElectrodeItem;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.item.MachineUpgradeItem;
import com.hbm.ntm.pollution.PollutionData;
import com.hbm.ntm.recipe.ArcFurnaceRecipes;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
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
import net.minecraft.sounds.SoundSource;
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

import java.util.Arrays;

/** Source solid-mode Electric Arc Furnace batch processor. */
public final class ArcFurnaceBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, HeReceiver {
    public static final int ELECTRODE_START = 0;
    public static final int ELECTRODE_END = 3;
    public static final int BATTERY = 3;
    public static final int UPGRADE = 4;
    public static final int GRID_START = 5;
    public static final int GRID_END = 25;
    public static final int QUEUE_START = 25;
    public static final int QUEUE_END = 30;
    public static final int SLOT_COUNT = 30;
    public static final long MAX_POWER = 2_500_000L;
    public static final byte ELECTRODE_NONE = 0;
    public static final byte ELECTRODE_FRESH = 1;
    public static final byte ELECTRODE_USED = 2;
    public static final byte ELECTRODE_DEPLETED = 3;
    private static final int[] ACCESSIBLE_SLOTS = accessibleSlots();

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final byte[] electrodes = new byte[3];
    private long power;
    private float progress;
    private float lid;
    private float previousLid;
    private float targetLid;
    private int lidApproach;
    private int delay;
    private boolean progressing;
    private boolean hasMaterial;
    private Component customName;

    private long lastSyncedPower = Long.MIN_VALUE;
    private float lastSyncedProgress = Float.NaN;
    private float lastSyncedLid = Float.NaN;
    private boolean lastSyncedProgressing;
    private boolean lastSyncedMaterial;
    private final byte[] lastSyncedElectrodes = {-1, -1, -1};

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> Math.round(progress * 10_000F);
                case 1 -> Math.round(lid * 10_000F);
                case 2 -> (int) power;
                case 3 -> (int) (power >>> 32);
                case 4 -> (int) MAX_POWER;
                case 5 -> (int) (MAX_POWER >>> 32);
                case 6 -> progressing ? 1 : 0;
                case 7 -> hasMaterial ? 1 : 0;
                case 8, 9, 10 -> electrodes[index - 8];
                case 11 -> delay;
                default -> 0;
            };
        }

        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = value / 10_000F;
                case 1 -> lid = value / 10_000F;
                case 2 -> power = power & 0xFFFFFFFF00000000L | value & 0xFFFFFFFFL;
                case 3 -> power = power & 0xFFFFFFFFL | (long) value << 32;
                case 6 -> progressing = value != 0;
                case 7 -> hasMaterial = value != 0;
                case 8, 9, 10 -> electrodes[index - 8] = (byte) value;
                case 11 -> delay = value;
                default -> { }
            }
        }

        @Override public int getCount() { return 12; }
    };

    public ArcFurnaceBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_ARC_FURNACE.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, ArcFurnaceBlockEntity furnace) {
        if (level.isClientSide) furnace.clientTick();
        else furnace.serverTick((ServerLevel) level, position, state);
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        float lidBefore = lid;
        power = getPower();
        dischargeBattery();
        if (level.getGameTime() % 20L == 0L) subscribeConnections(level, position, state);
        if (lid > 0F) loadIngredients();

        progressing = false;
        if (power > 0L) {
            boolean ingredients = hasIngredients();
            boolean validElectrodes = hasElectrodes();
            int speed = speedLevel();
            long consumption = 1_000L * pow5(speed);

            if (ingredients && validElectrodes && delay <= 0) {
                if (lid > 0F) {
                    lid = Math.max(0F, lid - lidStep(speed));
                    progress = 0F;
                } else if (power >= consumption) {
                    progress += 1F / processDuration(speed);
                    progressing = true;
                    power -= consumption;
                    if (progress >= 1F) {
                        processBatch();
                        progress = 0F;
                        delay = cooldown(speed);
                        PollutionData.get(level).increment(position, PollutionData.Type.SOOT, 10F);
                    }
                }
            } else {
                if (delay > 0) delay--;
                progress = 0F;
                if (lid < 1F) lid = Math.min(1F, lid + lidStep(speed));
            }
            hasMaterial = ingredients;
        }

        updateElectrodeStates();
        if (!hasMaterial) hasMaterial = hasIngredients();
        playLidSounds(level, position, lidBefore);
        setChanged();
        syncIfChanged(level, position, state);
    }

    private void clientTick() {
        previousLid = lid;
        if (lidApproach > 0) {
            lid += (targetLid - lid) / lidApproach;
            lidApproach--;
        } else lid = targetLid;
    }

    private void dischargeBattery() {
        ItemStack stack = items.get(BATTERY);
        if (!(stack.getItem() instanceof HeBatteryItem battery)) return;
        long amount = Math.min(Math.min(Math.max(MAX_POWER - power, 0L), battery.getDischargeRate(stack)),
                battery.getCharge(stack));
        if (amount > 0L) {
            battery.discharge(stack, amount);
            power += amount;
        }
    }

    private void subscribeConnections(ServerLevel level, BlockPos core, BlockState state) {
        Direction facing = state.getValue(ArcFurnaceBlock.FACING);
        Direction side = facing.getClockWise();
        BlockPos[] targets = {
                core.relative(facing, 3).relative(side), core.relative(facing, 3).relative(side.getOpposite()),
                core.relative(side, 3).relative(facing), core.relative(side, 3).relative(facing.getOpposite()),
                core.relative(side.getOpposite(), 3).relative(facing),
                core.relative(side.getOpposite(), 3).relative(facing.getOpposite())
        };
        Direction[] directions = {facing, facing, side, side, side.getOpposite(), side.getOpposite()};
        for (int index = 0; index < targets.length; index++) trySubscribe(level, targets[index], directions[index]);
    }

    private void loadIngredients() {
        for (int queue = QUEUE_START; queue < QUEUE_END; queue++) {
            ItemStack source = items.get(queue);
            ArcFurnaceRecipes.Recipe recipe = ArcFurnaceRecipes.find(source);
            if (source.isEmpty() || recipe == null) continue;
            int max = Math.min(maxInputSize(), source.getMaxStackSize() / recipe.output().getCount());

            for (int slot = GRID_START; slot < GRID_END && !source.isEmpty(); slot++) {
                ItemStack target = items.get(slot);
                if (target.isEmpty() || !ItemStack.isSameItemSameComponents(source, target)) continue;
                int move = Math.min(source.getCount(), Math.min(target.getMaxStackSize() - target.getCount(),
                        max - target.getCount()));
                if (move > 0) {
                    target.grow(move);
                    source.shrink(move);
                }
            }
            for (int slot = GRID_START; slot < GRID_END && !source.isEmpty(); slot++) {
                if (!items.get(slot).isEmpty()) continue;
                int move = Math.min(max, source.getCount());
                items.set(slot, source.copyWithCount(move));
                source.shrink(move);
            }
            if (source.isEmpty()) items.set(queue, ItemStack.EMPTY);
        }
    }

    private void processBatch() {
        for (int slot = GRID_START; slot < GRID_END; slot++) {
            ItemStack input = items.get(slot);
            ArcFurnaceRecipes.Recipe recipe = ArcFurnaceRecipes.find(input);
            if (recipe == null) continue;
            ItemStack output = recipe.output();
            output.setCount(output.getCount() * input.getCount());
            items.set(slot, output);
        }
        for (int slot = ELECTRODE_START; slot < ELECTRODE_END; slot++) {
            ItemStack electrode = items.get(slot);
            if (electrode.getItem() instanceof ArcElectrodeItem && ArcElectrodeItem.damage(electrode)) {
                items.set(slot, ArcElectrodeItem.burnt(ModItems.ARC_ELECTRODE_BURNT.get(), electrode));
            }
        }
    }

    public boolean hasIngredients() {
        for (int slot = GRID_START; slot < GRID_END; slot++) {
            if (ArcFurnaceRecipes.find(items.get(slot)) != null) return true;
        }
        return false;
    }

    public boolean hasElectrodes() {
        for (int slot = ELECTRODE_START; slot < ELECTRODE_END; slot++) {
            if (!(items.get(slot).getItem() instanceof ArcElectrodeItem)) return false;
        }
        return true;
    }

    private void updateElectrodeStates() {
        for (int slot = ELECTRODE_START; slot < ELECTRODE_END; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.is(ModItems.ARC_ELECTRODE_BURNT.get())) electrodes[slot] = ELECTRODE_DEPLETED;
            else if (stack.getItem() instanceof ArcElectrodeItem) {
                electrodes[slot] = progressing || ArcElectrodeItem.durability(stack) > 0
                        ? ELECTRODE_USED : ELECTRODE_FRESH;
            } else electrodes[slot] = ELECTRODE_NONE;
        }
    }

    private int speedLevel() {
        ItemStack stack = items.get(UPGRADE);
        return stack.getItem() instanceof MachineUpgradeItem upgrade
                && upgrade.type() == MachineUpgradeItem.Type.SPEED ? Math.min(upgrade.level(), 3) : 0;
    }

    public int maxInputSize() {
        return switch (speedLevel()) { case 0 -> 1; case 1 -> 4; case 2 -> 8; default -> 16; };
    }

    private static long pow5(int exponent) {
        long result = 1L;
        for (int i = 0; i < exponent; i++) result *= 5L;
        return result;
    }

    private static int processDuration(int speed) { return 400 / (speed * 2 + 1); }
    private static int cooldown(int speed) { return (int) (120D / (speed * 0.5D + 1D)); }
    private static float lidStep(int speed) { return (float) ((speed * 0.5D + 1D) / 60D); }

    private void playLidSounds(ServerLevel level, BlockPos position, float before) {
        if (lid == before) return;
        boolean starting = before == 0F || before == 1F;
        boolean stopping = lid == 0F || lid == 1F;
        if (starting) level.playSound(null, position, ModSounds.ARC_FURNACE_LID_START.get(),
                SoundSource.BLOCKS, 0.75F, 1F);
        if (stopping) level.playSound(null, position, ModSounds.ARC_FURNACE_LID_STOP.get(),
                SoundSource.BLOCKS, 1F, 1F);
    }

    private void syncIfChanged(ServerLevel level, BlockPos position, BlockState state) {
        if (power != lastSyncedPower || progress != lastSyncedProgress || lid != lastSyncedLid
                || progressing != lastSyncedProgressing || hasMaterial != lastSyncedMaterial
                || !Arrays.equals(electrodes, lastSyncedElectrodes)) {
            lastSyncedPower = power;
            lastSyncedProgress = progress;
            lastSyncedLid = lid;
            lastSyncedProgressing = progressing;
            lastSyncedMaterial = hasMaterial;
            System.arraycopy(electrodes, 0, lastSyncedElectrodes, 0, electrodes.length);
            level.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public float lid(float partialTick) { return previousLid + (lid - previousLid) * partialTick; }
    public float progress() { return progress; }
    public boolean progressing() { return progressing; }
    public boolean hasMaterial() { return hasMaterial; }
    public byte electrodeState(int index) { return electrodes[index]; }
    public ContainerData dataAccess() { return data; }
    public int delay() { return delay; }
    public void setProgressForTest(float value) { progress = value; }
    public void setLidForTest(float value) { lid = value; targetLid = value; }
    public void setDelayForTest(int value) { delay = value; }

    @Override public long getPower() { return Math.max(0L, Math.min(power, MAX_POWER)); }
    @Override public void setPower(long value) { power = value; }
    @Override public long getMaxPower() { return MAX_POWER; }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }

    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.machineArcFurnaceLarge");
    }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ArcFurnaceMenu(id, inventory, this, data);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putLong("power", power);
        tag.putFloat("progress", progress);
        tag.putFloat("lid", lid);
        tag.putInt("delay", delay);
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        power = tag.getLong("power");
        progress = tag.getFloat("progress");
        lid = tag.getFloat("lid");
        targetLid = lid;
        delay = tag.getInt("delay");
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
        updateElectrodeStates();
        hasMaterial = hasIngredients();
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("power", power);
        tag.putFloat("progress", progress);
        tag.putFloat("lid", lid);
        tag.putBoolean("progressing", progressing);
        tag.putBoolean("hasMaterial", hasMaterial);
        tag.putByteArray("electrodes", electrodes);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        power = tag.getLong("power");
        progress = tag.getFloat("progress");
        targetLid = tag.getFloat("lid");
        if (targetLid != 0F && targetLid != 1F) lidApproach = 2;
        progressing = tag.getBoolean("progressing");
        hasMaterial = tag.getBoolean("hasMaterial");
        byte[] states = tag.getByteArray("electrodes");
        System.arraycopy(states, 0, electrodes, 0, Math.min(states.length, electrodes.length));
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack result = ContainerHelper.removeItem(items, slot, count);
        if (!result.isEmpty()) setChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        if (slot == UPGRADE && stack.getItem() instanceof MachineUpgradeItem && level != null) {
            level.playSound(null, worldPosition, ModSounds.UPGRADE_PLUG.get(), SoundSource.BLOCKS, 1F, 1F);
        }
        setChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getCenter()) <= 128D;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot >= ELECTRODE_START && slot < ELECTRODE_END) return stack.getItem() instanceof ArcElectrodeItem;
        if (slot == BATTERY) return stack.getItem() instanceof HeBatteryItem;
        if (slot == UPGRADE) return stack.getItem() instanceof MachineUpgradeItem;
        if (slot >= GRID_START && slot < QUEUE_END) return ArcFurnaceRecipes.find(stack) != null;
        return false;
    }

    @Override public int[] getSlotsForFace(Direction side) { return ACCESSIBLE_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot < ELECTRODE_END && canPlaceItem(slot, stack)
                || slot >= QUEUE_START && slot < QUEUE_END && canPlaceItem(slot, stack);
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (slot < ELECTRODE_END) return lid >= 1F && !(stack.getItem() instanceof ArcElectrodeItem);
        if (slot >= GRID_START && slot < GRID_END) return lid > 0F && ArcFurnaceRecipes.find(stack) == null;
        return slot >= QUEUE_START && ArcFurnaceRecipes.find(stack) == null;
    }

    private static int[] accessibleSlots() {
        int[] slots = new int[3 + (GRID_END - GRID_START) + (QUEUE_END - QUEUE_START)];
        int index = 0;
        for (int slot = 0; slot < 3; slot++) slots[index++] = slot;
        for (int slot = GRID_START; slot < QUEUE_END; slot++) slots[index++] = slot;
        return slots;
    }
}
