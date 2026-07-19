package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.ArcFurnaceBlock;
import com.hbm.ntm.blockentity.ArcFurnaceBlockEntity;
import com.hbm.ntm.client.sound.ArcFurnaceSoundInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.WeakHashMap;

/** Arc furnace OBJ with independently troublesome lid, electrodes and cables. */
public final class ArcFurnaceRenderer implements BlockEntityRenderer<ArcFurnaceBlockEntity> {
    public static final ModelResourceLocation BODY = model("body");
    public static final ModelResourceLocation COLD = model("cold");
    public static final ModelResourceLocation LID = model("lid");
    public static final ModelResourceLocation[] RINGS = {model("ring1"), model("ring2"), model("ring3")};
    public static final ModelResourceLocation[] FRESH = {
            model("electrode1"), model("electrode2"), model("electrode3")};
    public static final ModelResourceLocation[] HOT = {
            model("electrode1_hot"), model("electrode2_hot"), model("electrode3_hot")};
    public static final ModelResourceLocation[] SHORT = {
            model("electrode1_short"), model("electrode2_short"), model("electrode3_short")};
    public static final ModelResourceLocation[] CABLES = {model("cable1"), model("cable2"), model("cable3")};
    private final Map<ArcFurnaceBlockEntity, ArcFurnaceSoundInstance> sounds = new WeakHashMap<>();

    public ArcFurnaceRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(ArcFurnaceBlockEntity furnace, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (furnace.progressing()) {
            ArcFurnaceSoundInstance sound = sounds.get(furnace);
            if (sound == null || sound.isStopped()) {
                sound = new ArcFurnaceSoundInstance(furnace);
                sounds.put(furnace, sound);
                Minecraft.getInstance().getSoundManager().play(sound);
            }
        }

        poses.pushPose();
        poses.translate(0.5D, 0D, 0.5D);
        poses.mulPose(Axis.YP.rotationDegrees(rotation(
                furnace.getBlockState().getValue(ArcFurnaceBlock.FACING))));
        ThermalModelRenderer.render(BODY, poses, buffers, packedLight, packedOverlay);
        if (furnace.hasMaterial()) ThermalModelRenderer.render(COLD, poses, buffers, packedLight, packedOverlay);

        float lift = furnace.lid(partialTick);
        double shake = furnace.progressing() && furnace.getLevel() != null
                ? Math.sin(furnace.getLevel().getGameTime() + partialTick) * 0.005D : 0D;
        poses.translate(0D, lift * 2D, shake);
        ThermalModelRenderer.render(LID, poses, buffers, packedLight, packedOverlay);
        for (int index = 0; index < 3; index++) {
            byte state = furnace.electrodeState(index);
            if (state == ArcFurnaceBlockEntity.ELECTRODE_NONE) continue;
            ThermalModelRenderer.render(RINGS[index], poses, buffers, packedLight, packedOverlay);
            if (state == ArcFurnaceBlockEntity.ELECTRODE_FRESH) {
                ThermalModelRenderer.render(FRESH[index], poses, buffers, packedLight, packedOverlay);
            } else if (state == ArcFurnaceBlockEntity.ELECTRODE_USED) {
                ThermalModelRenderer.render(HOT[index], poses, buffers, LightTexture.FULL_BRIGHT, packedOverlay);
            } else {
                ThermalModelRenderer.render(SHORT[index], poses, buffers, LightTexture.FULL_BRIGHT, packedOverlay);
            }
        }
        renderCables(furnace, partialTick, poses, buffers, packedLight, packedOverlay);
        poses.popPose();
    }

    private static void renderCables(ArcFurnaceBlockEntity furnace, float partialTick, PoseStack poses,
                                     MultiBufferSource buffers, int packedLight, int packedOverlay) {
        double time = furnace.getLevel() == null ? 0D : furnace.getLevel().getGameTime() + partialTick;
        float angle = furnace.progressing() ? (float) (Math.sin(time / 2D) * 30D) : 0F;
        double[] pivotZ = {0.5D, 0D, -0.5D};
        for (int index = 0; index < 3; index++) {
            if (furnace.electrodeState(index) == ArcFurnaceBlockEntity.ELECTRODE_NONE) continue;
            poses.pushPose();
            poses.translate(0D, 5.5D, pivotZ[index]);
            poses.mulPose(Axis.XP.rotationDegrees(angle));
            poses.translate(0D, -5.5D, -pivotZ[index]);
            ThermalModelRenderer.render(CABLES[index], poses, buffers, packedLight, packedOverlay);
            poses.popPose();
        }
    }

    private static ModelResourceLocation model(String part) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "block/arc_furnace_" + part));
    }

    private static float rotation(Direction facing) {
        return switch (facing) {
            case EAST -> 0F;
            case NORTH -> 90F;
            case WEST -> 180F;
            case SOUTH -> 270F;
            default -> 0F;
        };
    }

    @Override public AABB getRenderBoundingBox(ArcFurnaceBlockEntity furnace) {
        BlockPos position = furnace.getBlockPos();
        return new AABB(position.getX() - 4, position.getY(), position.getZ() - 4,
                position.getX() + 5, position.getY() + 7, position.getZ() + 5);
    }

    @Override public int getViewDistance() { return 256; }
}
