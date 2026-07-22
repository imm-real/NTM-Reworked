package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.model.EnvsuitMesh;
import com.hbm.ntm.entity.ChargeThrowerProjectileEntity;
import com.hbm.ntm.item.ChargeThrowerItem;
import com.hbm.ntm.weapon.ChargeThrowerAmmoType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Set;

public final class ChargeThrowerProjectileRenderer extends EntityRenderer<ChargeThrowerProjectileEntity> {
    private static final ResourceLocation MODEL = id("models/weapons/charge_thrower.obj");
    private static final ResourceLocation HOOK = id("textures/models/weapons/charge_thrower_hook.png");
    private static final ResourceLocation MORTAR = id("textures/models/weapons/charge_thrower_mortar.png");
    private static final ResourceLocation BLANK = id("textures/particle/particle_base.png");
    private static final Set<String> GROUPS = Set.of("Hook", "Mortar", "Oomph");
    private static final RenderType CABLE = RenderType.create(
            "hbm_charge_thrower_cable", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 256, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));
    private EnvsuitMesh mesh;

    public ChargeThrowerProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(ChargeThrowerProjectileEntity projectile, float yaw, float partialTick,
                       PoseStack poses, MultiBufferSource buffers, int light) {
        poses.pushPose();
        if (projectile.ammoType().kind() == ChargeThrowerAmmoType.Kind.HOOK) {
            poses.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, projectile.yRotO, projectile.getYRot()) - 90.0F));
            poses.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, projectile.xRotO, projectile.getXRot()) + 180.0F));
        }
        poses.scale(0.125F, 0.125F, 0.125F);
        poses.mulPose(Axis.YN.rotationDegrees(90.0F));
        poses.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poses.translate(0.0D, 0.0D, -6.0D);
        if (projectile.ammoType().kind() == ChargeThrowerAmmoType.Kind.HOOK) {
            renderPart("Hook", HOOK, poses, buffers, light);
        } else {
            renderPart("Mortar", MORTAR, poses, buffers, light);
            if (projectile.ammoType().kind() == ChargeThrowerAmmoType.Kind.CHARGED_MORTAR) {
                renderPart("Oomph", MORTAR, poses, buffers, light);
            }
        }
        poses.popPose();

        if (projectile.ammoType().kind() == ChargeThrowerAmmoType.Kind.HOOK
                && ownsVisibleCable(projectile)) renderCable(projectile, partialTick, poses, buffers);
        super.render(projectile, yaw, partialTick, poses, buffers, light);
    }

    private static boolean ownsVisibleCable(ChargeThrowerProjectileEntity hook) {
        Entity owner = hook.getOwner();
        if (owner == null) return false;
        ItemStack held = owner instanceof net.minecraft.world.entity.LivingEntity living
                ? living.getMainHandItem() : ItemStack.EMPTY;
        return held.getItem() instanceof ChargeThrowerItem && ChargeThrowerItem.lastHook(held) == hook.getId();
    }

    private static void renderCable(ChargeThrowerProjectileEntity hook, float partialTick,
                                    PoseStack poses, MultiBufferSource buffers) {
        Entity owner = hook.getOwner();
        if (owner == null) return;
        double hx = Mth.lerp(partialTick, hook.xo, hook.getX());
        double hy = Mth.lerp(partialTick, hook.yo, hook.getY());
        double hz = Mth.lerp(partialTick, hook.zo, hook.getZ());
        double ox = Mth.lerp(partialTick, owner.xo, owner.getX());
        double oy = Mth.lerp(partialTick, owner.yo, owner.getY()) + owner.getEyeHeight() - 0.25D;
        double oz = Mth.lerp(partialTick, owner.zo, owner.getZ());
        Vec3 delta = new Vec3(ox - hx, oy - hy, oz - hz);
        double hang = Math.min(delta.length() / 15.0D, 0.5D);
        VertexConsumer consumer = buffers.getBuffer(CABLE);
        PoseStack.Pose pose = poses.last();
        int segments = 10;
        float girth = 0.03125F;
        for (int index = 0; index < segments; index++) {
            double a = index / (double) segments;
            double b = (index + 1) / (double) segments;
            Vector3f from = point(delta, a, hang);
            Vector3f to = point(delta, b, hang);
            Vector3f direction = new Vector3f(to).sub(from).normalize();
            Vector3f reference = Math.abs(direction.y()) > 0.95F
                    ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(0.0F, 1.0F, 0.0F);
            Vector3f right = direction.cross(reference, new Vector3f()).normalize().mul(girth);
            Vector3f up = right.cross(direction, new Vector3f()).normalize().mul(girth);
            quad(consumer, pose, new Vector3f(from).add(right), new Vector3f(from).sub(right),
                    new Vector3f(to).sub(right), new Vector3f(to).add(right));
            quad(consumer, pose, new Vector3f(from).add(up), new Vector3f(from).sub(up),
                    new Vector3f(to).sub(up), new Vector3f(to).add(up));
        }
    }

    private static Vector3f point(Vec3 delta, double progress, double hang) {
        return new Vector3f((float) (delta.x * progress),
                (float) (delta.y * progress - Math.sin(progress * Math.PI) * hang),
                (float) (delta.z * progress));
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             Vector3f a, Vector3f b, Vector3f c, Vector3f d) {
        vertex(consumer, pose, a); vertex(consumer, pose, b); vertex(consumer, pose, c); vertex(consumer, pose, d);
    }
    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vector3f point) {
        consumer.addVertex(pose, point.x(), point.y(), point.z()).setColor(0xFF606060);
    }
    private void renderPart(String group, ResourceLocation texture, PoseStack poses,
                            MultiBufferSource buffers, int light) {
        mesh().render(group, poses.last(), buffers.getBuffer(RenderType.entityCutout(texture)),
                1.0F, light, OverlayTexture.NO_OVERLAY, -1);
    }
    private EnvsuitMesh mesh() {
        if (mesh == null) mesh = EnvsuitMesh.load(Minecraft.getInstance().getResourceManager(), MODEL, GROUPS, "Charge Thrower Projectile");
        return mesh;
    }
    @Override public ResourceLocation getTextureLocation(ChargeThrowerProjectileEntity entity) { return BLANK; }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path); }
}
