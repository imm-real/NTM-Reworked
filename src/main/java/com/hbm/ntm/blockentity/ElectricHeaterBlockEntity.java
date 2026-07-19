package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.ElectricHeaterBlock;
import com.hbm.ntm.energy.HeReceiver;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.thermal.HeatSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Electricity goes in, heat comes out, accounting cries in ten steps. */
public final class ElectricHeaterBlockEntity extends BlockEntity implements HeReceiver, HeatSource {
    private long power;
    private int heatEnergy;
    private boolean active;
    private int setting;

    public ElectricHeaterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HEATER_ELECTRIC.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ElectricHeaterBlockEntity heater) {
        if (level.isClientSide) return;
        heater.serverTick((ServerLevel) level, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(ElectricHeaterBlock.FACING);
        if (level.getGameTime() % 20L == 0L) {
            trySubscribe(level, pos.relative(facing, 3), facing);
        }

        // Keep the truncation. Free fractions are still free heat.
        heatEnergy = (int) (heatEnergy * 0.999D);
        pullHeat(level, pos);

        active = false;
        long consumption = getConsumption();
        if (setting > 0 && power >= consumption) {
            power -= consumption;
            heatEnergy += getHeatGen();
            active = true;
        }

        setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    private void pullHeat(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos.below()) instanceof HeatSource source) {
            heatEnergy = (int) (heatEnergy + source.getHeatStored() * 0.85D);
            source.useUpHeat(source.getHeatStored());
        }
    }

    public void toggleSetting() {
        setting++;
        if (setting > 10) setting = 0;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public int setting() { return setting; }
    public boolean active() { return active; }
    public int heatEnergy() { return heatEnergy; }
    public long getConsumption() { return (long) (Math.pow(setting, 1.4D) * 200D); }
    public int getHeatGen() { return setting * 100; }

    @Override public long getPower() { return power; }
    @Override public void setPower(long power) { this.power = power; setChanged(); }
    @Override public long getMaxPower() { return getConsumption() * 20L; }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }

    @Override public int getHeatStored() { return heatEnergy; }
    @Override public void useUpHeat(int heat) {
        heatEnergy = Math.max(0, heatEnergy - heat);
        setChanged();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("power", power);
        tag.putInt("setting", setting);
        tag.putInt("heatEnergy", heatEnergy);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        power = tag.getLong("power");
        setting = tag.getInt("setting");
        heatEnergy = tag.getInt("heatEnergy");
        active = tag.getBoolean("isOn");
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("setting", setting);
        tag.putInt("heatEnergy", heatEnergy);
        tag.putBoolean("isOn", active);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        setting = tag.getInt("setting");
        heatEnergy = tag.getInt("heatEnergy");
        active = tag.getBoolean("isOn");
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
