package com.hbm.ntm.blockentity;

import com.hbm.ntm.foundry.FoundryMaterial;
import com.hbm.ntm.foundry.MoltenAcceptor;
import com.hbm.ntm.item.FoundryMoldItem;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class FoundryMoldBlockEntity extends BlockEntity implements WorldlyContainer, MoltenAcceptor {
    public static final int MOLD = 0;
    public static final int OUTPUT = 1;
    private static final int[] AUTOMATION_SLOTS = {OUTPUT};

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private FoundryMaterial material;
    private int amount;
    private int cooloff = 100;

    public FoundryMoldBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FOUNDRY_MOLD.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FoundryMoldBlockEntity mold) {
        if (level.isClientSide) return;
        mold.amount = Math.min(mold.amount, mold.capacity());
        if (mold.amount <= 0) {
            mold.amount = 0;
            mold.material = null;
        }

        if (mold.hasSupportedMold() && mold.amount == mold.capacity() && mold.items.get(OUTPUT).isEmpty()) {
            mold.cooloff--;
            if (mold.cooloff <= 0) {
                FoundryMaterial completed = mold.material;
                mold.amount = 0;
                mold.material = null;
                ItemStack output = completed == null ? ItemStack.EMPTY : mold.outputFor(completed);
                if (!output.isEmpty()) mold.items.set(OUTPUT, output);
                mold.cooloff = 200;
                mold.sync();
            } else if (mold.cooloff % 20 == 0) {
                mold.sync();
            }
        } else if (mold.cooloff != 200) {
            mold.cooloff = 200;
            mold.sync();
        }
    }

    public int pour(FoundryMaterial incoming, int offered) {
        if (offered <= 0 || !hasSupportedMold() || !items.get(OUTPUT).isEmpty()
                || outputFor(incoming).isEmpty() || material != null && material != incoming) return 0;
        int accepted = Math.min(offered, capacity() - amount);
        if (accepted <= 0) return 0;
        material = incoming;
        amount += accepted;
        sync();
        return accepted;
    }

    @Override public boolean canAcceptPour(FoundryMaterial incoming, int offered, Direction inputSide) {
        return inputSide == Direction.UP && offered > 0 && hasSupportedMold() && items.get(OUTPUT).isEmpty()
                && !outputFor(incoming).isEmpty() && (material == null || material == incoming)
                && amount < capacity();
    }

    @Override public int acceptPour(FoundryMaterial incoming, int offered, Direction inputSide) {
        return canAcceptPour(incoming, offered, inputSide) ? pour(incoming, offered) : 0;
    }

    @Override public boolean canAcceptFlow(FoundryMaterial incoming, int offered, Direction inputSide) {
        return offered > 0 && hasSupportedMold() && items.get(OUTPUT).isEmpty()
                && !outputFor(incoming).isEmpty() && (material == null || material == incoming)
                && amount < capacity();
    }

    @Override public int acceptFlow(FoundryMaterial incoming, int offered, Direction inputSide) {
        return canAcceptFlow(incoming, offered, inputSide) ? pour(incoming, offered) : 0;
    }

    @Nullable public FoundryMoldItem.Mold installedMold() {
        return FoundryMoldItem.type(items.get(MOLD));
    }

    public FoundryMoldItem.MoldSize moldSize() {
        return getBlockState().is(ModBlocks.FOUNDRY_BASIN.get())
                ? FoundryMoldItem.MoldSize.LARGE : FoundryMoldItem.MoldSize.SMALL;
    }

    public boolean acceptsMold(ItemStack stack) {
        FoundryMoldItem.Mold candidate = FoundryMoldItem.type(stack);
        return candidate != null && candidate.size() == moldSize();
    }

    public boolean hasSupportedMold() {
        FoundryMoldItem.Mold installed = installedMold();
        return installed != null && installed.size() == moldSize();
    }

    private ItemStack outputFor(FoundryMaterial incoming) {
        FoundryMoldItem.Mold installed = installedMold();
        return installed == null ? ItemStack.EMPTY : installed.output(incoming);
    }

    public int capacity() {
        FoundryMoldItem.Mold installed = installedMold();
        return installed != null && installed.size() == moldSize() ? installed.cost() : 0;
    }

    public boolean isLargeBasin() { return moldSize() == FoundryMoldItem.MoldSize.LARGE; }
    public double moldRenderHeight() { return .13D; }
    public double outputRenderHeight() { return isLargeBasin() ? .875D : .25D; }
    public double moltenSurfaceHeight() {
        return capacity() <= 0 ? .125D : .125D + amount * (isLargeBasin() ? .75D : .25D) / capacity();
    }

    public FoundryMaterial material() { return material; }
    public int amount() { return amount; }
    public int cooloff() { return cooloff; }

    public void clearMolten() {
        material = null;
        amount = 0;
        cooloff = 200;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        if (material != null) tag.putString("material", material.id());
        tag.putInt("amount", amount);
        tag.putInt("cooloff", cooloff);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        material = material(tag.getString("material"));
        amount = tag.getInt("amount");
        cooloff = tag.contains("cooloff") ? tag.getInt("cooloff") : 100;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, items, registries);
        if (material != null) tag.putString("material", material.id());
        tag.putInt("amount", amount);
        tag.putInt("cooloff", cooloff);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        material = material(tag.getString("material"));
        amount = tag.getInt("amount");
        cooloff = tag.getInt("cooloff");
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Nullable private static FoundryMaterial material(String id) {
        for (FoundryMaterial value : FoundryMaterial.values()) if (value.id().equals(id)) return value;
        return null;
    }

    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) sync();
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        sync();
    }
    @Override public boolean stillValid(Player player) { return false; }
    @Override public void clearContent() { items.clear(); sync(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return false; }
    @Override public int[] getSlotsForFace(Direction side) { return AUTOMATION_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return slot == OUTPUT; }
}
