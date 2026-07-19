package com.hbm.ntm.armor;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.item.ArmorCladdingItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class ArmorModEvents {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final ResourceLocation[] IRON_MODIFIERS = {
            id("iron_cladding_helmet"), id("iron_cladding_chestplate"),
            id("iron_cladding_leggings"), id("iron_cladding_boots")
    };

    private ArmorModEvents() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ArmorModEvents::onEntityTick);
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getEntity() instanceof LivingEntity living) updateIronCladding(living);
        else if (event.getEntity() instanceof ItemEntity item) updateObsidianSkin(item);
    }

    private static void updateIronCladding(LivingEntity living) {
        var attribute = living.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (attribute == null) return;
        for (int index = 0; index < SLOTS.length; index++) {
            ResourceLocation id = IRON_MODIFIERS[index];
            attribute.removeModifier(id);
            ItemStack armor = living.getItemBySlot(SLOTS[index]);
            if (ArmorModHandler.hasCladdingEffect(armor, ArmorCladdingItem.Effect.IRON,
                    living.registryAccess())) {
                attribute.addOrUpdateTransientModifier(new AttributeModifier(
                        id, 0.5D, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    private static void updateObsidianSkin(ItemEntity entity) {
        if (!entity.isInvulnerable() && ArmorModHandler.hasCladdingEffect(entity.getItem(),
                ArmorCladdingItem.Effect.OBSIDIAN, entity.registryAccess())) entity.setInvulnerable(true);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path);
    }
}
