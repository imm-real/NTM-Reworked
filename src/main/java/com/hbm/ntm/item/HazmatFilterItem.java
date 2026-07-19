package com.hbm.ntm.item;

import com.hbm.ntm.hazard.HazardProtection;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.EnumSet;
import java.util.Set;

public final class HazmatFilterItem extends Item {
    private final FilterType type;

    public HazmatFilterItem(FilterType type) {
        super(new Properties().stacksTo(1).durability(20_000));
        this.type = type;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(helmet.getItem() instanceof FilterableMaskItem mask)
                || !mask.hbm$acceptsFilter(helmet, this)) {
            return InteractionResultHolder.pass(held);
        }
        ItemStack previous = MaskFilterStorage.installed(helmet);
        if (!level.isClientSide) {
            MaskFilterStorage.install(helmet, held.copyWithCount(1));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.GASMASK_SCREW.get(),
                    SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        if (!previous.isEmpty()) {
            return InteractionResultHolder.sidedSuccess(previous, level.isClientSide);
        }
        // ItemFilter manually zeroed the stack in 1.7.10, including in creative mode.
        held.shrink(1);
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    public boolean protects(HazardProtection protection) {
        return type.protections().contains(protection);
    }

    public FilterType type() {
        return type;
    }

    public enum FilterType {
        STANDARD("gas_mask_filter", EnumSet.of(HazardProtection.PARTICLE_COARSE, HazardProtection.PARTICLE_FINE,
                HazardProtection.GAS_LUNG, HazardProtection.GAS_BLISTERING, HazardProtection.BACTERIA)),
        MONO("gas_mask_filter_mono", EnumSet.of(HazardProtection.PARTICLE_COARSE,
                HazardProtection.GAS_MONOXIDE)),
        COMBO("gas_mask_filter_combo", EnumSet.of(HazardProtection.PARTICLE_COARSE, HazardProtection.PARTICLE_FINE,
                HazardProtection.GAS_LUNG, HazardProtection.GAS_BLISTERING, HazardProtection.BACTERIA,
                HazardProtection.GAS_MONOXIDE)),
        RAG("gas_mask_filter_rag", EnumSet.of(HazardProtection.PARTICLE_COARSE)),
        PISS("gas_mask_filter_piss", EnumSet.of(HazardProtection.PARTICLE_COARSE,
                HazardProtection.GAS_LUNG));

        private final String id;
        private final Set<HazardProtection> protections;

        FilterType(String id, Set<HazardProtection> protections) {
            this.id = id;
            this.protections = protections;
        }

        public String id() {
            return id;
        }

        public Set<HazardProtection> protections() {
            return protections;
        }

        public Item item() {
            return switch (this) {
                case STANDARD -> ModItems.GAS_MASK_FILTER.get();
                case MONO -> ModItems.GAS_MASK_FILTER_MONO.get();
                case COMBO -> ModItems.GAS_MASK_FILTER_COMBO.get();
                case RAG -> ModItems.GAS_MASK_FILTER_RAG.get();
                case PISS -> ModItems.GAS_MASK_FILTER_PISS.get();
            };
        }

        public static FilterType byId(String id) {
            for (FilterType type : values()) if (type.id.equals(id)) return type;
            return STANDARD;
        }
    }
}
