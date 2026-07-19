package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.MachinePressBlock;
import com.hbm.ntm.inventory.MachinePressMenu;
import com.hbm.ntm.item.StampItem;
import com.hbm.ntm.recipe.PressRecipes;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModBlocks;
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
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class MachinePressBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SLOT_FUEL = 0;
    public static final int SLOT_STAMP = 1;
    public static final int SLOT_INPUT = 2;
    public static final int SLOT_OUTPUT = 3;
    public static final int SLOT_COUNT = 13;
    public static final int MAX_SPEED = 400;
    public static final int PROGRESS_AT_MAX = 25;
    public static final int MAX_PRESS = 200;
    private static final int[] AUTOMATION_SLOTS = {0, 1, 2, 3};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> speed;
                case 1 -> burnTime;
                case 2 -> press;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> speed = value;
                case 1 -> burnTime = value;
                case 2 -> {
                    syncPress = value;
                    turnProgress = 2;
                }
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    private Component customName;
    private int speed;
    private int burnTime;
    private int press;
    private boolean retracting;
    private int delay;

    private double renderPress;
    private double lastPress;
    private int syncPress;
    private int turnProgress;
    private ItemStack renderStack = ItemStack.EMPTY;

    private int lastSyncedSpeed = Integer.MIN_VALUE;
    private int lastSyncedBurnTime = Integer.MIN_VALUE;
    private int lastSyncedPress = Integer.MIN_VALUE;
    private ItemStack lastSyncedInput = ItemStack.EMPTY;

    public MachinePressBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_PRESS.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, MachinePressBlockEntity press) {
        if (level.isClientSide) {
            press.clientTick();
        } else {
            press.serverTick((ServerLevel) level, position, state);
        }
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        boolean preheated = false;
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(position.relative(direction)).is(ModBlocks.PRESS_PREHEATER.get())) {
                preheated = true;
                break;
            }
        }

        boolean canProcess = canProcess();
        boolean changed = false;

        if ((canProcess || retracting) && burnTime >= 200) {
            int oldSpeed = speed;
            speed = Math.min(MAX_SPEED, speed + (preheated ? 4 : 1));
            changed |= speed != oldSpeed;
        } else {
            int oldSpeed = speed;
            speed = Math.max(0, speed - 1);
            changed |= speed != oldSpeed;
        }

        if (delay <= 0) {
            int stampSpeed = speed * PROGRESS_AT_MAX / MAX_SPEED;
            if (retracting) {
                int oldPress = press;
                press -= stampSpeed;
                changed |= press != oldPress;
                if (press <= 0) {
                    retracting = false;
                    delay = 5;
                    changed = true;
                }
            } else if (canProcess) {
                int oldPress = press;
                press += stampSpeed;
                changed |= press != oldPress;
                if (press >= MAX_PRESS) {
                    completeOperation(level, position);
                    changed = true;
                }
            } else if (press > 0) {
                retracting = true;
                changed = true;
            }
        } else {
            delay--;
        }

        ItemStack fuel = items.get(SLOT_FUEL);
        int fuelValue = fuelValue(fuel);
        if (!fuel.isEmpty() && burnTime < 200 && fuelValue > 0) {
            burnTime += fuelValue;
            if (fuel.getCount() == 1 && fuel.getItem().hasCraftingRemainingItem()) {
                items.set(SLOT_FUEL, new ItemStack(fuel.getItem().getCraftingRemainingItem()));
            } else {
                fuel.shrink(1);
                if (fuel.isEmpty()) {
                    items.set(SLOT_FUEL, ItemStack.EMPTY);
                }
            }
            changed = true;
        }

        if (changed) {
            setChanged();
        }
        syncClient(level, position, state);
    }

    private void clientTick() {
        lastPress = renderPress;
        if (turnProgress > 0) {
            renderPress += (syncPress - renderPress) / turnProgress;
            turnProgress--;
        } else {
            renderPress = syncPress;
        }
    }

    private void completeOperation(ServerLevel level, BlockPos position) {
        level.playSound(null, position, ModSounds.PRESS_OPERATE.get(), SoundSource.BLOCKS, 1.5F, 1.0F);
        ItemStack output = PressRecipes.getOutput(items.get(SLOT_INPUT), items.get(SLOT_STAMP));
        ItemStack outputSlot = items.get(SLOT_OUTPUT);
        if (outputSlot.isEmpty()) {
            items.set(SLOT_OUTPUT, output.copy());
        } else {
            outputSlot.grow(output.getCount());
        }
        removeItem(SLOT_INPUT, 1);

        ItemStack stamp = items.get(SLOT_STAMP);
        if (stamp.getMaxDamage() != 0) {
            int damage = stamp.getDamageValue() + 1;
            if (damage >= stamp.getMaxDamage()) {
                items.set(SLOT_STAMP, ItemStack.EMPTY);
            } else {
                stamp.setDamageValue(damage);
            }
        }

        retracting = true;
        delay = 5;
        if (burnTime >= 200) {
            burnTime -= 200;
        }
        setChanged();
    }

    public boolean canProcess() {
        if (burnTime < 200 || items.get(SLOT_STAMP).isEmpty() || items.get(SLOT_INPUT).isEmpty()) {
            return false;
        }
        ItemStack output = PressRecipes.getOutput(items.get(SLOT_INPUT), items.get(SLOT_STAMP));
        if (output.isEmpty()) {
            return false;
        }
        ItemStack outputSlot = items.get(SLOT_OUTPUT);
        return outputSlot.isEmpty()
                || ItemStack.isSameItemSameComponents(outputSlot, output)
                && outputSlot.getCount() + output.getCount() <= outputSlot.getMaxStackSize();
    }

    private void syncClient(ServerLevel level, BlockPos position, BlockState state) {
        ItemStack input = items.get(SLOT_INPUT);
        boolean heartbeat = level.getGameTime() % 20L == 0L;
        if (!heartbeat && speed == lastSyncedSpeed && burnTime == lastSyncedBurnTime && press == lastSyncedPress
                && ItemStack.matches(input, lastSyncedInput)) {
            return;
        }
        lastSyncedSpeed = speed;
        lastSyncedBurnTime = burnTime;
        lastSyncedPress = press;
        lastSyncedInput = input.copy();
        level.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS);
    }

    public static int fuelValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return AbstractFurnaceBlockEntity.getFuel().getOrDefault(stack.getItem(), 0);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("press", press);
        tag.putInt("burnTime", burnTime);
        tag.putInt("speed", speed);
        tag.putBoolean("ret", retracting);
        if (customName != null) {
            tag.putString("name", Component.Serializer.toJson(customName, registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        press = tag.getInt("press");
        burnTime = tag.getInt("burnTime");
        speed = tag.getInt("speed");
        retracting = tag.getBoolean("ret");
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
        syncPress = press;
        renderPress = press;
        lastPress = press;
        renderStack = items.get(SLOT_INPUT).copy();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("speed", speed);
        tag.putInt("burnTime", burnTime);
        tag.putInt("press", press);
        if (!items.get(SLOT_INPUT).isEmpty()) {
            tag.put("input", items.get(SLOT_INPUT).save(registries));
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        speed = tag.getInt("speed");
        burnTime = tag.getInt("burnTime");
        syncPress = tag.getInt("press");
        renderStack = tag.contains("input") ? ItemStack.parseOptional(registries, tag.getCompound("input")) : ItemStack.EMPTY;
        turnProgress = 2;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.press");
    }

    public void setCustomName(Component customName) {
        this.customName = customName;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MachinePressMenu(containerId, playerInventory, this, data);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) <= 128.0D;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (stack.getItem() instanceof StampItem) {
            return slot == SLOT_STAMP;
        }
        if (fuelValue(stack) > 0 && slot == SLOT_FUEL) {
            return true;
        }
        return slot == SLOT_INPUT;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public int[] getSlotsForFace(Direction direction) {
        return AUTOMATION_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return slot == SLOT_OUTPUT;
    }

    public int speed() {
        return speed;
    }

    public int burnTime() {
        return burnTime;
    }

    public int press() {
        return press;
    }

    public double renderPress() {
        return renderPress;
    }

    public double lastPress() {
        return lastPress;
    }

    public ItemStack renderStack() {
        return renderStack;
    }
}
