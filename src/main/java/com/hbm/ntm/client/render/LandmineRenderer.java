package com.hbm.ntm.client.render;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.LandmineBlock;
import com.hbm.ntm.blockentity.LandmineBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Landmine fashion: stone underground, snow in snow, sand in heat, grass otherwise. */
public final class LandmineRenderer implements BlockEntityRenderer<LandmineBlockEntity> {
    public static final ModelResourceLocation AP_GRASS = model("mine_ap_grass");
    public static final ModelResourceLocation AP_SNOW = model("mine_ap_snow");
    public static final ModelResourceLocation AP_STONE = model("mine_ap_stone");
    public static final ModelResourceLocation AP_DESERT = model("mine_ap_desert");
    public static final ModelResourceLocation SHRAP = model("mine_shrap_obj");
    public static final ModelResourceLocation HE = model("mine_he_obj");
    public static final ModelResourceLocation FAT = model("mine_fat_obj");
    public static final ModelResourceLocation NAVAL = model("mine_naval_obj");

    public static final List<ModelResourceLocation> MODELS = List.of(
            AP_GRASS, AP_SNOW, AP_STONE, AP_DESERT, SHRAP, HE, FAT, NAVAL);

    public LandmineRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LandmineBlockEntity mine, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = mine.getLevel();
        BlockState state = mine.getBlockState();
        if (level == null || !(state.getBlock() instanceof LandmineBlock landmine)) {
            return;
        }

        poses.pushPose();
        poses.translate(0.5D, 0.0D, 0.5D);
        poses.mulPose(Axis.YP.rotationDegrees(180.0F));

        switch (landmine.type()) {
            case AP -> {
                poses.scale(0.375F, 0.375F, 0.375F);
                poses.translate(0.0D, -0.0625D * 3.5D, 0.0D);
                ThermalModelRenderer.render(apModel(level, mine.getBlockPos()), poses, buffers,
                        packedLight, packedOverlay);
            }
            case SHRAP -> {
                poses.scale(0.375F, 0.375F, 0.375F);
                poses.translate(0.0D, -0.0625D * 3.5D, 0.0D);
                ThermalModelRenderer.render(SHRAP, poses, buffers, packedLight, packedOverlay);
            }
            case HE -> {
                poses.mulPose(Axis.YP.rotationDegrees(-90.0F));
                ThermalModelRenderer.render(HE, poses, buffers, packedLight, packedOverlay);
            }
            case FAT -> {
                poses.scale(0.25F, 0.25F, 0.25F);
                ThermalModelRenderer.render(FAT, poses, buffers, packedLight, packedOverlay);
            }
            case NAVAL -> {
                poses.translate(0.0D, 0.5D, 0.0D);
                ThermalModelRenderer.render(NAVAL, poses, buffers, packedLight, packedOverlay);
            }
        }

        poses.popPose();
    }

    private static ModelResourceLocation apModel(Level level, BlockPos pos) {
        if (level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) > pos.getY() + 2) {
            return AP_STONE;
        }
        Holder<Biome> biome = level.getBiome(pos);
        if (biome.value().coldEnoughToSnow(pos)) {
            return AP_SNOW;
        }
        if (biome.value().getBaseTemperature() >= 1.5F && !biome.value().hasPrecipitation()) {
            return AP_DESERT;
        }
        return AP_GRASS;
    }

    @Override
    public AABB getRenderBoundingBox(LandmineBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return new AABB(pos).inflate(2.0D);
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    private static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "block/" + path));
    }
}
