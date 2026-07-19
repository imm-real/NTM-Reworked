package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.FoundryMoldBlockEntity;
import com.hbm.ntm.item.FoundryMoldItem;
import com.hbm.ntm.item.FoundryScrapsItem;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
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

public class FoundryMoldBlock extends BaseEntityBlock {
    public static final MapCodec<FoundryMoldBlock> CODEC = simpleCodec(FoundryMoldBlock::new);
    private static final VoxelShape SMALL_SHAPE = Shapes.or(
            box(0, 0, 0, 16, 2, 16),
            box(0, 0, 0, 16, 8, 2), box(0, 0, 14, 16, 8, 16),
            box(0, 0, 0, 2, 8, 16), box(14, 0, 0, 16, 8, 16));
    private static final VoxelShape LARGE_SHAPE = Shapes.or(
            box(0, 0, 0, 16, 2, 16),
            box(0, 0, 0, 16, 16, 2), box(0, 0, 14, 16, 16, 16),
            box(0, 0, 0, 2, 16, 16), box(14, 0, 0, 16, 16, 16));
    private final boolean large;

    public FoundryMoldBlock(Properties properties) { this(properties, false); }
    protected FoundryMoldBlock(Properties properties, boolean large) {
        super(properties);
        this.large = large;
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return large ? LARGE_SHAPE : SMALL_SHAPE;
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return large ? LARGE_SHAPE : SMALL_SHAPE;
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                        Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FoundryMoldBlockEntity mold)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!mold.getItem(FoundryMoldBlockEntity.OUTPUT).isEmpty()) {
            if (!level.isClientSide) give(player, mold.removeItem(FoundryMoldBlockEntity.OUTPUT, 64));
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (mold.acceptsMold(stack) && mold.getItem(FoundryMoldBlockEntity.MOLD).isEmpty()) {
            if (!level.isClientSide) {
                mold.setItem(FoundryMoldBlockEntity.MOLD, stack.copyWithCount(1));
                if (!player.getAbilities().instabuild) stack.shrink(1);
                level.playSound(null, pos, ModSounds.UPGRADE_PLUG.get(), SoundSource.BLOCKS, 1F, 1F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(ModItems.SCREWDRIVER.get())) {
            if (mold.getItem(FoundryMoldBlockEntity.MOLD).isEmpty() || mold.amount() > 0) return ItemInteractionResult.FAIL;
            if (!level.isClientSide) {
                give(player, mold.removeItem(FoundryMoldBlockEntity.MOLD, 1));
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(ItemTags.SHOVELS)) {
            if (!level.isClientSide && mold.material() != null && mold.amount() > 0) {
                give(player, FoundryScrapsItem.create(ModItems.SCRAPS.get(), mold.material(), mold.amount()));
                mold.clearMolten();
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                         Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof FoundryMoldBlockEntity mold
                && !mold.getItem(FoundryMoldBlockEntity.OUTPUT).isEmpty()) {
            if (!level.isClientSide) give(player, mold.removeItem(FoundryMoldBlockEntity.OUTPUT, 64));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof FoundryMoldBlockEntity mold) {
            if (mold.material() != null && mold.amount() > 0) Containers.dropItemStack(level, pos.getX() + .5, pos.getY() + .5,
                    pos.getZ() + .5, FoundryScrapsItem.create(ModItems.SCRAPS.get(), mold.material(), mold.amount()));
            Containers.dropContents(level, pos, mold);
            mold.clearContent();
        }
        super.onRemove(state, level, pos, next, moved);
    }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof FoundryMoldBlockEntity mold
                && mold.amount() > 0 && mold.amount() >= mold.capacity()) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + .25 + random.nextDouble() * .5,
                    pos.getY() + (large ? .9 : .5), pos.getZ() + .25 + random.nextDouble() * .5, 0, 0, 0);
        }
    }

    private static void give(Player player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack, false);
        player.inventoryMenu.broadcastChanges();
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new FoundryMoldBlockEntity(pos, state); }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.FOUNDRY_MOLD.get(), FoundryMoldBlockEntity::tick);
    }
}
