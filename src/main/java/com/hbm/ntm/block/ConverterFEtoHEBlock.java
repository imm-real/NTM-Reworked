package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.ConverterFEtoHEBlockEntity;
import com.hbm.ntm.blockentity.MachineShredderBlockEntity;
import com.hbm.ntm.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public final class ConverterFEtoHEBlock extends BaseEntityBlock {
    public static final MapCodec<ConverterFEtoHEBlock> CODEC = simpleCodec(ConverterFEtoHEBlock::new);

    public ConverterFEtoHEBlock(Properties properties) { super(properties); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, position, state, placer, stack);
        if (level.getBlockEntity(position) instanceof MachineShredderBlockEntity shredder
                && stack.has(DataComponents.CUSTOM_NAME)) {
            shredder.setCustomName(stack.getHoverName());
        }
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConverterFEtoHEBlockEntity(pos, state);
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        return createTickerHelper(type, ModBlockEntities.MACHINE_CONVERTER_FE_HE.get(), ConverterFEtoHEBlockEntity::tick);
    }
}
