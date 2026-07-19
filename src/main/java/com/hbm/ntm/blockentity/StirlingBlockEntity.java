package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.ThermalMultiblockBlock;
import com.hbm.ntm.energy.HeProvider;
import com.hbm.ntm.entity.CogEntity;
import com.hbm.ntm.network.StirlingStatePayload;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.thermal.HeatSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public final class StirlingBlockEntity extends BlockEntity implements HeProvider {
    public static final double DIFFUSION = 0.1D;
    public static final double EFFICIENCY = 0.5D;
    public static final int MAX_HEAT = 300;
    public static final int OVERSPEED_LIMIT = 300;

    private long powerBuffer;
    private int heat;
    private long synchronizedPower;
    private int synchronizedHeat;
    private int warningCooldown;
    private int overspeed;
    private boolean hasCog = true;
    private float spin;
    private float lastSpin;

    public StirlingBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_STIRLING.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, StirlingBlockEntity stirling) {
        if (level.isClientSide) {
            stirling.clientTick();
        } else {
            stirling.serverTick((ServerLevel) level, position, state);
        }
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        if (hasCog) {
            powerBuffer = 0;
            pullHeat(level, position);
            powerBuffer = (long) (heat * EFFICIENCY);

            if (warningCooldown > 0) {
                warningCooldown--;
            }
            if (heat > MAX_HEAT) {
                overspeed++;
                if (overspeed > 60 && warningCooldown == 0) {
                    warningCooldown = 100;
                    level.playSound(null, position.getX() + 0.5D, position.getY() + 1.0D,
                            position.getZ() + 0.5D, ModSounds.WARN_OVERSPEED.get(),
                            SoundSource.BLOCKS, 2.0F, 1.0F);
                }
                if (overspeed > OVERSPEED_LIMIT) {
                    fail(level, position, state);
                }
            } else {
                overspeed = 0;
            }
        } else {
            overspeed = 0;
            warningCooldown = 0;
        }

        // Snapshot before provision empties the transient fields; block updates arrive later.
        synchronizedPower = powerBuffer;
        synchronizedHeat = heat;
        PacketDistributor.sendToPlayersNear(
                level,
                null,
                position.getX() + 0.5D,
                position.getY() + 1.0D,
                position.getZ() + 0.5D,
                150.0D,
                new StirlingStatePayload(position, synchronizedPower, synchronizedHeat, hasCog)
        );

        if (hasCog) {
            provide(level, position.east(2), Direction.EAST);
            provide(level, position.west(2), Direction.WEST);
            provide(level, position.south(2), Direction.SOUTH);
            provide(level, position.north(2), Direction.NORTH);
        } else if (powerBuffer > 0) {
            powerBuffer--;
        }
        heat = 0;
        setChanged();
    }

    private void provide(ServerLevel level, BlockPos target, Direction direction) {
        tryProvide(level, target, direction);
    }

    private void pullHeat(ServerLevel level, BlockPos position) {
        if (level.getBlockEntity(position.below()) instanceof HeatSource source) {
            int pulled = (int) (source.getHeatStored() * DIFFUSION);
            if (pulled > 0) {
                source.useUpHeat(pulled);
                heat += pulled;
                return;
            }
        }
        heat = Math.max(heat - Math.max(heat / 1000, 1), 0);
    }

    private void fail(ServerLevel level, BlockPos position, BlockState state) {
        hasCog = false;
        ThermalMultiblockBlock.updateCogState(level, position, false);
        level.explode(null, position.getX() + 0.5D, position.getY() + 1.0D, position.getZ() + 0.5D,
                5.0F, false, Level.ExplosionInteraction.NONE);

        Direction facing = state.getValue(ThermalMultiblockBlock.FACING);
        CogEntity cog = new CogEntity(ModEntities.COG.get(), level);
        cog.setPos(position.getX() + 0.5D + facing.getStepX(), position.getY() + 1.0D,
                position.getZ() + 0.5D + facing.getStepZ());
        cog.setOrientation(facing);
        Direction tangent = facing.getClockWise();
        cog.setDeltaMovement(tangent.getStepX(), 1.0D + (heat - MAX_HEAT) * 0.0001D, tangent.getStepZ());
        level.addFreshEntity(cog);
    }

    private void clientTick() {
        float momentum = powerBuffer * 50.0F / MAX_HEAT;
        lastSpin = spin;
        spin += momentum;
        if (spin >= 360.0F) {
            spin -= 360.0F;
            lastSpin -= 360.0F;
        }
    }

    public void applyClientSnapshot(long power, int heat, boolean hasCog) {
        this.powerBuffer = power;
        this.heat = heat;
        this.hasCog = hasCog;
    }

    public void setHasCog(boolean hasCog) {
        this.hasCog = hasCog;
        synchronizedPower = powerBuffer;
        synchronizedHeat = heat;
        setChanged();
        if (level != null) {
            ThermalMultiblockBlock.updateCogState(level, worldPosition, hasCog);
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("powerBuffer", powerBuffer);
        tag.putBoolean("hasCog", hasCog);
        tag.putInt("overspeed", overspeed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        powerBuffer = tag.getLong("powerBuffer");
        synchronizedPower = powerBuffer;
        hasCog = tag.getBoolean("hasCog");
        overspeed = tag.getInt("overspeed");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("powerBuffer", synchronizedPower);
        tag.putInt("heat", synchronizedHeat);
        tag.putBoolean("hasCog", hasCog);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        powerBuffer = tag.getLong("powerBuffer");
        heat = tag.getInt("heat");
        hasCog = tag.getBoolean("hasCog");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public long getPower() { return powerBuffer; }
    @Override public void setPower(long power) { powerBuffer = power; }
    @Override public long getMaxPower() { return powerBuffer; }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }

    public long powerBuffer() { return powerBuffer; }
    public int heat() { return heat; }
    public boolean hasCog() { return hasCog; }
    public float spin() { return spin; }
    public float lastSpin() { return lastSpin; }
    public AABB renderBounds() { return new AABB(worldPosition.getX() - 1, worldPosition.getY(), worldPosition.getZ() - 1, worldPosition.getX() + 2, worldPosition.getY() + 2, worldPosition.getZ() + 2); }
}
