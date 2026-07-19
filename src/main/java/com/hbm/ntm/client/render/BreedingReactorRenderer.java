package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.BreedingReactorBlock;
import com.hbm.ntm.blockentity.BreedingReactorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

public final class BreedingReactorRenderer implements BlockEntityRenderer<BreedingReactorBlockEntity> {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/breeding_reactor"));

    public BreedingReactorRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(BreedingReactorBlockEntity reactor, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        poses.translate(.5D, 0D, .5D);
        poses.mulPose(Axis.YP.rotationDegrees(sourceRotation(reactor.getBlockState().getValue(BreedingReactorBlock.FACING))));
        if (reactor.progress() > 0.0F) renderSparks(poses, buffers);
        ThermalModelRenderer.render(MODEL, poses, buffers, light, overlay);
        poses.popPose();
    }

    private static float sourceRotation(net.minecraft.core.Direction facing) {
        return switch (facing) {
            case NORTH -> 90F; case WEST -> 180F; case SOUTH -> 270F; case EAST -> 0F; default -> 90F;
        };
    }

    private static void renderSparks(PoseStack poses, MultiBufferSource buffers) {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        int frame = (int) ((System.currentTimeMillis() % 10_000L) / 100L);
        for (int spark = 0; spark < 3; spark++) {
            poses.pushPose();
            // Radians passed as degrees. The tiny wobble is historically significant.
            poses.mulPose(Axis.YP.rotationDegrees((float) (Math.PI * spark)));
            renderSpark(lines, poses.last(), frame + spark);
            poses.popPose();
        }
    }

    private static void renderSpark(VertexConsumer consumer, PoseStack.Pose pose, int seed) {
        Random random = new Random(seed);
        Vec3 base = new Vec3(random.nextDouble() - .5D, random.nextDouble() - .5D,
                random.nextDouble() - .5D).normalize();
        Vec3 previous = new Vec3(0D, 1.5625D, 0D);
        int segments = 3 + random.nextInt(4);
        for (int i = 0; i < segments; i++) {
            Vec3 direction = new Vec3(base.x * .15D * random.nextFloat(),
                    base.y * .15D * random.nextFloat(), base.z * .15D * random.nextFloat());
            Vec3 next = previous.add(direction);
            line(consumer, pose, previous, next, 0F, 1F, 0F);
            line(consumer, pose, previous, next, 1F, 1F, 1F);
            previous = next;
        }
    }

    private static void line(VertexConsumer consumer, PoseStack.Pose pose, Vec3 from, Vec3 to,
                             float red, float green, float blue) {
        Vec3 normal = to.subtract(from).normalize();
        consumer.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(red, green, blue, 1F).setNormal((float) normal.x, (float) normal.y, (float) normal.z);
        consumer.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                .setColor(red, green, blue, 1F).setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    @Override public AABB getRenderBoundingBox(BreedingReactorBlockEntity reactor) { return reactor.renderBounds(); }
    @Override public int getViewDistance() { return 256; }
}
