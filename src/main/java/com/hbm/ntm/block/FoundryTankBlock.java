package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.FoundryTankBlockEntity;
import com.hbm.ntm.item.FoundryScrapsItem;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Source four-block foundry storage basin. */
public final class FoundryTankBlock extends BaseEntityBlock {
    public static final MapCodec<FoundryTankBlock> CODEC = simpleCodec(FoundryTankBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            box(0, 0, 0, 16, 2, 16),
            box(0, 0, 0, 16, 16, 2), box(0, 0, 14, 16, 16, 16),
            box(0, 0, 0, 2, 16, 16), box(14, 0, 0, 16, 16, 16));

    public FoundryTankBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                             CollisionContext context) { return SHAPE; }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                      CollisionContext context) { return SHAPE; }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                         Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ItemTags.SHOVELS)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FoundryTankBlockEntity tank
                && tank.material() != null && tank.amount() > 0) {
            ItemStack scraps = FoundryScrapsItem.create(ModItems.SCRAPS.get(), tank.material(), tank.amount());
            if (!player.getInventory().add(scraps)) player.drop(scraps, false);
            player.inventoryMenu.broadcastChanges();
            tank.clearMolten();
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof FoundryTankBlockEntity tank
                && tank.material() != null && tank.amount() > 0) {
            Containers.dropItemStack(level, pos.getX() + .5D, pos.getY() + .5D, pos.getZ() + .5D,
                    FoundryScrapsItem.create(ModItems.SCRAPS.get(), tank.material(), tank.amount()));
            tank.clearMolten();
        }
        super.onRemove(state, level, pos, next, moved);
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FoundryTankBlockEntity(pos, state);
    }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.FOUNDRY_TANK.get(), FoundryTankBlockEntity::tick);
    }
}
