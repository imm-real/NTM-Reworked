package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.DynamicSlagBlockEntity;
import com.hbm.ntm.item.FoundryScrapsItem;
import com.hbm.ntm.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Hidden moving slag from the foundry's least trustworthy opening. */
public final class DynamicSlagBlock extends BaseEntityBlock {
    public static final MapCodec<DynamicSlagBlock> CODEC = simpleCodec(DynamicSlagBlock::new);

    public DynamicSlagBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                             CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof DynamicSlagBlockEntity slag) {
            return box(0, 0, 0, 16, slag.height() * 16D, 16);
        }
        return Shapes.block();
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                      CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (!level.isClientSide) schedule(level, pos);
    }

    public static void schedule(Level level, BlockPos pos) {
        if (!level.isClientSide && level.getBlockState(pos).getBlock() instanceof DynamicSlagBlock) {
            level.scheduleTick(pos, level.getBlockState(pos).getBlock(), 1);
        }
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof DynamicSlagBlockEntity slag)
                || slag.material() == null || slag.amount() <= 0) {
            level.removeBlock(pos, false);
            return;
        }
        var material = slag.material();
        int amount = slag.amount();
        BlockPos belowPos = pos.below();
        if (belowPos.getY() >= level.getMinBuildHeight()) {
            if (level.getBlockState(belowPos).canBeReplaced()) {
                level.setBlock(belowPos, defaultBlockState(), Block.UPDATE_ALL);
                if (level.getBlockEntity(belowPos) instanceof DynamicSlagBlockEntity below) {
                    below.setContents(material, amount);
                    level.removeBlock(pos, false);
                    return;
                }
            } else if (level.getBlockEntity(belowPos) instanceof DynamicSlagBlockEntity below
                    && below.canAccept(material)) {
                int moved = below.add(material, amount);
                slag.remove(moved);
                if (slag.amount() <= 0) level.removeBlock(pos, false);
                else schedule(level, pos);
                return;
            }
        }

        List<BlockPos> open = new ArrayList<>(4);
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(direction);
            if (level.getBlockState(neighbor).canBeReplaced()) open.add(neighbor);
        }
        if (slag.amount() >= DynamicSlagBlockEntity.CAPACITY / 5 && !open.isEmpty()) {
            int spread = Math.max(slag.amount() / (open.size() * 2), 1);
            for (BlockPos neighbor : open) {
                if (slag.amount() <= 0) break;
                level.setBlock(neighbor, defaultBlockState(), Block.UPDATE_ALL);
                if (level.getBlockEntity(neighbor) instanceof DynamicSlagBlockEntity side) {
                    int moved = side.add(material, Math.min(spread, slag.amount()));
                    slag.remove(moved);
                }
            }
        }
        if (slag.amount() <= 0) level.removeBlock(pos, false);
        else schedule(level, pos);
    }

    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof DynamicSlagBlockEntity slag
                && slag.material() != null && slag.amount() > 0) {
            return List.of(FoundryScrapsItem.create(ModItems.SCRAPS.get(), slag.material(), slag.amount()));
        }
        return List.of();
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DynamicSlagBlockEntity(pos, state);
    }
}
