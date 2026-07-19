package com.hbm.ntm.blockentity;

import com.hbm.ntm.foundry.FoundryMaterial;
import com.hbm.ntm.foundry.MoltenAcceptor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** One-material foundry buffer shared by channels and storage basins. */
public abstract class AbstractFoundryBlockEntity extends BlockEntity implements MoltenAcceptor {
    protected FoundryMaterial material;
    protected int amount;

    protected AbstractFoundryBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public abstract int capacity();

    public FoundryMaterial material() { return material; }
    public int amount() { return amount; }

    protected final void normalize() {
        amount = Math.max(0, Math.min(amount, capacity()));
        if (amount == 0) material = null;
    }

    protected final boolean standardCanAccept(FoundryMaterial incoming, int offered) {
        return incoming != null && offered > 0 && amount < capacity()
                && (material == null || material == incoming || amount == 0);
    }

    protected final int standardAccept(FoundryMaterial incoming, int offered) {
        if (!standardCanAccept(incoming, offered)) return 0;
        int accepted = Math.min(offered, capacity() - amount);
        if (accepted <= 0) return 0;
        material = incoming;
        amount += accepted;
        sync();
        return accepted;
    }

    public final int removeMolten(int requested) {
        int removed = Math.min(Math.max(requested, 0), amount);
        if (removed <= 0) return 0;
        amount -= removed;
        normalize();
        sync();
        return removed;
    }

    public final void clearMolten() {
        material = null;
        amount = 0;
        sync();
    }

    public final void setMoltenForTest(FoundryMaterial material, int amount) {
        this.material = material;
        this.amount = amount;
        normalize();
        sync();
    }

    @Override public boolean canAcceptPour(FoundryMaterial incoming, int offered, Direction inputSide) {
        return inputSide == Direction.UP && standardCanAccept(incoming, offered);
    }

    @Override public int acceptPour(FoundryMaterial incoming, int offered, Direction inputSide) {
        return canAcceptPour(incoming, offered, inputSide) ? standardAccept(incoming, offered) : 0;
    }

    @Override public boolean canAcceptFlow(FoundryMaterial incoming, int offered, Direction inputSide) {
        return standardCanAccept(incoming, offered);
    }

    @Override public int acceptFlow(FoundryMaterial incoming, int offered, Direction inputSide) {
        return canAcceptFlow(incoming, offered, inputSide) ? standardAccept(incoming, offered) : 0;
    }

    protected final void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (material != null) tag.putString("material", material.id());
        tag.putInt("amount", amount);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        material = FoundryMaterial.byId(tag.getString("material"));
        amount = tag.getInt("amount");
        normalize();
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (material != null) tag.putString("material", material.id());
        tag.putInt("amount", amount);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        material = FoundryMaterial.byId(tag.getString("material"));
        amount = tag.getInt("amount");
        normalize();
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
