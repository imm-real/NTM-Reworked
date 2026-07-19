package com.hbm.ntm.item;

import com.hbm.ntm.hazard.HazardProtection;
import com.hbm.ntm.hazard.HazardProtectiveItem;
import com.hbm.ntm.radiation.RadiationProtectiveItem;
import com.hbm.ntm.registry.ModArmorMaterials;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Billion-HE DNT Nano Suit for modest household errands. */
public final class DntArmorItem extends ArmorItem
        implements HeBatteryItem, RadiationProtectiveItem, HazardProtectiveItem {
    public static final long MAX_CHARGE = 1_000_000_000L;
    public static final long CHARGE_RATE = 1_000_000L;
    public static final long ARMOR_DAMAGE_COST = 100_000L;
    public static final long IDLE_DRAIN = 115L;

    private static final String CHARGE = "charge";
    private static final Set<HazardProtection> FULL_PACKAGE = EnumSet.of(
            HazardProtection.PARTICLE_COARSE,
            HazardProtection.PARTICLE_FINE,
            HazardProtection.GAS_LUNG,
            HazardProtection.BACTERIA,
            HazardProtection.GAS_BLISTERING,
            HazardProtection.GAS_MONOXIDE,
            HazardProtection.LIGHT,
            HazardProtection.SAND
    );

    private final float radiationResistance;

    public DntArmorItem(Type type) {
        super(ModArmorMaterials.DNT_NANO, type, new Properties().durability(1));
        radiationResistance = switch (type) {
            case HELMET -> 1.0F;
            case CHESTPLATE -> 2.0F;
            case LEGGINGS -> 1.5F;
            case BOOTS -> 0.5F;
            default -> 0.0F;
        };
    }

    @Override
    public long getCharge(ItemStack stack) {
        CompoundTag data = data(stack);
        return data.contains(CHARGE, Tag.TAG_ANY_NUMERIC)
                ? Mth.clamp(data.getLong(CHARGE), 0L, MAX_CHARGE)
                : MAX_CHARGE;
    }

    @Override
    public long getMaxCharge(ItemStack stack) {
        return MAX_CHARGE;
    }

    @Override
    public long getChargeRate(ItemStack stack) {
        return CHARGE_RATE;
    }

    @Override
    public long getDischargeRate(ItemStack stack) {
        return 0L;
    }

    @Override
    public void charge(ItemStack stack, long amount) {
        if (amount <= 0L) return;
        long current = getCharge(stack);
        setCharge(stack, amount >= MAX_CHARGE - current ? MAX_CHARGE : current + amount);
    }

    @Override
    public void setCharge(ItemStack stack, long amount) {
        CompoundTag data = data(stack);
        data.putLong(CHARGE, Mth.clamp(amount, 0L, MAX_CHARGE));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    @Override
    public void discharge(ItemStack stack, long amount) {
        if (amount <= 0L) return;
        long current = getCharge(stack);
        setCharge(stack, amount >= current ? 0L : current - amount);
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount,
                                                    @Nullable T entity, Consumer<Item> onBroken) {
        if (entity != null && entity.hasInfiniteMaterials()) return 0;
        if (amount > 0) {
            long cost = amount > Long.MAX_VALUE / ARMOR_DAMAGE_COST
                    ? Long.MAX_VALUE : amount * ARMOR_DAMAGE_COST;
            discharge(stack, cost);
        }
        return 0;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getCharge(stack) < MAX_CHARGE;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * getCharge(stack) / MAX_CHARGE);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb((float) getCharge(stack) / MAX_CHARGE / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Charge: " + BatteryPackItem.shortNumber(getCharge(stack))
                + " / " + BatteryPackItem.shortNumber(MAX_CHARGE)));
        tooltip.add(Component.translatable("armor.fullSetBonus").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("  ")
                .append(Component.translatable(MobEffects.DAMAGE_BOOST.value().getDescriptionId()))
                .append(" X, ")
                .append(Component.translatable(MobEffects.DIG_SPEED.value().getDescriptionId()))
                .append(" VIII, ")
                .append(Component.translatable(MobEffects.JUMP.value().getDescriptionId()))
                .append(" III")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  ").append(Component.translatable("armor.vats"))
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.literal("  ").append(Component.translatable("armor.geigerSound"))
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("  ").append(Component.translatable("armor.thermal"))
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.literal("  ").append(Component.translatable("armor.hardLanding"))
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.literal("  ").append(Component.translatable("armor.rocketBoots"))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  ").append(Component.translatable("armor.fastFall"))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  ").append(Component.translatable("armor.sprintBoost"))
                .withStyle(ChatFormatting.AQUA));
    }

    @Override
    public float hbm$getRadiationResistance(ItemStack stack, LivingEntity wearer) {
        return radiationResistance;
    }

    @Override
    public boolean hbm$protects(ItemStack stack, LivingEntity wearer, HazardProtection protection) {
        return getType() == Type.HELMET && FULL_PACKAGE.contains(protection);
    }

    public float radiationResistance() {
        return radiationResistance;
    }

    public boolean isPowered(ItemStack stack) {
        return getCharge(stack) > 0L;
    }

    public static boolean hasFullSet(LivingEntity wearer) {
        return wearer.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.DNS_HELMET.get())
                && wearer.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.DNS_PLATE.get())
                && wearer.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.DNS_LEGS.get())
                && wearer.getItemBySlot(EquipmentSlot.FEET).is(ModItems.DNS_BOOTS.get());
    }

    public static boolean hasFullPoweredSet(LivingEntity wearer) {
        if (!hasFullSet(wearer)) return false;
        for (ItemStack armor : wearer.getArmorSlots()) {
            if (!(armor.getItem() instanceof DntArmorItem dnt) || !dnt.isPowered(armor)) return false;
        }
        return true;
    }

    public static boolean hasChest(LivingEntity wearer) {
        return wearer.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.DNS_PLATE.get());
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
}
