package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.AssemblyMachineBlock;
import com.hbm.ntm.blockentity.AssemblyMachineBlockEntity;
import com.hbm.ntm.recipe.AssemblyClientRecipes;
import com.hbm.ntm.recipe.AssemblyRecipe;
import com.hbm.ntm.client.sound.AssemblyMachineSoundInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AssemblyMachineRenderer implements BlockEntityRenderer<AssemblyMachineBlockEntity> {
    public static final Map<String, ModelResourceLocation> MODELS = createModels();
    private final ItemRenderer itemRenderer;
    private final Map<AssemblyMachineBlockEntity, AssemblyMachineSoundInstance> sounds = new java.util.WeakHashMap<>();

    public AssemblyMachineRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    private static Map<String, ModelResourceLocation> createModels() {
        Map<String, ModelResourceLocation> models = new LinkedHashMap<>();
        for (String name : new String[]{"base", "frame", "ring", "armlower1", "armupper1", "head1", "spike1",
                "armlower2", "armupper2", "head2", "spike2"}) {
            models.put(name, ModelResourceLocation.standalone(
                    ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/assembly_machine_" + name)));
        }
        return Map.copyOf(models);
    }

    @Override
    public void render(AssemblyMachineBlockEntity machine, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        if (machine.active()) {
            AssemblyMachineSoundInstance sound = sounds.get(machine);
            if (sound == null || sound.isStopped()) {
                sound = new AssemblyMachineSoundInstance(machine);
                sounds.put(machine, sound);
                Minecraft.getInstance().getSoundManager().play(sound);
            }
        }
        pose.pushPose();
        pose.translate(0.5D, 0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(90F + facingRotation(machine.getBlockState().getValue(AssemblyMachineBlock.FACING))));
        renderModel("base", pose, buffers, light, overlay);
        if (machine.hasFrame()) renderModel("frame", pose, buffers, light, overlay);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees((float) machine.interpolatedRing(partialTick)));
        renderModel("ring", pose, buffers, light, overlay);
        renderArm(machine.arm(0), false, "1", partialTick, pose, buffers, light, overlay);
        renderArm(machine.arm(1), true, "2", partialTick, pose, buffers, light, overlay);
        pose.popPose();

        renderRecipeIcon(machine, pose, buffers, light, overlay);
        pose.popPose();
    }

    private void renderArm(AssemblyMachineBlockEntity.AssemblerArm arm, boolean mirrored, String suffix,
                           float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        double sign = mirrored ? -1D : 1D;
        double zLower = sign * 0.9375D;
        double zHead = sign * 0.4375D;
        pose.pushPose();
        pivotX(pose, 1.625D, zLower, sign * arm.value(0, partialTick));
        renderModel("armlower" + suffix, pose, buffers, light, overlay);
        pivotX(pose, 2.375D, zLower, sign * arm.value(1, partialTick));
        renderModel("armupper" + suffix, pose, buffers, light, overlay);
        pivotX(pose, 2.375D, zHead, sign * arm.value(2, partialTick));
        renderModel("head" + suffix, pose, buffers, light, overlay);
        pose.translate(0D, arm.value(3, partialTick), 0D);
        renderModel("spike" + suffix, pose, buffers, light, overlay);
        pose.popPose();
    }

    private static void pivotX(PoseStack pose, double y, double z, double degrees) {
        pose.translate(0D, y, z);
        pose.mulPose(Axis.XP.rotationDegrees((float) degrees));
        pose.translate(0D, -y, -z);
    }

    private void renderRecipeIcon(AssemblyMachineBlockEntity machine, PoseStack pose, MultiBufferSource buffers,
                                  int light, int overlay) {
        if (machine.recipeId() == null || Minecraft.getInstance().player == null) return;
        double distance = Minecraft.getInstance().player.distanceToSqr(
                machine.getBlockPos().getX() + 0.5D, machine.getBlockPos().getY() + 1D,
                machine.getBlockPos().getZ() + 0.5D);
        if (distance >= 35D * 35D) return;
        AssemblyRecipe recipe = AssemblyClientRecipes.get(machine.recipeId());
        if (recipe == null) return;
        ItemStack icon = recipe.icon();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(90F));
        pose.translate(0D, 1.0625D, 0D);
        BakedModel itemModel = itemRenderer.getModel(icon, machine.getLevel(), null, 0);
        if (icon.getItem() instanceof BlockItem) {
            if (itemModel.isGui3d()) pose.translate(0D, -0.0625D, 0D);
            else { pose.translate(0D, -0.125D, 0D); pose.scale(0.5F, 0.5F, 0.5F); }
        } else {
            pose.mulPose(Axis.XP.rotationDegrees(-90F));
            pose.translate(0D, -0.25D, 0D);
        }
        pose.scale(1.25F, 1.25F, 1.25F);
        itemRenderer.renderStatic(icon, ItemDisplayContext.FIXED, light, overlay, pose, buffers,
                machine.getLevel(), 0);
        pose.popPose();
    }

    private void renderModel(String name, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(MODELS.get(name));
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = buffers.getBuffer(Sheets.solidBlockSheet());
        renderer.renderModel(pose.last(), consumer, Blocks.IRON_BLOCK.defaultBlockState(), model,
                1F, 1F, 1F, light, overlay);
    }

    private static float facingRotation(Direction direction) {
        return switch (direction) {
            case NORTH -> 0F;
            case EAST -> 90F;
            case SOUTH -> 180F;
            case WEST -> 270F;
            default -> 0F;
        };
    }

    @Override public AABB getRenderBoundingBox(AssemblyMachineBlockEntity machine) {
        BlockPos pos = machine.getBlockPos();
        return new AABB(pos.getX() - 1D, pos.getY(), pos.getZ() - 1D,
                pos.getX() + 2D, pos.getY() + 3D, pos.getZ() + 2D);
    }
    @Override public int getViewDistance() { return 256; }
}
