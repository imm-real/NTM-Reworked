package com.hbm.ntm.item;

import com.hbm.ntm.hazard.HazardProtection;
import com.hbm.ntm.hazard.HazardProtectiveItem;
import com.hbm.ntm.radiation.RadiationProtectiveItem;
import com.hbm.ntm.registry.ModArmorMaterials;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public final class HazmatArmorItem extends ArmorItem
        implements RadiationProtectiveItem, HazardProtectiveItem, FilterableMaskItem {
    private final float radiationResistance;

    public HazmatArmorItem(Type type, float radiationResistance) {
        super(ModArmorMaterials.HAZMAT, type, new Properties().durability(type.getDurability(60)));
        this.radiationResistance = radiationResistance;
    }

    @Override
    public float hbm$getRadiationResistance(ItemStack stack, LivingEntity wearer) {
        return radiationResistance;
    }

    @Override
    public boolean hbm$protects(ItemStack stack, LivingEntity wearer, HazardProtection protection) {
        if (!isHelmet()) return false;
        if (protection == HazardProtection.SAND) return true;
        ItemStack filter = MaskFilterStorage.installed(stack);
        return filter.getItem() instanceof HazmatFilterItem item && item.protects(protection);
    }

    @Override
    public void hbm$damageProtection(ItemStack stack, LivingEntity wearer, HazardProtection protection, int amount) {
        if (!isHelmet() || protection == HazardProtection.SAND) return;
        ItemStack filter = MaskFilterStorage.installed(stack);
        if (filter.getItem() instanceof HazmatFilterItem item && item.protects(protection)) {
            MaskFilterStorage.damage(stack, amount);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isHelmet() && player.isShiftKeyDown()) {
            ItemStack filter = MaskFilterStorage.installed(stack);
            if (!filter.isEmpty()) {
                if (!level.isClientSide) {
                    MaskFilterStorage.remove(stack);
                    if (!player.getInventory().add(filter)) player.drop(filter, true);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.GASMASK_SCREW.get(),
                            SoundSource.PLAYERS, 1.0F, 1.0F);
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (!isHelmet()) return;
        ItemStack filter = MaskFilterStorage.installed(stack);
        if (filter.isEmpty()) {
            tooltip.add(Component.literal("No filter installed!").withStyle(ChatFormatting.RED));
            return;
        }
        tooltip.add(Component.literal("Installed filter:").withStyle(ChatFormatting.GOLD));
        int remaining = (filter.getMaxDamage() - filter.getDamageValue()) * 100 / Math.max(filter.getMaxDamage(), 1);
        tooltip.add(Component.literal("  ").append(filter.getHoverName()).append(" (" + remaining + "%)"));
    }

    public boolean isHelmet() {
        return getType() == Type.HELMET;
    }

    public static void installFilter(ItemStack helmet, ItemStack filter) {
        MaskFilterStorage.install(helmet, filter);
    }

    public static ItemStack installedFilter(ItemStack helmet) {
        return MaskFilterStorage.installed(helmet);
    }

    public static void removeFilter(ItemStack helmet) {
        MaskFilterStorage.remove(helmet);
    }

    public static void damageFilter(ItemStack helmet, int amount) {
        MaskFilterStorage.damage(helmet, amount);
    }
}
