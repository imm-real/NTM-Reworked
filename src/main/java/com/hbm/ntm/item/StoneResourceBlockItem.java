package com.hbm.ntm.item;

import com.hbm.ntm.block.StoneResourceBlock;
import com.hbm.ntm.hazard.HazardCarrier;
import com.hbm.ntm.hazard.HazardProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.Block;

/** Stone resource variants sharing one item and one filing cabinet. */
public final class StoneResourceBlockItem extends BlockItem implements HazardCarrier {
    public StoneResourceBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack create(Item item, StoneResourceBlock.Type type, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY.with(StoneResourceBlock.TYPE, type));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(type.legacyMetadata()));
        return stack;
    }

    public static StoneResourceBlock.Type type(ItemStack stack) {
        StoneResourceBlock.Type type = stack.getOrDefault(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY).get(StoneResourceBlock.TYPE);
        if (type != null) return type;
        CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return model == null ? StoneResourceBlock.Type.HEMATITE
                : StoneResourceBlock.Type.byLegacyMetadata(model.value());
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return "block.hbm.stone_resource." + type(stack).getSerializedName();
    }

    @Override
    public HazardProfile hbm$getHazards(ItemStack stack) {
        return type(stack) == StoneResourceBlock.Type.ASBESTOS
                ? HazardProfile.NONE.withAsbestos(1.0F)
                : HazardProfile.NONE;
    }
}
