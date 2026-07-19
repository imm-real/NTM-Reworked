package com.hbm.ntm.item;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.hazard.ContagionSystem;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Hidden MKUNICORN syringe administered with all the grace of a melee attack. */
public final class MkuSyringeItem extends Item {
    public MkuSyringeItem() {
        super(new Properties());
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (target.level() instanceof ServerLevel level) {
            ContagionSystem.infect(target);
            level.playSound(null, target.blockPosition(), ModSounds.SYRINGE.get(),
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            stack.shrink(1);

            if (HbmConfig.ENABLE_EXTENDED_LOGGING.get()) {
                HbmNtm.LOGGER.info("[MKU] {} used an MKU syringe!", attacker.getName().getString());
            }
        }

        // ItemSyringe#hitEntity performs the effect but deliberately returns false.
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("?").withStyle(ChatFormatting.RED));
    }
}
