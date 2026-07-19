package com.hbm.ntm.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class SawmillMachineBlockItem extends DescriptionBlockItem {
    private static final String MISSING_BLADE = "hbm_missing_blade";

    public SawmillMachineBlockItem(Block block, Properties properties) {
        super(block, properties,
                "block.hbm.machine_sawmill.desc.0",
                "block.hbm.machine_sawmill.desc.1",
                "block.hbm.machine_sawmill.desc.2");
    }

    public static ItemStack withoutBlade(SawmillMachineBlockItem item) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MISSING_BLADE, true);
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        return stack;
    }

    public static boolean isMissingBlade(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getBoolean(MISSING_BLADE);
    }
}
