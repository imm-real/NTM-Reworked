package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.DfcComponentBlock;
import com.hbm.ntm.blockentity.DfcBlockEntity;
import com.hbm.ntm.blockentity.DfcEmitterBlockEntity;
import com.hbm.ntm.blockentity.DfcInjectorBlockEntity;
import com.hbm.ntm.blockentity.DfcStabilizerBlockEntity;
import com.hbm.ntm.dfc.DfcKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

public final class DfcComponentRenderer<T extends DfcBlockEntity> implements BlockEntityRenderer<T> {
    public static final ModelResourceLocation EMITTER = model("dfc_emitter_obj");
    public static final ModelResourceLocation INJECTOR = model("dfc_injector_obj");
    public static final ModelResourceLocation RECEIVER = model("dfc_receiver_obj");
    public static final ModelResourceLocation STABILIZER = model("dfc_stabilizer_obj");

    public DfcComponentRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(T component, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        poses.translate(0.5D, 0.0D, 0.5D);
        orientLikeSource(poses, component.getBlockState().getValue(DfcComponentBlock.FACING));
        ThermalModelRenderer.render(model(component.kind()), poses, buffers, light, overlay);
        poses.translate(0.0D, 0.5D, 0.0D);
        int beam = beam(component);
        if (beam > 0) renderBeam(component, poses, buffers, beam);
        poses.popPose();
    }

    private static void orientLikeSource(PoseStack poses, Direction direction) {
        poses.mulPose(Axis.YP.rotationDegrees(90.0F));
        switch (direction) {
            case DOWN -> {
                poses.translate(0.0D, 0.5D, -0.5D);
                poses.mulPose(Axis.XP.rotationDegrees(90.0F));
            }
            case UP -> {
                poses.translate(0.0D, 0.5D, 0.5D);
                poses.mulPose(Axis.XN.rotationDegrees(90.0F));
            }
            case NORTH -> poses.mulPose(Axis.YP.rotationDegrees(90.0F));
            case WEST -> poses.mulPose(Axis.YP.rotationDegrees(180.0F));
            case SOUTH -> poses.mulPose(Axis.YP.rotationDegrees(270.0F));
            case EAST -> { }
        }
    }

    private static int beam(DfcBlockEntity component) {
        if (component instanceof DfcEmitterBlockEntity emitter) return emitter.beam();
        if (component instanceof DfcInjectorBlockEntity injector) return injector.beam();
        if (component instanceof DfcStabilizerBlockEntity stabilizer) return stabilizer.beam();
        return 0;
    }

    private static void renderBeam(DfcBlockEntity component, PoseStack poses,
                                   MultiBufferSource buffers, int beam) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        PoseStack.Pose pose = poses.last();
        double phase = component.getLevel() == null ? 0.0D
                : (component.getLevel().getGameTime() % 360L) * Math.PI / 18.0D;
        if (component.kind() == DfcKind.EMITTER) {
            prism(consumer, pose, 0.0F, 0.0F, beam, 0.065F, 1.0F, 0.55F, 0.0F, 0.95F);
            prism(consumer, pose, (float) Math.sin(phase) * 0.09F,
                    (float) Math.cos(phase) * 0.09F, beam, 0.025F, 1.0F, 0.82F, 0.0F, 0.9F);
        } else if (component instanceof DfcInjectorBlockEntity injector) {
            if (injector.tank(0).amount() > 0) {
                int color = com.hbm.ntm.item.FluidIdentifierItem.Selection.fromFluid(injector.tank(0).fluid()).color();
                prism(consumer, pose, -0.035F, 0.0F, beam, 0.018F, red(color), green(color), blue(color), 0.9F);
            }
            if (injector.tank(1).amount() > 0) {
                int color = com.hbm.ntm.item.FluidIdentifierItem.Selection.fromFluid(injector.tank(1).fluid()).color();
                prism(consumer, pose, 0.035F, 0.0F, beam, 0.018F, red(color), green(color), blue(color), 0.9F);
            }
        } else if (component.kind() == DfcKind.STABILIZER) {
            for (int i = 0; i < 3; i++) {
                double angle = phase * (0.35D + i * 0.2D) + i * Math.PI * 2.0D / 3.0D;
                prism(consumer, pose, (float) Math.sin(angle) * 0.08F,
                        (float) Math.cos(angle) * 0.08F, beam, 0.018F,
                        1.0F, 0.65F + i * 0.08F, 0.0F, 0.9F);
            }
        }
    }

    private static void prism(VertexConsumer consumer, PoseStack.Pose pose, float x, float y,
                              float length, float radius, float red, float green, float blue, float alpha) {
        quad(consumer, pose, x-radius,y-radius,0, x+radius,y-radius,0,
                x+radius,y-radius,length, x-radius,y-radius,length, red,green,blue,alpha);
        quad(consumer, pose, x+radius,y-radius,0, x+radius,y+radius,0,
                x+radius,y+radius,length, x+radius,y-radius,length, red,green,blue,alpha);
        quad(consumer, pose, x+radius,y+radius,0, x-radius,y+radius,0,
                x-radius,y+radius,length, x+radius,y+radius,length, red,green,blue,alpha);
        quad(consumer, pose, x-radius,y+radius,0, x-radius,y-radius,0,
                x-radius,y-radius,length, x-radius,y+radius,length, red,green,blue,alpha);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             float ax,float ay,float az,float bx,float by,float bz,
                             float cx,float cy,float cz,float dx,float dy,float dz,
                             float red,float green,float blue,float alpha) {
        vertex(consumer,pose,ax,ay,az,red,green,blue,alpha); vertex(consumer,pose,bx,by,bz,red,green,blue,alpha);
        vertex(consumer,pose,cx,cy,cz,red,green,blue,alpha); vertex(consumer,pose,dx,dy,dz,red,green,blue,alpha);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x,float y,float z,
                               float red,float green,float blue,float alpha) {
        consumer.addVertex(pose, x, y, z).setColor(red, green, blue, alpha);
    }

    private static float red(int color) { return (color >> 16 & 255) / 255.0F; }
    private static float green(int color) { return (color >> 8 & 255) / 255.0F; }
    private static float blue(int color) { return (color & 255) / 255.0F; }

    private static ModelResourceLocation model(DfcKind kind) {
        return switch (kind) {
            case EMITTER -> EMITTER;
            case INJECTOR -> INJECTOR;
            case RECEIVER -> RECEIVER;
            case STABILIZER -> STABILIZER;
            default -> throw new IllegalArgumentException("Core is not a component model");
        };
    }

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/" + name));
    }

    @Override public AABB getRenderBoundingBox(T component) {
        BlockPos pos = component.getBlockPos();
        return new AABB(pos.getX() - 51.0D, pos.getY() - 51.0D, pos.getZ() - 51.0D,
                pos.getX() + 52.0D, pos.getY() + 52.0D, pos.getZ() + 52.0D);
    }
    @Override public int getViewDistance() { return 256; }
}
