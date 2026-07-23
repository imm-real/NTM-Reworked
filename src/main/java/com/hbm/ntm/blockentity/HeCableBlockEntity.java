package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.HeCableBlock;
import com.hbm.ntm.energy.HeConductor;
import com.hbm.ntm.energy.HeNetworkManager;
import com.hbm.ntm.energy.HeNode;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class HeCableBlockEntity extends BlockEntity implements HeConductor {
    protected HeNode node;

    public HeCableBlockEntity(BlockPos position, BlockState state) {
        this(ModBlockEntities.RED_CABLE.get(), position, state);
    }

    protected HeCableBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state) {
        super(type, position, state);
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, HeCableBlockEntity cable) {
        if (level instanceof ServerLevel serverLevel) {
            cable.ensureNode(serverLevel);
            if (state.getBlock() instanceof HeCableBlock cableBlock) {
                BlockState correctedState = cableBlock.updateRenderShape(state);
                if (correctedState != state) {
                    level.setBlock(position, correctedState, 2);
                }
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            ensureNode(serverLevel);
        }
    }

    @Override
    public void onChunkUnloaded() {
        // Keep the node; UNINOS cable topology survives chunk unload.
    }

    protected void ensureNode(ServerLevel level) {
        if (node != null && !node.expired()) {
            return;
        }
        HeNetworkManager manager = HeNetworkManager.get(level);
        node = manager.getNode(worldPosition);
        if (node == null || node.expired()) {
            node = createNode(worldPosition);
            manager.createNode(node);
        }
    }

    protected HeNode node() {
        return node;
    }
}
