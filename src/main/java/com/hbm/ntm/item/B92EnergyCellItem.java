package com.hbm.ntm.item;

import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;

import java.util.List;

/** B92 cell that quietly drains the gun and never returns the favor. */
public final class B92EnergyCellItem extends Item {
    public static final int CAPACITY = 25;
    private static final String ENERGY = "energy";

    public B92EnergyCellItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof Player player) || energy(stack) >= CAPACITY) return;
        for (ItemStack candidate : player.getInventory().items) {
            if (!candidate.is(ModItems.GUN_B92.get())) continue;
            int gunEnergy = B92Item.energy(candidate);
            if (gunEnergy > 1) {
                B92Item.setEnergy(candidate, gunEnergy - 1);
                setEnergy(stack, energy(stack) + 1);
                return;
            }
        }
    }

    public static int energy(ItemStack stack) { return data(stack).getInt(ENERGY); }

    public static void setEnergy(ItemStack stack, int value) {
        CompoundTag tag = data(stack);
        tag.putInt(ENERGY, value);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        if (value == CAPACITY) stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
    }

    public static ItemStack fullCell() {
        ItemStack stack = new ItemStack(ModItems.GUN_B92_AMMO.get());
        setEnergy(stack, CAPACITY);
        return stack;
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Draws energy from the B92, allowing you to"));
        tooltip.add(Component.literal("reload it an additional 25 times."));
        tooltip.add(Component.literal("The cell will permanently hold its charge,"));
        tooltip.add(Component.literal("it is not meant to be used as a battery enhancement"));
        tooltip.add(Component.literal("for the B92, but rather as a bomb."));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("Charges: " + energy(stack) + " / " + CAPACITY));
    }
}
