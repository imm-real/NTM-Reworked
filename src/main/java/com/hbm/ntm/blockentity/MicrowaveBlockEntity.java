package com.hbm.ntm.blockentity;

import com.hbm.ntm.energy.HeReceiver;
import com.hbm.ntm.inventory.MicrowaveMenu;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.registry.ModBlockEntities;
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
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/** Cooks food, eats HE and punishes metal cutlery. */
public final class MicrowaveBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, HeReceiver {
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    public static final int BATTERY = 2;
    public static final int SLOT_COUNT = 3;
    public static final long MAX_POWER = 50_000L;
    public static final int CONSUMPTION = 50;
    public static final int MAX_TIME = 300;
    public static final int MAX_SPEED = 5;
    public static final int DATA_COUNT = 4;
    private static final int[] INPUT_SLOTS = {INPUT};
    private static final int[] OUTPUT_SLOTS = {OUTPUT};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> time;
                case 1 -> (int) power;
                case 2 -> (int) (power >>> 32);
                case 3 -> speed;
                default -> 0;
            };
        }

        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> time = value;
                case 1 -> power = power & 0xFFFFFFFF00000000L | value & 0xFFFFFFFFL;
                case 2 -> power = power & 0xFFFFFFFFL | (long) value << 32;
                case 3 -> speed = Mth.clamp(value, 0, MAX_SPEED);
                default -> { }
            }
        }

        @Override public int getCount() { return DATA_COUNT; }
    };

    private Component customName;
    private long power;
    private int time;
    private int speed;
    private long lastSyncedPower = Long.MIN_VALUE;
    private int lastSyncedTime = Integer.MIN_VALUE;
    private int lastSyncedSpeed = Integer.MIN_VALUE;

    public MicrowaveBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_MICROWAVE.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, MicrowaveBlockEntity microwave) {
        if (!level.isClientSide) microwave.serverTick((ServerLevel) level, position);
    }

    private void serverTick(ServerLevel level, BlockPos position) {
        // Ask for power every tick. Patience does not heat leftovers.
        for (Direction direction : Direction.values()) {
            trySubscribe(level, position.relative(direction), direction);
        }
        dischargeBattery();

        if (canProcess()) {
            if (speed >= MAX_SPEED) {
                explode(level, position);
                return;
            }
            // Finish the old meal before checking whether another one exists.
            if (time >= MAX_TIME) {
                processItem();
                time = 0;
            }
            if (canProcess()) {
                power -= CONSUMPTION;
                time += speed * 2;
            }
        }

        setChanged();
        syncIfChanged(level, position);
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

    private void explode(ServerLevel level, BlockPos position) {
        // Drop the contents, not the microwave. Warranty successfully voided.
        level.destroyBlock(position, false);
        level.explode(null, position.getX() + 0.5D, position.getY() + 0.5D, position.getZ() + 0.5D,
                5.0F, true, Level.ExplosionInteraction.TNT);
    }

    private void processItem() {
        if (!canProcess()) return;
        ItemStack result = smeltingResult(items.get(INPUT));
        if (items.get(OUTPUT).isEmpty()) items.set(OUTPUT, result.copy());
        else items.get(OUTPUT).grow(result.getCount());
        removeItem(INPUT, 1);
    }

    public boolean canProcess() {
        if (speed == 0 || power < CONSUMPTION || items.get(INPUT).isEmpty()) return false;
        ItemStack result = smeltingResult(items.get(INPUT));
        if (result.isEmpty() || !(isFood(items.get(INPUT)) || isFood(result))) return false;
        ItemStack output = items.get(OUTPUT);
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= result.getMaxStackSize();
    }

    public boolean isSmeltable(ItemStack stack) { return !smeltingResult(stack).isEmpty(); }

    private static boolean isFood(ItemStack stack) { return stack.has(DataComponents.FOOD); }

    private ItemStack smeltingResult(ItemStack input) {
        if (level == null || input.isEmpty()) return ItemStack.EMPTY;
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, recipeInput, level)
                .map(holder -> holder.value().assemble(recipeInput, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    private void syncIfChanged(ServerLevel level, BlockPos position) {
        if (power == lastSyncedPower && time == lastSyncedTime && speed == lastSyncedSpeed) return;
        lastSyncedPower = power;
        lastSyncedTime = time;
        lastSyncedSpeed = speed;
        BlockState state = level.getBlockState(position);
        level.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS);
    }

    public void adjustSpeed(int delta) { setSpeed(speed + delta); }
    public int speed() { return speed; }
    public int time() { return time; }
    public void setSpeed(int value) { speed = Mth.clamp(value, 0, MAX_SPEED); setChanged(); }
    public void setTimeForTest(int value) { time = value; }
    public ContainerData dataAccess() { return data; }
    public AABB renderBounds() { return new AABB(worldPosition).inflate(1.0D); }

    @Override public long getPower() { return power; }
    @Override public void setPower(long value) { power = Math.max(0L, Math.min(value, MAX_POWER)); }
    @Override public long getMaxPower() { return MAX_POWER; }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putLong("power", power);
        tag.putInt("speed", speed);
        // Cooking progress does not survive a reload. Neither do good leftovers.
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        power = Math.max(0L, Math.min(tag.getLong("power"), MAX_POWER));
        speed = Mth.clamp(tag.getInt("speed"), 0, MAX_SPEED);
        time = 0;
        customName = tag.contains("name")
                ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("power", power);
        tag.putInt("time", time);
        tag.putInt("speed", speed);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        power = tag.getLong("power");
        time = tag.getInt("time");
        speed = tag.getInt("speed");
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.microwave");
    }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MicrowaveMenu(id, inventory, this, data);
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
                && player.distanceToSqr(worldPosition.getCenter()) <= 64.0D;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT && isSmeltable(stack);
    }
    @Override public int[] getSlotsForFace(Direction side) {
        return (side == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS).clone();
    }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == OUTPUT;
    }
}
