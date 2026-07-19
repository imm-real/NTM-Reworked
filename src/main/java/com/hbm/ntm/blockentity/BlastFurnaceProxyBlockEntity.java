package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.BlastFurnaceBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/** Seven blast-furnace windows into one inventory and fluid system. */
public final class BlastFurnaceProxyBlockEntity extends InventoryProxyBlockEntity<BlastFurnaceBlockEntity> {
    public BlastFurnaceProxyBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_BLAST_FURNACE_PROXY.get(), position, state);
    }

    @Override
    @Nullable public BlastFurnaceBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof BlastFurnaceBlock)) return null;
        BlockPos core = BlastFurnaceBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof BlastFurnaceBlockEntity furnace ? furnace : null;
    }

    @Nullable public IFluidHandler fluidHandler(@Nullable Direction side) {
        Direction outward = BlastFurnaceBlock.portSide(getBlockState());
        BlastFurnaceBlockEntity furnace = target();
        return furnace != null && (side == null || side == outward) ? furnace.fluidHandler() : null;
    }
}
