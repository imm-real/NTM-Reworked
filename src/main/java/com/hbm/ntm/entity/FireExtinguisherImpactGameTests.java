package com.hbm.ntm.entity;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FireExtinguisherImpactGameTests {
    private FireExtinguisherImpactGameTests() { }

    @GameTest(template = "empty")
    public static void waterClearsTheSourceImpactVolume(GameTestHelper helper) {
        BlockPos impact = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos fire = impact.offset(1, 0, 0);
        BlockPos foam = impact.offset(0, 1, -1);
        helper.getLevel().setBlock(fire, Blocks.FIRE.defaultBlockState(), 3);
        helper.getLevel().setBlock(foam, ModBlocks.BLOCK_FOAM.get().defaultBlockState(), 3);

        helper.assertTrue(FireExtinguisherProjectileEntity.waterImpact(helper.getLevel(), impact)
                        && helper.getLevel().getBlockState(fire).isAir()
                        && helper.getLevel().getBlockState(foam).isAir(),
                "water must clear fire and extinguisher foam throughout the source 3x3x3 volume");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void foamAndSandBuildSevenLayersThenBecomeFullBlocks(GameTestHelper helper) {
        BlockPos foam = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos sand = helper.absolutePos(new BlockPos(4, 2, 2));
        helper.getLevel().setBlock(foam.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(sand.below(), Blocks.STONE.defaultBlockState(), 3);
        BlockState foamLayer = ModBlocks.FOAM_LAYER.get().defaultBlockState();
        BlockState sandLayer = ModBlocks.SAND_BORON_LAYER.get().defaultBlockState();

        for (int shot = 0; shot < 7; shot++) {
            FireExtinguisherProjectileEntity.layer(helper.getLevel(), foam, foamLayer,
                    ModBlocks.BLOCK_FOAM.get().defaultBlockState());
            FireExtinguisherProjectileEntity.layer(helper.getLevel(), sand, sandLayer,
                    ModBlocks.SAND_MIX.get().defaultBlockState());
        }
        helper.assertTrue(helper.getLevel().getBlockState(foam).getValue(SnowLayerBlock.LAYERS) == 7
                        && helper.getLevel().getBlockState(sand).getValue(SnowLayerBlock.LAYERS) == 7,
                "the first seven source impacts must leave seven partial layers");

        FireExtinguisherProjectileEntity.layer(helper.getLevel(), foam, foamLayer,
                ModBlocks.BLOCK_FOAM.get().defaultBlockState());
        FireExtinguisherProjectileEntity.layer(helper.getLevel(), sand, sandLayer,
                ModBlocks.SAND_MIX.get().defaultBlockState());
        helper.assertTrue(helper.getLevel().getBlockState(foam).is(ModBlocks.BLOCK_FOAM.get())
                        && helper.getLevel().getBlockState(sand).is(ModBlocks.SAND_MIX.get()),
                "the eighth source impact must finish solid foam and falling boron sand");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyLoadExtinguishesBurningEntities(GameTestHelper helper) {
        Zombie zombie = new Zombie(EntityType.ZOMBIE, helper.getLevel());
        zombie.setRemainingFireTicks(200);
        FireExtinguisherProjectileEntity.extinguish(zombie);
        helper.assertTrue(!zombie.isOnFire(), "all three source loads use the same entity-extinguish callback");
        helper.succeed();
    }
}
