package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.BreedingReactorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Both upper blocks expose the core inventory. Convenient, probably irradiated. */
public final class BreedingReactorProxyBlockEntity extends InventoryProxyBlockEntity<BreedingReactorBlockEntity> {
    public BreedingReactorProxyBlockEntity(BlockPos pos, BlockState state) {
        super(registeredType(), pos, state);
    }

    @SuppressWarnings("unchecked")
    private static BlockEntityType<BreedingReactorProxyBlockEntity> registeredType() {
        return (BlockEntityType<BreedingReactorProxyBlockEntity>) (BlockEntityType<?>)
                BuiltInRegistries.BLOCK_ENTITY_TYPE.get(BreedingReactorBlock.PROXY_BE_ID);
    }

    @Nullable @Override public BreedingReactorBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof BreedingReactorBlock)) return null;
        return level.getBlockEntity(BreedingReactorBlock.corePosition(worldPosition, getBlockState()))
                instanceof BreedingReactorBlockEntity reactor ? reactor : null;
    }
}
