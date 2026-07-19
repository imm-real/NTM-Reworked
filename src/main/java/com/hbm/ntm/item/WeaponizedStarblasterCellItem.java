package com.hbm.ntm.item;

import com.hbm.ntm.config.HbmConfig;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Source rigged B92 cell: a fifty-second dropped-item fuse or immediate fire trigger. */
public final class WeaponizedStarblasterCellItem extends Item {
    public static final int FUSE_TICKS = 50 * 20;
    public static final int FLEIJA_RADIUS = 100;

    public WeaponizedStarblasterCellItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        Level level = entity.level();
        if (entity.tickCount > FUSE_TICKS || entity.isOnFire()) {
            if (level instanceof ServerLevel server) {
                if (HbmConfig.DANGEROUS_DROP_STAR.get()) {
                    B92Item.spawnFleija(server, entity.getX(), entity.getY(), entity.getZ(),
                            FLEIJA_RADIUS, FLEIJA_RADIUS);
                }
                entity.discard();
            }
        }

        int remaining = Math.max(1, FUSE_TICKS - entity.tickCount);
        double x = entity.getX() + level.random.nextGaussian() * entity.getBbWidth() / 2.0D;
        double y = entity.getY() + level.random.nextGaussian() * entity.getBbHeight();
        double z = entity.getZ() + level.random.nextGaussian() * entity.getBbWidth() / 2.0D;
        if (level.random.nextInt(FUSE_TICKS) >= remaining) {
            level.addParticle(DustParticleOptions.REDSTONE, x, y, z, 0.0D, 0.0D, 0.0D);
        } else {
            level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0D, 0.0D, 0.0D);
        }
        if (remaining < 100) {
            level.addParticle(ParticleTypes.LAVA, x, y, z, 0.0D, 0.0D, 0.0D);
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("A charged energy cell, rigged to explode"));
        tooltip.add(Component.literal("when left on the floor for too long."));
    }
}
