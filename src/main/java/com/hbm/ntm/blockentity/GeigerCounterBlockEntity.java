package com.hbm.ntm.blockentity;

import com.hbm.ntm.radiation.ChunkRadiationData;
import com.hbm.ntm.radiation.RadiationClicker;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.ror.RorValueProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class GeigerCounterBlockEntity extends BlockEntity implements RorValueProvider {
    private int timer;
    private float lastRadiation;

    public GeigerCounterBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.GEIGER.get(), position, state);
    }

    public static void serverTick(Level level, BlockPos position, BlockState state,
                                  GeigerCounterBlockEntity geiger) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        geiger.timer++;
        if (geiger.timer == 10) {
            geiger.timer = 0;
            geiger.lastRadiation = ChunkRadiationData.get(serverLevel).get(position);
            serverLevel.updateNeighbourForOutputSignal(position, state.getBlock());
        }
        if (geiger.timer % 5 == 0) {
            RadiationClicker.tickPlacedGeiger(serverLevel, position, geiger.lastRadiation);
        }
    }

    public float lastRadiation() {
        return lastRadiation;
    }

    public float check() {
        return level instanceof ServerLevel serverLevel
                ? ChunkRadiationData.get(serverLevel).get(worldPosition)
                : lastRadiation;
    }

    @Override public String[] rorInfo() { return new String[]{VALUE_PREFIX + "rad"}; }
    @Override public String provideRorValue(String name) {
        return (VALUE_PREFIX + "rad").equals(name) ? Integer.toString((int) Math.ceil(check())) : null;
    }
}
