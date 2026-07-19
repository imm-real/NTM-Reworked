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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
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

/** Powered M1TTY environment suit. Batteries not emotionally included. */
public final class EnvsuitArmorItem extends ArmorItem
        implements HeBatteryItem, RadiationProtectiveItem, HazardProtectiveItem {
    public static final long MAX_CHARGE = 100_000L;
    public static final long CHARGE_RATE = 1_000L;
    public static final long ARMOR_DAMAGE_COST = 250L;

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

    public EnvsuitArmorItem(Type type) {
        // One durability point is a decoy; armor damage is paid in HE first.
        super(ModArmorMaterials.ENVSUIT, type, new Properties().durability(1));
        this.radiationResistance = switch (type) {
            case HELMET -> 0.2F;
            case CHESTPLATE -> 0.4F;
            case LEGGINGS -> 0.3F;
            case BOOTS -> 0.1F;
            default -> 0.0F;
        };
    }

    @Override
    public long getCharge(ItemStack stack) {
        CompoundTag data = data(stack);
        return data.contains(CHARGE, Tag.TAG_ANY_NUMERIC)
                ? Mth.clamp(data.getLong(CHARGE), 0L, getMaxCharge(stack))
                : getMaxCharge(stack);
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
        long charge = getCharge(stack);
        long capacity = getMaxCharge(stack);
        setCharge(stack, amount >= capacity - charge ? capacity : charge + amount);
    }

    @Override
    public void setCharge(ItemStack stack, long amount) {
        CompoundTag data = data(stack);
        data.putLong(CHARGE, Mth.clamp(amount, 0L, getMaxCharge(stack)));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    @Override
    public void discharge(ItemStack stack, long amount) {
        if (amount <= 0L) return;
        long charge = getCharge(stack);
        setCharge(stack, amount >= charge ? 0L : charge - amount);
    }

    @Override
    public <T extends LivingEntity> int damageItem(
            ItemStack stack,
            int amount,
            @Nullable T entity,
            Consumer<Item> onBroken
    ) {
        // Creative players skip both wear and the HE bill.
        if (entity instanceof net.minecraft.world.entity.player.Player player && player.isCreative()) return 0;
        if (amount > 0) {
            long cost = amount > Long.MAX_VALUE / ARMOR_DAMAGE_COST
                    ? Long.MAX_VALUE
                    : amount * ARMOR_DAMAGE_COST;
            discharge(stack, cost);
        }
        return 0;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getCharge(stack) < getMaxCharge(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * getCharge(stack) / Math.max(1L, getMaxCharge(stack)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float fraction = (float) getCharge(stack) / Math.max(1L, getMaxCharge(stack));
        return Mth.hsvToRgb(Math.max(0.0F, fraction) / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Charge: " + BatteryPackItem.shortNumber(getCharge(stack))
                + " / " + BatteryPackItem.shortNumber(getMaxCharge(stack))));
        tooltip.add(Component.translatable("armor.fullSetBonus").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("  ")
                .append(Component.translatable(MobEffects.MOVEMENT_SPEED.value().getDescriptionId()))
                .append(", ")
                .append(Component.translatable(MobEffects.JUMP.value().getDescriptionId()))
                .withStyle(ChatFormatting.AQUA));
    }

    @Override
    public float hbm$getRadiationResistance(ItemStack stack, LivingEntity wearer) {
        return radiationResistance;
    }

    @Override
    public boolean hbm$protects(ItemStack stack, LivingEntity wearer, HazardProtection protection) {
        // The helmet carries FULL_PACKAGE protection even unpowered and without the set.
        return getType() == Type.HELMET && FULL_PACKAGE.contains(protection);
    }

    public boolean isPowered(ItemStack stack) {
        return getCharge(stack) > 0L;
    }

    public float radiationResistance() {
        return radiationResistance;
    }

    public static boolean hasFullSet(LivingEntity wearer) {
        return wearer.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(ModItems.ENVSUIT_HELMET.get())
                && wearer.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(ModItems.ENVSUIT_PLATE.get())
                && wearer.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).is(ModItems.ENVSUIT_LEGS.get())
                && wearer.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).is(ModItems.ENVSUIT_BOOTS.get());
    }

    public static boolean hasFullPoweredSet(LivingEntity wearer) {
        if (!hasFullSet(wearer)) return false;
        for (ItemStack armor : wearer.getArmorSlots()) {
            if (!(armor.getItem() instanceof EnvsuitArmorItem envsuit) || !envsuit.isPowered(armor)) return false;
        }
        return true;
    }

    public static boolean hasPoweredChest(LivingEntity wearer) {
        return wearer.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)
                .getItem() instanceof EnvsuitArmorItem;
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
}
