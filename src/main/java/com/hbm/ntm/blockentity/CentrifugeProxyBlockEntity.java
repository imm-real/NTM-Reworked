package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.CentrifugeBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** HE and inventory proxy with no fluid port. */
public final class CentrifugeProxyBlockEntity extends InventoryProxyBlockEntity<CentrifugeBlockEntity>
        implements HeReceiverProxy {
    private static final int[] AUTOMATION_SLOTS = {0, 2, 3, 4, 5};

    public CentrifugeProxyBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_CENTRIFUGE_PROXY.get(), position, state);
    }

    @Override
    @Nullable public CentrifugeBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof CentrifugeBlock)) return null;
        BlockPos core = CentrifugeBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof CentrifugeBlockEntity centrifuge ? centrifuge : null;
    }

    @Override public boolean canConnect(Direction side) { return side != null && target() != null; }
    @Override public int[] getSlotsForFace(Direction side) { return target() == null ? NO_SLOTS : AUTOMATION_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return target() != null && slot == CentrifugeBlockEntity.INPUT;
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return target() != null && slot >= CentrifugeBlockEntity.OUTPUT_START
                && slot < CentrifugeBlockEntity.OUTPUT_END;
    }
}
