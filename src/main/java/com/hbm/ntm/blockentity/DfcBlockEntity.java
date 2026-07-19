package com.hbm.ntm.blockentity;

import com.hbm.ntm.dfc.DfcKind;
import com.hbm.ntm.inventory.DfcMenu;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public abstract class DfcBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int DATA_COUNT = 18;
    protected final NonNullList<ItemStack> items;
    private final int[] allSlots;
    private final DfcKind kind;
    private final ContainerData menuData = new ContainerData() {
        @Override public int get(int index) { return menuValue(index); }
        @Override public void set(int index, int value) { }
        @Override public int getCount() { return DATA_COUNT; }
    };

    protected DfcBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, DfcKind kind) {
        super(type, pos, state);
        this.kind = kind;
        this.items = NonNullList.withSize(kind.slots(), ItemStack.EMPTY);
        this.allSlots = new int[kind.slots()];
        for (int i = 0; i < allSlots.length; i++) allSlots[i] = i;
    }

    public final DfcKind kind() { return kind; }
    protected abstract int menuValue(int index);
    protected abstract void saveDfcState(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket);
    protected abstract void loadDfcState(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket);

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!items.isEmpty()) ContainerHelper.saveAllItems(tag, items, registries);
        saveDfcState(tag, registries, false);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (!items.isEmpty()) ContainerHelper.loadAllItems(tag, items, registries);
        loadDfcState(tag, registries, false);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveDfcState(tag, registries, true);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadDfcState(tag, registries, true);
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public Component getDisplayName() { return Component.translatable(kind.translationKey()); }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new DfcMenu(id, inventory, this, menuData, kind);
    }

    @Override public int getContainerSize() { return items.size(); }
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
                && player.distanceToSqr(worldPosition.getCenter()) <= 128.0D;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public int[] getSlotsForFace(Direction side) { return allSlots; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return true; }
}
