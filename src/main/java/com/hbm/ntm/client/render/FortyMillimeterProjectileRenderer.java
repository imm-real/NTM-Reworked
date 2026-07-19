package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.FortyMillimeterProjectileEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Compact glowing projectile that hopes nobody asks about aerodynamics. */
public final class FortyMillimeterProjectileRenderer extends EntityRenderer<FortyMillimeterProjectileEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/particle/particle_base.png");
    private static final RenderType TYPE = RenderType.create(
            "hbm_40mm_projectile", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
            128, false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    public FortyMillimeterProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(FortyMillimeterProjectileEntity projectile, float yaw, float partialTick,
                       PoseStack poses, MultiBufferSource buffers, int light) {
        Vec3 motion = projectile.getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-8D) return;
        Vector3f direction = motion.normalize().toVector3f();
        Vector3f reference = Math.abs(direction.y()) > 0.95F
                ? new Vector3f(1,0,0) : new Vector3f(0,1,0);
        Vector3f right = direction.cross(reference, new Vector3f()).normalize();
        Vector3f up = right.cross(direction, new Vector3f()).normalize();
        float radius = projectile.ammoType().isFlare() ? 0.09F : 0.07F;
        float length = projectile.ammoType().isFlare() ? 0.3F : 0.45F;
        Vector3f front = new Vector3f(direction).mul(length * 0.5F);
        Vector3f back = new Vector3f(direction).mul(-length * 0.5F);
        Vector3f r = new Vector3f(right).mul(radius);
        Vector3f u = new Vector3f(up).mul(radius);
        int color = projectile.ammoType().isFlare() ? 0xFFFF3010 : 0xFF8A8A78;
        VertexConsumer consumer = buffers.getBuffer(TYPE);
        PoseStack.Pose pose = poses.last();
        Vector3f fpp = new Vector3f(front).add(r).add(u);
        Vector3f fpn = new Vector3f(front).add(r).sub(u);
        Vector3f fnp = new Vector3f(front).sub(r).add(u);
        Vector3f fnn = new Vector3f(front).sub(r).sub(u);
        Vector3f bpp = new Vector3f(back).add(r).add(u);
        Vector3f bpn = new Vector3f(back).add(r).sub(u);
        Vector3f bnp = new Vector3f(back).sub(r).add(u);
        Vector3f bnn = new Vector3f(back).sub(r).sub(u);
        quad(consumer, pose, fpp, fnp, bnp, bpp, color);
        quad(consumer, pose, fnn, fpn, bpn, bnn, color);
        quad(consumer, pose, fnp, fnn, bnn, bnp, color);
        quad(consumer, pose, fpn, fpp, bpp, bpn, color);
        quad(consumer, pose, fpp, fpn, fnn, fnp, color);
        quad(consumer, pose, bpp, bnp, bnn, bpn, color);
        super.render(projectile, yaw, partialTick, poses, buffers, light);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             Vector3f a, Vector3f b, Vector3f c, Vector3f d, int color) {
        vertex(consumer, pose, a, color); vertex(consumer, pose, b, color);
        vertex(consumer, pose, c, color); vertex(consumer, pose, d, color);
    }
    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vector3f point, int color) {
        consumer.addVertex(pose, point.x(), point.y(), point.z()).setColor(color);
    }
    @Override public ResourceLocation getTextureLocation(FortyMillimeterProjectileEntity entity) { return TEXTURE; }
}
