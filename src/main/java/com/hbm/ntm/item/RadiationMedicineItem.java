package com.hbm.ntm.item;

import com.hbm.ntm.radiation.RadiationData;
import com.hbm.ntm.radiation.RadiationSystem;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

public final class RadiationMedicineItem extends Item {
    public enum Treatment {
        RADAWAY(140, 0),
        RADAWAY_STRONG(350, 0),
        RADAWAY_FLUSH(500, 0),
        RAD_X(0, 3600),
        HERBAL(0, 0);

        private final int radAwayTicks;
        private final int radXTicks;

        Treatment(int radAwayTicks, int radXTicks) {
            this.radAwayTicks = radAwayTicks;
            this.radXTicks = radXTicks;
        }
    }

    private final Treatment treatment;

    public RadiationMedicineItem(Properties properties, Treatment treatment) {
        super(properties);
        this.treatment = treatment;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isRadAway()) {
            ItemStack result = level.isClientSide ? stack : applyTreatmentAndConsume(stack, player);
            return InteractionResultHolder.sidedSuccess(result, level.isClientSide);
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            return applyTreatmentAndConsume(stack, entity);
        }
        return stack;
    }

    private ItemStack applyTreatmentAndConsume(ItemStack stack, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            CriteriaTriggers.CONSUME_ITEM.trigger(player, stack);
            player.awardStat(Stats.ITEM_USED.get(this));
        }

        RadiationData data = RadiationSystem.data(entity);
        if (treatment.radAwayTicks > 0) {
            data.addRadAwayTicks(treatment.radAwayTicks);
        }
        if (treatment.radXTicks > 0) {
            data.refreshRadXTicks(treatment.radXTicks);
        }
        if (treatment == Treatment.HERBAL) {
            data.setAsbestos(0);
            data.setBlackLung(Math.min(data.blackLung(), RadiationData.MAX_BLACK_LUNG / 5));
            data.addRadiation(-100.0F);
            entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 12_000, 2));
            entity.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 12_000, 2));
            entity.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 2));
        }
        if (entity instanceof ServerPlayer player) {
            player.syncData(com.hbm.ntm.registry.ModAttachments.RADIATION);
        }

        if (!(entity instanceof Player player)) return stack;

        if (isRadAway()) {
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    ModSounds.RADAWAY.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            // ItemSimpleConsumable decrements RadAway even for creative players.
            stack.shrink(1);
            ItemStack emptyBag = new ItemStack(ModItems.IV_EMPTY.get());
            if (stack.isEmpty()) return emptyBag;
            if (!player.getInventory().add(emptyBag)) player.drop(emptyBag, false);
            return stack;
        }

        if (!player.hasInfiniteMaterials()) stack.shrink(1);
        return stack;
    }

    private boolean isRadAway() {
        return treatment == Treatment.RADAWAY
                || treatment == Treatment.RADAWAY_STRONG
                || treatment == Treatment.RADAWAY_FLUSH;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 10;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (treatment == Treatment.RAD_X) {
            tooltip.add(Component.translatable("item.hbm.radx.desc"));
        } else if (treatment == Treatment.HERBAL) {
            tooltip.add(Component.translatable("item.hbm.pill_herbal.desc.0"));
            tooltip.add(Component.translatable("item.hbm.pill_herbal.desc.1"));
        }
    }

    public Treatment treatment() {
        return treatment;
    }
}
