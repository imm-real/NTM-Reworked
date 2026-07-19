package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.ArcFurnaceBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Inventory and HE proxy used by the furnace's six extra cells. */
public final class ArcFurnaceProxyBlockEntity extends InventoryProxyBlockEntity<ArcFurnaceBlockEntity>
        implements HeReceiverProxy {
    public ArcFurnaceProxyBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_ARC_FURNACE_PROXY.get(), position, state);
    }

    @Override
    @Nullable public ArcFurnaceBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof ArcFurnaceBlock)) return null;
        BlockPos core = ArcFurnaceBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof ArcFurnaceBlockEntity furnace ? furnace : null;
    }

    @Override public boolean canConnect(Direction side) { return side != null; }
}
