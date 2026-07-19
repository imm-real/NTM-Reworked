package com.hbm.ntm.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class CustomLoreItem extends Item {
    private final String[] descriptionKeys;

    public CustomLoreItem(String... descriptionKeys) {
        this(new Item.Properties(), descriptionKeys);
    }

    public CustomLoreItem(Item.Properties properties, String... descriptionKeys) {
        super(properties);
        this.descriptionKeys = descriptionKeys;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        for (String key : descriptionKeys) tooltip.add(Component.translatable(key));
    }
}
