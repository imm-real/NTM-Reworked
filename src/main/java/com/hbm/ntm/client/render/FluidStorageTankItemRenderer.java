package com.hbm.ntm.client.render;

import com.hbm.ntm.blockentity.FluidStorageTankBlockEntity;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class FluidStorageTankItemRenderer extends BlockEntityWithoutLevelRenderer {
    public FluidStorageTankItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        FluidIdentifierItem.Selection selection = FluidIdentifierItem.Selection.byId(
                data.getString(FluidStorageTankBlockEntity.ITEM_FLUID));
        boolean damaged = data.getBoolean(FluidStorageTankBlockEntity.ITEM_DAMAGED);
        poses.pushPose();
        poses.translate(0.5D, 0.5D, 0.5D);
        if (context == ItemDisplayContext.GUI) {
            // Tank inventory camera. Transform order is load-bearing.
            poses.translate(0.0D, -2.0D, 0.0D);
            poses.scale(3.5F, 3.5F, 3.5F);
            poses.mulPose(Axis.YP.rotationDegrees(90.0F));
            poses.scale(0.75F, 0.75F, 0.75F);
        }
        FluidStorageTankRenderer.renderParts(selection, damaged, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }
}
