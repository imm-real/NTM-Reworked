package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.AbstractCraneBlock;
import com.hbm.ntm.conveyor.ConveyorBelt;
import com.hbm.ntm.conveyor.ConveyorEnterable;
import com.hbm.ntm.entity.MovingConveyorItemEntity;
import com.hbm.ntm.inventory.CraneExtractorMenu;
import com.hbm.ntm.item.MachineUpgradeItem;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.Nullable;

/** Source Conveyor Ejector: nine ghost filters, nine-slot buffer and two ejector upgrades. */
public final class CraneExtractorBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int FILTER_START = 0;
    public static final int FILTER_END = 9;
    public static final int BUFFER_START = 9;
    public static final int BUFFER_END = 18;
    public static final int STACK_UPGRADE = 18;
    public static final int EJECTOR_UPGRADE = 19;
    public static final int SLOT_COUNT = 20;
    private static final int[] BUFFER_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final boolean[] exactFilter = new boolean[FILTER_END];
    private boolean whitelist;
    private boolean maxEject;
    private Component customName;
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> whitelist ? 1 : 0;
                case 1 -> maxEject ? 1 : 0;
                case 2 -> filterMask();
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            if (index == 0) whitelist = value != 0;
            else if (index == 1) maxEject = value != 0;
            else if (index == 2) setFilterMask(value);
        }
        @Override public int getCount() { return 3; }
    };

    public CraneExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRANE_EXTRACTOR.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CraneExtractorBlockEntity extractor) {
        extractor.serverTick(level, pos, state);
    }

    private void serverTick(Level level, BlockPos pos, BlockState state) {
        int delay = switch (upgradeLevel(EJECTOR_UPGRADE, MachineUpgradeItem.Type.EJECTOR)) {
            case 1 -> 10;
            case 2 -> 5;
            case 3 -> 2;
            default -> 20;
        };
        if (level.getGameTime() % delay != 0L || level.hasNeighborSignal(pos)) return;

        int amount = switch (upgradeLevel(STACK_UPGRADE, MachineUpgradeItem.Type.STACK)) {
            case 1 -> 4;
            case 2 -> 16;
            case 3 -> 64;
            default -> 1;
        };
        Direction beltSide = state.getValue(AbstractCraneBlock.INPUT); // source switcheroo
        Direction inventorySide = state.getValue(AbstractCraneBlock.OUTPUT);
        BeltTarget belt = outputBelt(level, pos, beltSide);
        IItemHandler source = sourceHandler(level, pos.relative(inventorySide), inventorySide.getOpposite());

        boolean sent = source != null && pullFromSource(source, amount, belt);
        if (!sent && belt != null) sendBuffered(amount, belt);
    }

    private boolean pullFromSource(IItemHandler source, int amount, @Nullable BeltTarget belt) {
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack available = source.getStackInSlot(slot);
            if (available.isEmpty()) continue;
            int maxTarget = Math.min(amount, available.getMaxStackSize());
            if (maxEject && available.getCount() < maxTarget) continue;
            boolean match = matchesFilter(available);
            if (whitelist != match) continue;

            int requested = Math.min(amount, available.getCount());
            ItemStack simulated = source.extractItem(slot, requested, true);
            if (simulated.isEmpty()) continue;
            if (belt != null) {
                ItemStack extracted = source.extractItem(slot, simulated.getCount(), false);
                if (extracted.isEmpty()) continue;
                spawnMoving(extracted, belt);
                setChanged();
                return true;
            }

            ItemStack remainder = insertBuffer(simulated, true);
            int accepted = simulated.getCount() - remainder.getCount();
            if (accepted <= 0) return true;
            ItemStack extracted = source.extractItem(slot, accepted, false);
            insertBuffer(extracted, false);
            setChanged();
            return true;
        }
        return false;
    }

    private void sendBuffered(int amount, BeltTarget belt) {
        for (int slot = BUFFER_START; slot < BUFFER_END; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) continue;
            int maxTarget = Math.min(amount, stack.getMaxStackSize());
            if (maxEject && stack.getCount() < maxTarget) continue;
            ItemStack sent = removeItem(slot, Math.min(amount, stack.getCount()));
            if (!sent.isEmpty()) spawnMoving(sent, belt);
            return;
        }
    }

    private void spawnMoving(ItemStack stack, BeltTarget target) {
        Vec3 start = worldPosition.getCenter().add(target.output().getStepX() * .55D,
                target.output().getStepY() * .55D, target.output().getStepZ() * .55D);
        Vec3 snap = target.belt().closestSnappingPosition(level, target.pos(), target.state(), start);
        MovingConveyorItemEntity moving = MovingConveyorItemEntity.create(level, stack);
        moving.setPos(snap.x, snap.y, snap.z);
        if (target.state().getBlock() instanceof ConveyorEnterable enterable
                && enterable.canConveyorItemEnter(level, target.pos(), target.output().getOpposite(), moving)) {
            enterable.onConveyorItemEnter(level, target.pos(), target.output().getOpposite(), moving);
            return;
        }
        level.addFreshEntity(moving);
    }

    @Nullable
    private static BeltTarget outputBelt(Level level, BlockPos pos, Direction side) {
        BlockPos beltPos = pos.relative(side);
        BlockState beltState = level.getBlockState(beltPos);
        return beltState.getBlock() instanceof ConveyorBelt belt
                ? new BeltTarget(side, beltPos, beltState, belt) : null;
    }

    @Nullable
    private static IItemHandler sourceHandler(Level level, BlockPos pos, Direction side) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        if (handler != null) return handler;
        if (level.getBlockEntity(pos) instanceof WorldlyContainer sided) return new SidedInvWrapper(sided, side);
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container container) return new InvWrapper(container);
        return null;
    }

    private ItemStack insertBuffer(ItemStack incoming, boolean simulate) {
        ItemStack remainder = incoming.copy();
        for (int slot = BUFFER_START; slot < BUFFER_END && !remainder.isEmpty(); slot++) {
            ItemStack existing = items.get(slot);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remainder)) continue;
            int moved = Math.min(remainder.getCount(), Math.min(64, existing.getMaxStackSize()) - existing.getCount());
            if (moved > 0) {
                if (!simulate) existing.grow(moved);
                remainder.shrink(moved);
            }
        }
        for (int slot = BUFFER_START; slot < BUFFER_END && !remainder.isEmpty(); slot++) {
            if (!items.get(slot).isEmpty()) continue;
            int moved = Math.min(remainder.getCount(), Math.min(64, remainder.getMaxStackSize()));
            if (!simulate) items.set(slot, remainder.copyWithCount(moved));
            remainder.shrink(moved);
        }
        return remainder;
    }

    public boolean matchesFilter(ItemStack stack) {
        for (int slot = FILTER_START; slot < FILTER_END; slot++) {
            ItemStack filter = items.get(slot);
            if (filter.isEmpty()) continue;
            if (exactFilter[slot] ? ItemStack.isSameItemSameComponents(filter, stack)
                    : filter.is(stack.getItem())) return true;
        }
        return false;
    }

    public void setFilter(int slot, ItemStack stack) {
        if (slot < FILTER_START || slot >= FILTER_END) return;
        items.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        exactFilter[slot] = !stack.isEmpty();
        setChanged();
    }

    public void nextFilterMode(int slot) {
        if (slot < FILTER_START || slot >= FILTER_END || items.get(slot).isEmpty()) return;
        exactFilter[slot] = !exactFilter[slot];
        setChanged();
    }

    public boolean exactFilter(int slot) { return slot >= 0 && slot < exactFilter.length && exactFilter[slot]; }
    public boolean whitelist() { return whitelist; }
    public boolean maxEject() { return maxEject; }
    public void toggleWhitelist() { whitelist = !whitelist; setChanged(); }
    public void toggleMaxEject() { maxEject = !maxEject; setChanged(); }
    public ContainerData dataAccess() { return data; }

    private int upgradeLevel(int slot, MachineUpgradeItem.Type type) {
        return items.get(slot).getItem() instanceof MachineUpgradeItem upgrade && upgrade.type() == type
                ? upgrade.level() : 0;
    }

    private int filterMask() {
        int mask = 0;
        for (int i = 0; i < exactFilter.length; i++) if (exactFilter[i]) mask |= 1 << i;
        return mask;
    }

    private void setFilterMask(int mask) {
        for (int i = 0; i < exactFilter.length; i++) exactFilter[i] = (mask & 1 << i) != 0;
    }

    public void dropRealContents() {
        if (level == null) return;
        for (int slot = BUFFER_START; slot < SLOT_COUNT; slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + .5D,
                    worldPosition.getY() + .5D, worldPosition.getZ() + .5D, stack.copy());
        }
        items.clear();
        setChanged();
    }

    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.craneExtractor");
    }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CraneExtractorMenu(id, inventory, this, data);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putBoolean("isWhitelist", whitelist);
        tag.putBoolean("maxEject", maxEject);
        tag.putInt("filterModes", filterMask());
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        whitelist = tag.getBoolean("isWhitelist");
        maxEject = tag.getBoolean("maxEject");
        setFilterMask(tag.getInt("filterModes"));
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() {
        for (int slot = BUFFER_START; slot < SLOT_COUNT; slot++) if (!items.get(slot).isEmpty()) return false;
        return true;
    }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        int limit = slot < FILTER_END ? 1 : Math.min(64, stack.getMaxStackSize());
        items.set(slot, stack.copyWithCount(Math.min(stack.getCount(), limit)));
        setChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()) < 400D;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot >= BUFFER_START && slot < BUFFER_END) return true;
        if (!(stack.getItem() instanceof MachineUpgradeItem upgrade)) return false;
        return slot == STACK_UPGRADE && upgrade.type() == MachineUpgradeItem.Type.STACK
                || slot == EJECTOR_UPGRADE && upgrade.type() == MachineUpgradeItem.Type.EJECTOR;
    }
    @Override public int[] getSlotsForFace(Direction side) { return BUFFER_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot >= BUFFER_START && slot < BUFFER_END;
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= BUFFER_START && slot < BUFFER_END;
    }

    private record BeltTarget(Direction output, BlockPos pos, BlockState state, ConveyorBelt belt) { }
}
