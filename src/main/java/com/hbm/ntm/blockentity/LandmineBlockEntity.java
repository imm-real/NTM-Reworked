package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.LandmineBlock;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.registry.ModStats;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Proximity logic for blocks that dislike feet. Covered mines sleep; primed mines do not. */
public final class LandmineBlockEntity extends BlockEntity {
    private boolean primed = false;
    private boolean waitingForPlayer = false;

    public LandmineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LANDMINE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LandmineBlockEntity mine) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!(state.getBlock() instanceof LandmineBlock landmine)) {
            return;
        }

        double range = landmine.type().range;
        double height = landmine.type().height;

        if (mine.waitingForPlayer) {
            range = 25.0D;
            height = 25.0D;
        } else if (!mine.primed) {
            range *= 2.0D;
            height *= 2.0D;
        }

        // A covered mine (non-air directly above) cannot trigger.
        if (!serverLevel.getBlockState(pos.above()).isAir()) {
            return;
        }

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        AABB area = new AABB(x - range, y - height, z - range, x + range + 1, y + height, z + range + 1);
        List<Entity> list = serverLevel.getEntitiesOfClass(Entity.class, area);

        for (Entity entity : list) {
            MobCategory category = entity.getType().getCategory();
            if (category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT
                    || category == MobCategory.UNDERGROUND_WATER_CREATURE || category == MobCategory.AMBIENT) {
                continue;
            }

            if (mine.waitingForPlayer) {
                if (entity instanceof Player) {
                    mine.waitingForPlayer = false;
                    mine.setChanged();
                    return;
                }
            } else if (entity instanceof LivingEntity) {
                if (mine.primed) {
                    landmine.explode(serverLevel, pos);
                    if (entity instanceof Player player) {
                        player.awardStat(Stats.CUSTOM.get(ModStats.MINES));
                    }
                }
                return;
            }
        }

        // With the doubled radius clear of living entities, prime once so the placer is never caught.
        if (!mine.primed && !mine.waitingForPlayer) {
            serverLevel.playSound(null, x, y, z, ModSounds.CHARGE_START.get(), SoundSource.BLOCKS,
                    3.0F, 1.0F);
            mine.primed = true;
            mine.setChanged();
        }
    }

    public boolean isPrimed() {
        return primed;
    }

    public boolean isWaitingForPlayer() {
        return waitingForPlayer;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        primed = tag.getBoolean("primed");
        waitingForPlayer = tag.getBoolean("waiting");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("primed", primed);
        tag.putBoolean("waiting", waitingForPlayer);
    }
}
