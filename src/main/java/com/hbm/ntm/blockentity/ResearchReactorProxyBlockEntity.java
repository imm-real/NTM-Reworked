package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.ResearchReactorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Lets both upper reactor blocks rummage through all twelve fuel slots. */
public final class ResearchReactorProxyBlockEntity
        extends InventoryProxyBlockEntity<ResearchReactorBlockEntity> {
    public ResearchReactorProxyBlockEntity(BlockPos pos, BlockState state) {
        super(registeredType(), pos, state);
    }

    @SuppressWarnings("unchecked")
    private static BlockEntityType<ResearchReactorProxyBlockEntity> registeredType() {
        return (BlockEntityType<ResearchReactorProxyBlockEntity>) (BlockEntityType<?>)
                BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ResearchReactorBlock.PROXY_BLOCK_ENTITY_ID);
    }

    @Nullable
    @Override
    public ResearchReactorBlockEntity target() {
        if (level == null || !(getBlockState().getBlock() instanceof ResearchReactorBlock)) return null;
        return level.getBlockEntity(ResearchReactorBlock.corePosition(worldPosition, getBlockState()))
                instanceof ResearchReactorBlockEntity reactor ? reactor : null;
    }
}
