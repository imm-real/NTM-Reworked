package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.DynamicSlagBlock;
import com.hbm.ntm.foundry.FoundryMaterial;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Material-bearing slag volume created by the foundry spill outlet. */
public final class DynamicSlagBlockEntity extends BlockEntity {
    public static final int CAPACITY = FoundryMaterial.BLOCK * 16;
    private FoundryMaterial material;
    private int amount;

    public DynamicSlagBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DYNAMIC_SLAG.get(), pos, state);
    }

    public FoundryMaterial material() { return material; }
    public int amount() { return amount; }
    public int capacity() { return CAPACITY; }
    public double height() { return Math.max(1D / 16D, Math.min(1D, (double) amount / CAPACITY)); }

    public boolean canAccept(FoundryMaterial incoming) {
        return incoming != null && amount < CAPACITY && (material == null || material == incoming || amount == 0);
    }

    public int add(FoundryMaterial incoming, int offered) {
        if (offered <= 0 || !canAccept(incoming)) return 0;
        int accepted = Math.min(offered, CAPACITY - amount);
        material = incoming;
        amount += accepted;
        sync();
        return accepted;
    }

    public int remove(int requested) {
        int removed = Math.min(Math.max(requested, 0), amount);
        amount -= removed;
        if (amount <= 0) {
            amount = 0;
            material = null;
        }
        sync();
        return removed;
    }

    public void setContents(FoundryMaterial material, int amount) {
        this.material = material;
        this.amount = Math.max(0, Math.min(amount, CAPACITY));
        if (this.amount == 0) this.material = null;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            DynamicSlagBlock.schedule(level, worldPosition);
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (material != null) tag.putString("material", material.id());
        tag.putInt("amount", amount);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        material = FoundryMaterial.byId(tag.getString("material"));
        amount = Math.max(0, Math.min(tag.getInt("amount"), CAPACITY));
        if (amount == 0) material = null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (material != null) tag.putString("material", material.id());
        tag.putInt("amount", amount);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        material = FoundryMaterial.byId(tag.getString("material"));
        amount = Math.max(0, Math.min(tag.getInt("amount"), CAPACITY));
        if (amount == 0) material = null;
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
