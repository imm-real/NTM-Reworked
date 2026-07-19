package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.ArcWelderBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class ArcWelderProxyBlockEntity extends InventoryProxyBlockEntity<ArcWelderBlockEntity>
        implements HeReceiverProxy {
    public ArcWelderProxyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_ARC_WELDER_PROXY.get(), pos, state);
    }

    @Override
    @Nullable public ArcWelderBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof ArcWelderBlock)) return null;
        BlockPos core = ArcWelderBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof ArcWelderBlockEntity welder ? welder : null;
    }

    public IFluidHandler fluidHandler() {
        ArcWelderBlockEntity target = target();
        return target == null ? null : target.fluidHandler();
    }

    // Source TileEntityProxyCombo delegates the core receiver's unrestricted connector,
    // while the ten perimeter points separately govern periodic cable subscriptions.
    @Override public boolean canConnect(Direction side) { return side != null; }
    @Override public int[] getSlotsForFace(Direction side) {
        return target() == null ? NO_SLOTS : ArcWelderBlock.automationSlots(getBlockState());
    }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        ArcWelderBlockEntity t = target();
        return t != null && slot < ArcWelderBlockEntity.INPUT_END && t.canPlaceItem(slot, stack);
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return target() != null && slot == ArcWelderBlockEntity.OUTPUT;
    }
}
