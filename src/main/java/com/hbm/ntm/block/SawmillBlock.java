package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.SawmillBlockEntity;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Direct item interaction for the Stirling Sawmill input and output. */
public final class SawmillBlock extends ThermalMultiblockBlock {
    public static final MapCodec<SawmillBlock> CODEC = simpleCodec(SawmillBlock::new);

    public SawmillBlock(Properties properties) {
        super(properties, Kind.SAWMILL);
    }

    @Override
    protected MapCodec<? extends SawmillBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position,
                                  CollisionContext context) {
        return sourceShape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos position,
                                           CollisionContext context) {
        return sourceShape(state);
    }

    private static VoxelShape sourceShape(BlockState state) {
        Direction facing = state.getValue(FACING);
        double offsetX = state.getValue(CORE_X) - 1 + 0.5D;
        double offsetY = state.getValue(CORE_Y) - 1;
        double offsetZ = state.getValue(CORE_Z) - 1 + 0.5D;
        VoxelShape shape = Shapes.empty();
        shape = Shapes.or(shape, sourceBox(facing, offsetX, offsetY, offsetZ,
                -1.5D, 0.0D, -1.5D, 1.5D, 1.0D, 1.5D));
        shape = Shapes.or(shape, sourceBox(facing, offsetX, offsetY, offsetZ,
                -1.25D, 1.0D, -0.5D, -0.625D, 1.875D, 0.5D));
        return Shapes.or(shape, sourceBox(facing, offsetX, offsetY, offsetZ,
                -0.625D, 1.0D, -1.0D, 1.375D, 2.0D, 1.0D));
    }

    /** Rotates the old dummy AABB around UP, in that order. */
    private static VoxelShape sourceBox(Direction facing, double offsetX, double offsetY, double offsetZ,
                                        double minX, double minY, double minZ,
                                        double maxX, double maxY, double maxZ) {
        double rotatedMinX;
        double rotatedMaxX;
        double rotatedMinZ;
        double rotatedMaxZ;
        switch (facing) {
            case WEST -> {
                rotatedMinX = minX; rotatedMaxX = maxX;
                rotatedMinZ = minZ; rotatedMaxZ = maxZ;
            }
            case NORTH -> {
                rotatedMinX = -maxZ; rotatedMaxX = -minZ;
                rotatedMinZ = minX; rotatedMaxZ = maxX;
            }
            case EAST -> {
                rotatedMinX = -maxX; rotatedMaxX = -minX;
                rotatedMinZ = -maxZ; rotatedMaxZ = -minZ;
            }
            case SOUTH -> {
                rotatedMinX = minZ; rotatedMaxX = maxZ;
                rotatedMinZ = -maxX; rotatedMaxZ = -minX;
            }
            default -> throw new IllegalStateException("Sawmill must face horizontally");
        }
        return box((offsetX + rotatedMinX) * 16.0D, (offsetY + minY) * 16.0D,
                (offsetZ + rotatedMinZ) * 16.0D, (offsetX + rotatedMaxX) * 16.0D,
                (offsetY + maxY) * 16.0D, (offsetZ + rotatedMaxZ) * 16.0D);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position,
                                                Player player, BlockHitResult hitResult) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockPos core = corePosition(position, state);
        if (!(level.getBlockEntity(core) instanceof SawmillBlockEntity sawmill)) {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getMainHandItem();
        if (!sawmill.hasBlade() && held.is(ModItems.SAWBLADE.get())) {
            held.shrink(1); // The 1.7.10 machine consumed the blade even in creative mode.
            sawmill.setHasBlade(true);
            level.playSound(null, position, ModSounds.UPGRADE_PLUG.get(), SoundSource.PLAYERS, 1.5F, 0.75F);
            return InteractionResult.SUCCESS;
        }

        if (!sawmill.getItem(SawmillBlockEntity.OUTPUT_SLOT).isEmpty()
                || !sawmill.getItem(SawmillBlockEntity.BYPRODUCT_SLOT).isEmpty()) {
            for (int slot = SawmillBlockEntity.OUTPUT_SLOT; slot <= SawmillBlockEntity.BYPRODUCT_SLOT; slot++) {
                ItemStack output = sawmill.removeItemNoUpdate(slot);
                if (!output.isEmpty() && !player.getInventory().add(output)) {
                    player.drop(output, false);
                }
            }
            player.containerMenu.broadcastChanges();
            sawmill.setChanged();
            return InteractionResult.SUCCESS;
        }

        if (sawmill.getItem(SawmillBlockEntity.INPUT_SLOT).isEmpty()
                && !held.isEmpty() && !sawmill.outputFor(held).isEmpty()) {
            sawmill.setItem(SawmillBlockEntity.INPUT_SLOT, held.copyWithCount(1));
            held.shrink(1); // Also intentionally consumes in creative, matching the old block.
            player.containerMenu.broadcastChanges();
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
