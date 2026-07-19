package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.ConventionalExplosiveBlock;
import com.hbm.ntm.entity.PrimedExplosiveEntity;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConventionalBombGameTests {
    private ConventionalBombGameTests() {
    }

    @GameTest(template = "empty")
    public static void conventionalBombsPreserveOriginalBlastStrengths(GameTestHelper helper) {
        check(helper, ModBlocks.DYNAMITE.get().blastPower() == 8.0F, "Dynamite must use an 8-strength flaming blast");
        check(helper, ModBlocks.TNT_NTM.get().blastPower() == 10.0F, "Actual TNT must use a 10-strength flaming blast");
        check(helper, ModBlocks.SEMTEX.get().blastPower() == 12.0F, "Semtex must use a 12-strength flaming blast");
        check(helper, ModBlocks.C4.get().blastPower() == 15.0F, "C-4 must use a 15-strength flaming blast");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conventional_prime_isolated")
    public static void redstonePrimesFullFuseWithOriginalBlockIdentity(GameTestHelper helper) {
        BlockPos support = new BlockPos(2, 1, 2);
        BlockPos explosive = support.above();
        helper.setBlock(support, Blocks.REDSTONE_BLOCK);
        helper.setBlock(explosive, ModBlocks.TNT_NTM.get());

        check(helper, helper.getBlockState(explosive).isAir(), "A powered explosive must remove its placed block");
        PrimedExplosiveEntity primed = onlyPrimed(helper, explosive);
        check(helper, primed.getFuse() == ConventionalExplosiveBlock.NORMAL_FUSE,
                "Direct ignition must begin with the original 80-tick fuse");
        check(helper, primed.getBlockState().is(ModBlocks.TNT_NTM.get()),
                "The primed entity must retain the exact explosive block for rendering and blast selection");
        check(helper, Math.abs(primed.getDeltaMovement().y - 0.2D) < 0.000001D,
                "The primed explosive must launch upward at the original 0.2 velocity");
        primed.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void adjacentFirePrimesExplosive(GameTestHelper helper) {
        BlockPos explosive = new BlockPos(2, 2, 2);
        helper.setBlock(explosive.east().below(), Blocks.NETHERRACK);
        helper.setBlock(explosive.east(), Blocks.FIRE);
        helper.setBlock(explosive, ModBlocks.DYNAMITE.get());

        check(helper, helper.getBlockState(explosive).isAir(), "Directly adjacent fire must prime the explosive on placement");
        PrimedExplosiveEntity primed = onlyPrimed(helper, explosive);
        check(helper, primed.getBlockState().is(ModBlocks.DYNAMITE.get()),
                "Adjacent-fire ignition must preserve Dynamite identity");
        primed.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void explosionChainUsesOriginalShortFuseRange(GameTestHelper helper) {
        BlockPos explosive = new BlockPos(2, 2, 2);
        helper.setBlock(explosive, ModBlocks.C4.get());
        BlockPos absolute = helper.absolutePos(explosive);
        Explosion source = new Explosion(
                helper.getLevel(),
                null,
                absolute.getX() + 0.5D,
                absolute.getY() + 0.5D,
                absolute.getZ() + 0.5D,
                4.0F,
                false,
                Explosion.BlockInteraction.DESTROY
        );
        ModBlocks.C4.get().wasExploded(helper.getLevel(), helper.absolutePos(explosive), source);

        PrimedExplosiveEntity primed = onlyPrimed(helper, explosive);
        check(helper, primed.getFuse() >= 10 && primed.getFuse() <= 29,
                "Explosion chaining must randomize the fuse to the original inclusive 10-29 tick range");
        primed.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void defuserSafelyRecoversConventionalExplosive(GameTestHelper helper) {
        BlockPos explosive = new BlockPos(2, 2, 2);
        helper.setBlock(explosive, ModBlocks.C4.get());
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack defuser = new ItemStack(ModItems.DEFUSER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, defuser);
        BlockPos absolute = helper.absolutePos(explosive);
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), net.minecraft.core.Direction.UP, absolute, false));
        ModItems.DEFUSER.get().useOn(context);

        check(helper, helper.getBlockState(explosive).isAir(),
                "Defuser must remove a conventional explosive without priming it");
        check(helper, defuser.getDamageValue() == 1, "Successful defusing must consume one durability");
        check(helper, helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new AABB(absolute).inflate(2.0D)).stream()
                .anyMatch(item -> item.getItem().is(ModItems.C4_ITEM.get())),
                "Defusing must recover the exact conventional explosive item");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fuseZeroSurvivesUntilFollowingTick(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(2, 3, 2));
        PrimedExplosiveEntity primed = new PrimedExplosiveEntity(
                helper.getLevel(),
                position.getX() + 0.5D,
                position.getY() + 0.5D,
                position.getZ() + 0.5D,
                null,
                ModBlocks.DYNAMITE.get().defaultBlockState(),
                1
        );
        helper.getLevel().addFreshEntity(primed);
        primed.tick();

        check(helper, primed.isAlive() && primed.getFuse() == 0,
                "The original post-decrement fuse keeps a fuse-zero explosive alive for one final tick");
        primed.discard();
        helper.succeed();
    }

    private static PrimedExplosiveEntity onlyPrimed(GameTestHelper helper, BlockPos relativeCenter) {
        AABB bounds = new AABB(helper.absolutePos(relativeCenter)).inflate(2.0D);
        List<PrimedExplosiveEntity> entities = helper.getLevel().getEntitiesOfClass(PrimedExplosiveEntity.class, bounds);
        check(helper, entities.size() == 1, "Expected exactly one primed HBM explosive entity");
        return entities.getFirst();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
