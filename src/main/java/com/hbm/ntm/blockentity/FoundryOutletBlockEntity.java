package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.FoundryOutletBlock;
import com.hbm.ntm.foundry.FoundryMaterial;
import com.hbm.ntm.foundry.MoltenAcceptor;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/** Redstone/filter-controlled foundry outlet; the spill variant creates dynamic slag. */
public final class FoundryOutletBlockEntity extends AbstractFoundryBlockEntity {
    private FoundryMaterial filter;
    private boolean invertFilter;
    private boolean invertRedstone;

    public FoundryOutletBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FOUNDRY_OUTLET.get(), pos, state);
    }

    @Override public int capacity() { return 0; }
    @Override public boolean canAcceptPour(FoundryMaterial incoming, int offered, Direction inputSide) { return false; }
    @Override public int acceptPour(FoundryMaterial incoming, int offered, Direction inputSide) { return 0; }

    public FoundryMaterial filter() { return filter; }
    public boolean invertFilter() { return invertFilter; }
    public boolean invertRedstone() { return invertRedstone; }

    public void setFilter(FoundryMaterial filter) { this.filter = filter; sync(); }
    public void clearFilter() { filter = null; invertFilter = false; sync(); }
    public void toggleFilterInversion() { invertFilter = !invertFilter; sync(); }
    public void toggleRedstoneInversion() { invertRedstone = !invertRedstone; sync(); }

    public boolean isClosed() {
        return level != null && (invertRedstone ^ level.hasNeighborSignal(worldPosition));
    }

    public boolean isSlagTap() { return getBlockState().is(ModBlocks.FOUNDRY_SLAGTAP.get()); }

    @Override public boolean canAcceptFlow(FoundryMaterial incoming, int offered, Direction inputSide) {
        if (incoming == null || offered <= 0 || isClosed() || !matchesFilter(incoming)) return false;
        Direction facing = getBlockState().getValue(FoundryOutletBlock.FACING);
        if (inputSide != facing.getOpposite()) return false;
        return isSlagTap() ? canSpill(incoming) : pouringTarget(incoming, offered) != null;
    }

    @Override public int acceptFlow(FoundryMaterial incoming, int offered, Direction inputSide) {
        if (!canAcceptFlow(incoming, offered, inputSide) || level == null || level.isClientSide) return 0;
        int accepted;
        double targetY;
        if (isSlagTap()) {
            SpillTarget target = spillTarget(incoming);
            if (target == null) return 0;
            DynamicSlagBlockEntity slag = ensureSlag(target.position());
            if (slag == null) return 0;
            accepted = slag.add(incoming, offered);
            targetY = target.position().getY() + slag.height();
        } else {
            PourTarget target = pouringTarget(incoming, offered);
            if (target == null) return 0;
            accepted = target.acceptor().acceptPour(incoming, offered, Direction.UP);
            targetY = target.position().getY() + .5D;
        }
        if (accepted > 0 && level instanceof ServerLevel server) emitPourParticles(server, incoming, targetY);
        return accepted;
    }

    private boolean matchesFilter(FoundryMaterial incoming) {
        return filter == null || !((filter == incoming) ^ !invertFilter);
    }

    private PourTarget pouringTarget(FoundryMaterial incoming, int offered) {
        if (level == null) return null;
        for (int drop = 1; drop <= 4; drop++) {
            BlockPos targetPos = worldPosition.below(drop);
            BlockEntity target = level.getBlockEntity(targetPos);
            if (target instanceof MoltenAcceptor acceptor) {
                return acceptor.canAcceptPour(incoming, offered, Direction.UP)
                        ? new PourTarget(targetPos, acceptor) : null;
            }
            BlockState targetState = level.getBlockState(targetPos);
            if (!targetState.isAir() && !targetState.canBeReplaced()) return null;
        }
        return null;
    }

    private boolean canSpill(FoundryMaterial incoming) {
        SpillTarget target = spillTarget(incoming);
        if (target == null || level == null) return false;
        if (level.getBlockEntity(target.position()) instanceof DynamicSlagBlockEntity slag) return slag.canAccept(incoming);
        return level.getBlockState(target.position()).canBeReplaced();
    }

    private SpillTarget spillTarget(FoundryMaterial incoming) {
        if (level == null) return null;
        for (int drop = 1; drop <= 15 && worldPosition.getY() - drop >= level.getMinBuildHeight(); drop++) {
            BlockPos hit = worldPosition.below(drop);
            BlockState state = level.getBlockState(hit);
            if (state.isAir() || state.canBeReplaced()) continue;
            if (level.getBlockEntity(hit) instanceof DynamicSlagBlockEntity slag && slag.canAccept(incoming)) {
                return new SpillTarget(hit);
            }
            BlockPos above = hit.above();
            if (level.getBlockState(above).canBeReplaced()) return new SpillTarget(above);
            return null;
        }
        return null;
    }

    private DynamicSlagBlockEntity ensureSlag(BlockPos position) {
        if (level == null) return null;
        if (!(level.getBlockEntity(position) instanceof DynamicSlagBlockEntity)) {
            if (!level.getBlockState(position).canBeReplaced()) return null;
            level.setBlock(position, ModBlocks.DYNAMIC_SLAG.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        return level.getBlockEntity(position) instanceof DynamicSlagBlockEntity slag ? slag : null;
    }

    private void emitPourParticles(ServerLevel level, FoundryMaterial material, double targetY) {
        int color = material.moltenColor();
        Vector3f rgb = new Vector3f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F,
                (color & 255) / 255F);
        DustParticleOptions particle = new DustParticleOptions(rgb, 1F);
        double x = worldPosition.getX() + .5D;
        double z = worldPosition.getZ() + .5D;
        for (double y = targetY; y <= worldPosition.getY() + .125D; y += .25D) {
            level.sendParticles(particle, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (filter != null) tag.putString("filter", filter.id());
        tag.putBoolean("invert_filter", invertFilter);
        tag.putBoolean("invert_redstone", invertRedstone);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        filter = FoundryMaterial.byId(tag.getString("filter"));
        invertFilter = tag.getBoolean("invert_filter");
        invertRedstone = tag.getBoolean("invert_redstone");
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (filter != null) tag.putString("filter", filter.id());
        tag.putBoolean("invert_filter", invertFilter);
        tag.putBoolean("invert_redstone", invertRedstone);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        filter = FoundryMaterial.byId(tag.getString("filter"));
        invertFilter = tag.getBoolean("invert_filter");
        invertRedstone = tag.getBoolean("invert_redstone");
    }

    private record PourTarget(BlockPos position, MoltenAcceptor acceptor) { }
    private record SpillTarget(BlockPos position) { }
}
