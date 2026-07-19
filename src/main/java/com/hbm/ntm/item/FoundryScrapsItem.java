package com.hbm.ntm.item;

import com.hbm.ntm.foundry.FoundryMaterial;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

/** Foundry scraps, now individually labeled for the recycling department. */
public final class FoundryScrapsItem extends Item {
    public FoundryScrapsItem() { super(new Properties().stacksTo(1)); }

    public static ItemStack create(Item item, FoundryMaterial material, int amount) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putString("material", material.id());
        tag.putInt("amount", amount);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(material.legacyId()));
        return stack;
    }

    public static FoundryMaterial.MaterialAmount contents(ItemStack stack) {
        if (!(stack.getItem() instanceof FoundryScrapsItem)) return null;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String id = tag.getString("material");
        for (FoundryMaterial material : FoundryMaterial.values()) {
            if (material.id().equals(id)) return new FoundryMaterial.MaterialAmount(material, tag.getInt("amount"));
        }
        return null;
    }

    @Override
    public Component getName(ItemStack stack) {
        FoundryMaterial.MaterialAmount contents = contents(stack);
        return contents == null ? Component.literal("Foundry Scraps")
                : Component.translatable("item.hbm.scraps", Component.translatable("hbmmat." + contents.material().id()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        FoundryMaterial.MaterialAmount contents = contents(stack);
        if (contents != null) tooltip.add(Component.literal(contents.amount() + " quanta / "
                + contents.amount() * 2 + "mB").withStyle(ChatFormatting.GRAY));
    }
}
