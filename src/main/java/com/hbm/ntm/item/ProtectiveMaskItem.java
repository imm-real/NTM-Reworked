package com.hbm.ntm.item;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.hazard.HazardProtection;
import com.hbm.ntm.hazard.HazardProtectiveItem;
import com.hbm.ntm.radiation.RadiationProtectiveItem;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Standalone goggles, gas masks, and improvised half masks. */
public final class ProtectiveMaskItem extends ArmorItem
        implements HazardProtectiveItem, RadiationProtectiveItem, FilterableMaskItem {
    private static final ResourceLocation ASH_GLASSES_ARMOR_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/armor/goggles.png");
    private final MaskType maskType;

    public ProtectiveMaskItem(Holder<ArmorMaterial> material, MaskType maskType) {
        super(material, Type.HELMET, new Properties().durability(Type.HELMET.getDurability(maskType.durabilityMultiplier())));
        this.maskType = maskType;
    }

    public MaskType maskType() {
        return maskType;
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot,
                                            ArmorMaterial.Layer layer, boolean innerModel) {
        return maskType == MaskType.ASH_GLASSES ? ASH_GLASSES_ARMOR_TEXTURE : null;
    }

    @Override
    public float hbm$getRadiationResistance(ItemStack stack, LivingEntity wearer) {
        return maskType.radiationResistance();
    }

    @Override
    public boolean hbm$protects(ItemStack stack, LivingEntity wearer, HazardProtection protection) {
        if (maskType.intrinsicProtections().contains(protection)) return true;
        if (!maskType.filterable() || maskType.filterBlacklist().contains(protection)) return false;
        ItemStack filter = MaskFilterStorage.installed(stack);
        return filter.getItem() instanceof HazmatFilterItem item && item.protects(protection);
    }

    @Override
    public void hbm$damageProtection(ItemStack stack, LivingEntity wearer, HazardProtection protection, int amount) {
        if (!maskType.filterable() || maskType.intrinsicProtections().contains(protection)
                || maskType.filterBlacklist().contains(protection)) return;
        ItemStack filter = MaskFilterStorage.installed(stack);
        if (filter.getItem() instanceof HazmatFilterItem item && item.protects(protection)) {
            MaskFilterStorage.damage(stack, amount);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (maskType.filterable() && player.isShiftKeyDown()) {
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
        if (maskType.filterable()) {
            ItemStack filter = MaskFilterStorage.installed(stack);
            if (filter.isEmpty()) {
                tooltip.add(Component.translatable("tooltip.hbm.mask.no_filter").withStyle(ChatFormatting.RED));
            } else {
                tooltip.add(Component.translatable("tooltip.hbm.mask.installed_filter")
                        .withStyle(ChatFormatting.GOLD));
                int remaining = (filter.getMaxDamage() - filter.getDamageValue()) * 100
                        / Math.max(filter.getMaxDamage(), 1);
                tooltip.add(Component.literal("  ").append(filter.getHoverName()).append(" (" + remaining + "%)"));
            }
        }
        if (!maskType.filterBlacklist().isEmpty()) {
            tooltip.add(Component.translatable("tooltip.hbm.mask.never_protects").withStyle(ChatFormatting.RED));
            for (HazardProtection protection : maskType.filterBlacklist()) {
                tooltip.add(Component.literal(" -").append(Component.translatable(protection.translationKey()))
                        .withStyle(ChatFormatting.DARK_RED));
            }
        }
    }

    public enum MaskType {
        GOGGLES(15, 0.0F, false,
                EnumSet.of(HazardProtection.LIGHT, HazardProtection.SAND), EnumSet.noneOf(HazardProtection.class)),
        ASH_GLASSES(15, 0.0F, false,
                EnumSet.of(HazardProtection.LIGHT, HazardProtection.SAND), EnumSet.noneOf(HazardProtection.class)),
        GAS_MASK(15, 0.07F, true,
                EnumSet.of(HazardProtection.SAND, HazardProtection.LIGHT),
                EnumSet.of(HazardProtection.GAS_BLISTERING)),
        M65(15, 0.095F, true,
                EnumSet.of(HazardProtection.SAND), EnumSet.of(HazardProtection.GAS_BLISTERING)),
        LEATHER(15, 0.0F, true,
                EnumSet.noneOf(HazardProtection.class), EnumSet.of(HazardProtection.GAS_BLISTERING)),
        HALF_MASK(15, 0.0F, true,
                EnumSet.noneOf(HazardProtection.class),
                EnumSet.of(HazardProtection.GAS_LUNG, HazardProtection.GAS_BLISTERING,
                        HazardProtection.BACTERIA)),
        DAMP_RAG(150, 0.0F, false,
                EnumSet.of(HazardProtection.PARTICLE_COARSE), EnumSet.noneOf(HazardProtection.class)),
        SOAKED_RAG(150, 0.0F, false,
                EnumSet.of(HazardProtection.PARTICLE_COARSE, HazardProtection.GAS_LUNG),
                EnumSet.noneOf(HazardProtection.class));

        private final int durabilityMultiplier;
        private final float radiationResistance;
        private final boolean filterable;
        private final Set<HazardProtection> intrinsicProtections;
        private final Set<HazardProtection> filterBlacklist;

        MaskType(int durabilityMultiplier, float radiationResistance, boolean filterable,
                 Set<HazardProtection> intrinsicProtections, Set<HazardProtection> filterBlacklist) {
            this.durabilityMultiplier = durabilityMultiplier;
            this.radiationResistance = radiationResistance;
            this.filterable = filterable;
            this.intrinsicProtections = Set.copyOf(intrinsicProtections);
            this.filterBlacklist = Set.copyOf(filterBlacklist);
        }

        public int durabilityMultiplier() {
            return durabilityMultiplier;
        }

        public float radiationResistance() {
            return radiationResistance;
        }

        public boolean filterable() {
            return filterable;
        }

        public Set<HazardProtection> intrinsicProtections() {
            return intrinsicProtections;
        }

        public Set<HazardProtection> filterBlacklist() {
            return filterBlacklist;
        }
    }
}
