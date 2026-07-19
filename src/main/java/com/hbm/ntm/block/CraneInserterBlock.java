package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.CraneInserterBlockEntity;
import com.hbm.ntm.conveyor.ConveyorEnterable;
import com.hbm.ntm.entity.MovingConveyorItemEntity;
import com.hbm.ntm.entity.MovingConveyorPackageEntity;
import com.hbm.ntm.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Accepts loose conveyor items/packages and inserts them into the configured inventory. */
public final class CraneInserterBlock extends AbstractCraneBlock implements ConveyorEnterable {
    public static final MapCodec<CraneInserterBlock> CODEC = simpleCodec(CraneInserterBlock::new);

    public CraneInserterBlock(Properties properties) {
        super(properties);
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CraneInserterBlockEntity(pos, state);
    }

    @Nullable @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.CRANE_INSERTER.get(),
                CraneInserterBlockEntity::tick);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CraneInserterBlockEntity inserter
                ? inserter.comparatorOutput() : 0;
    }

    @Override
    public boolean canConveyorItemEnter(Level level, BlockPos pos, Direction side,
                                        MovingConveyorItemEntity item) {
        return level.getBlockState(pos).getValue(INPUT) == side;
    }

    @Override
    public void onConveyorItemEnter(Level level, BlockPos pos, Direction side,
                                    MovingConveyorItemEntity item) {
        if (level.getBlockEntity(pos) instanceof CraneInserterBlockEntity inserter) {
            inserter.accept(item.getItemStack());
        }
    }

    @Override
    public boolean canConveyorPackageEnter(Level level, BlockPos pos, Direction side,
                                           MovingConveyorPackageEntity conveyorPackage) {
        return true;
    }

    @Override
    public void onConveyorPackageEnter(Level level, BlockPos pos, Direction side,
                                       MovingConveyorPackageEntity conveyorPackage) {
        if (level.getBlockEntity(pos) instanceof CraneInserterBlockEntity inserter) {
            for (ItemStack stack : conveyorPackage.getItemStacks()) inserter.accept(stack);
        }
    }
}
