package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.RefineryBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/** One of the refinery's four bottom-corner ports. */
public final class RefineryProxyBlockEntity extends InventoryProxyBlockEntity<RefineryBlockEntity>
        implements HeReceiverProxy {
    private static final int[] SULFUR_ONLY = {RefineryBlockEntity.SULFUR_OUTPUT};

    public RefineryProxyBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_REFINERY_PROXY.get(), position, state);
    }

    @Override
    @Nullable public RefineryBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof RefineryBlock)) return null;
        BlockPos core = RefineryBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof RefineryBlockEntity refinery ? refinery : null;
    }

    @Nullable public IFluidHandler fluidHandler(@Nullable Direction side) {
        RefineryBlockEntity target = target();
        return target != null && RefineryBlock.canConnectAt(getBlockState(), side)
                ? target.inputFluidHandler() : null;
    }

    @Override public boolean canConnect(Direction side) {
        return RefineryBlock.canConnectAt(getBlockState(), side);
    }
    @Override public long getReceiverSpeed() { return getMaxPower(); }
    @Override public boolean allowDirectProvision() { return true; }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return false; }
    @Override public int[] getSlotsForFace(Direction side) {
        return RefineryBlock.canConnectAt(getBlockState(), side) && target() != null ? SULFUR_ONLY : NO_SLOTS;
    }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == RefineryBlockEntity.SULFUR_OUTPUT
                && RefineryBlock.canConnectAt(getBlockState(), side) && target() != null;
    }
}
