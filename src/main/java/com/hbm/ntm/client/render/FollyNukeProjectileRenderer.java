package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.client.model.EnvsuitMesh;
import com.hbm.ntm.entity.FollyNukeProjectileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public final class FollyNukeProjectileRenderer extends EntityRenderer<FollyNukeProjectileEntity> {
    private static final ResourceLocation MODEL = id("models/projectiles/projectiles.obj");
    private static final ResourceLocation TEXTURE = id("textures/models/projectiles/rocket_mirv.png");
    private static final ResourceLocation BLANK = id("textures/particle/particle_base.png");
    private EnvsuitMesh mesh;

    public FollyNukeProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(FollyNukeProjectileEntity projectile, float yaw, float partialTick,
                       PoseStack poses, MultiBufferSource buffers, int light) {
        Vec3 direction = projectile.direction();
        float horizontal = Mth.sqrt((float) (direction.x * direction.x + direction.z * direction.z));
        float authoredYaw = (float) (Mth.atan2(direction.x, direction.z) * Mth.RAD_TO_DEG);
        float authoredPitch = (float) (Mth.atan2(direction.y, horizontal) * Mth.RAD_TO_DEG);
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(authoredYaw - 90.0F));
        poses.mulPose(Axis.ZP.rotationDegrees(authoredPitch + 180.0F));
        poses.scale(0.5F, 0.5F, 0.5F);
        poses.mulPose(Axis.ZP.rotationDegrees(90.0F));
        mesh().render("MissileMIRV", poses.last(), buffers.getBuffer(RenderType.entityCutout(TEXTURE)),
                1.0F, light, OverlayTexture.NO_OVERLAY, -1);
        poses.popPose();
        super.render(projectile, yaw, partialTick, poses, buffers, light);
    }

    private EnvsuitMesh mesh() {
        if (mesh == null) {
            mesh = EnvsuitMesh.load(Minecraft.getInstance().getResourceManager(), MODEL,
                    Set.of("MissileMIRV"), "Folly Nuclear Round");
        }
        return mesh;
    }

    @Override public ResourceLocation getTextureLocation(FollyNukeProjectileEntity entity) { return BLANK; }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path);
    }
}
