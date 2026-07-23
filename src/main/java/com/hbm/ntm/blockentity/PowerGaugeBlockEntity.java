package com.hbm.ntm.blockentity;

import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.ror.RorValueProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class PowerGaugeBlockEntity extends HeCableBlockEntity implements RorValueProvider {
    private long deltaTick;
    private long deltaSecond;
    private long deltaLastSecond;

    public PowerGaugeBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.POWER_GAUGE.get(), position, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PowerGaugeBlockEntity gauge) {
        if (!(level instanceof ServerLevel server)) return;
        gauge.ensureNode(server);
        gauge.deltaTick = gauge.node() != null && gauge.node().network() != null
                ? gauge.node().network().energyTracker() : 0L;
        if (level.getGameTime() % 20L == 0L) {
            gauge.deltaLastSecond = gauge.deltaSecond;
            gauge.deltaSecond = 0L;
        }
        gauge.deltaSecond += gauge.deltaTick;
        if (level.getGameTime() % 25L == 0L) {
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public long deltaTick() { return deltaTick; }
    public long deltaSecond() { return deltaLastSecond; }

    @Override public String[] rorInfo() {
        return new String[]{VALUE_PREFIX + "deltatick", VALUE_PREFIX + "deltasecond"};
    }

    @Override public String provideRorValue(String name) {
        if ((VALUE_PREFIX + "deltatick").equals(name)) return Long.toString(deltaTick);
        if ((VALUE_PREFIX + "deltasecond").equals(name)) return Long.toString(deltaSecond);
        return null;
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("deltaTick", deltaTick);
        tag.putLong("deltaSecond", deltaSecond);
        tag.putLong("deltaLastSecond", deltaLastSecond);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        deltaTick = tag.getLong("deltaTick");
        deltaSecond = tag.getLong("deltaSecond");
        deltaLastSecond = tag.getLong("deltaLastSecond");
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
