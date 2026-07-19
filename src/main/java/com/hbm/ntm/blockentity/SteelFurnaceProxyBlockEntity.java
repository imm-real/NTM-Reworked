package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.SteelFurnaceBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Inventory proxy used by all 17 Steel Furnace dummy cells. */
public final class SteelFurnaceProxyBlockEntity extends InventoryProxyBlockEntity<SteelFurnaceBlockEntity> {
    public SteelFurnaceProxyBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.FURNACE_STEEL_PROXY.get(), position, state);
    }

    @Override
    @Nullable public SteelFurnaceBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof SteelFurnaceBlock)) return null;
        BlockPos core = SteelFurnaceBlock.corePosition(worldPosition, getBlockState());
        return level.getBlockEntity(core) instanceof SteelFurnaceBlockEntity furnace ? furnace : null;
    }

}
