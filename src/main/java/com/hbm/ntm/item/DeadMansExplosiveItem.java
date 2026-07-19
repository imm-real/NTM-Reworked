package com.hbm.ntm.item;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.config.HbmConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Source strength-fifteen dangerous drop. */
public final class DeadMansExplosiveItem extends Item {
    public DeadMansExplosiveItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level() instanceof ServerLevel server) {
            if (HbmConfig.DANGEROUS_DROP_DEAD.get()) {
                // The old true flag meant block damage, not fire. Keep fire disabled.
                server.explode(entity, entity.getX(), entity.getY(), entity.getZ(), 15.0F, false,
                        Level.ExplosionInteraction.TNT);
                if (HbmConfig.ENABLE_EXTENDED_LOGGING.get()) {
                    HbmNtm.LOGGER.info("[DET] Detonated dead man's explosive at {} / {} / {}!",
                            (int) entity.getX(), (int) entity.getY(), (int) entity.getZ());
                }
            }
            entity.discard();
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Explodes when dropped!"));
        tooltip.add(Component.translatable("trait.drop").withStyle(ChatFormatting.RED));
    }
}
