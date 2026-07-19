package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.LevitationBombBlock;
import com.hbm.ntm.explosion.DetonationResult;
import com.hbm.ntm.explosion.ExplosionChaos;
import com.hbm.ntm.explosion.RemoteDetonatable;
import com.hbm.ntm.explosion.RemoteDetonation;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Focused tests for the Levitation Bomb ({@code hbm:float_bomb}). Because the source detonation rewrites
 * a ~21^3 terrain region and flings entities +50 in Y, the terrain/entity tests call the
 * {@link ExplosionChaos} helpers directly at explicit safe world coordinates (high above the platform,
 * in void air) inside their own isolated batches, and clean up every block/entity they touch.
 */
@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LevitationBombGameTests {
    private LevitationBombGameTests() {
    }

    @GameTest(template = "empty")
    public static void propertiesMatchSource(GameTestHelper helper) {
        LevitationBombBlock block = ModBlocks.FLOAT_BOMB.get();
        check(helper, block.getExplosionResistance() == 120.0F,
                "Source setResistance(200.0F) must map to the modern explosion resistance 120 (200 * 0.6)");
        check(helper, block instanceof RemoteDetonatable,
                "Source BombFloat implements IBomb, so float_bomb must be remotely detonatable");
        check(helper, BuiltInRegistries.BLOCK.getKey(block)
                        .equals(ResourceLocation.fromNamespaceAndPath("hbm", "float_bomb")),
                "The block must keep the source registry id hbm:float_bomb");
        check(helper, block.defaultBlockState().getRenderShape() == RenderShape.MODEL,
                "The Levitation Bomb is a full opaque model cube, unlike the invisible nuke blocks");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "float_floater_isolated")
    public static void floaterLiftsSphereIncludingUnbreakable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = helper.absolutePos(new BlockPos(2, 40, 2));
        int x = c.getX();
        int y = c.getY();
        int z = c.getZ();

        BlockPos inside = new BlockPos(x + 3, y, z);            // 3^2 = 9 < 112, inside the sphere
        BlockPos bedrockInside = new BlockPos(x, y, z + 3);     // unbreakable, still inside the sphere
        BlockPos outside = new BlockPos(x + 11, y, z);          // 11^2 = 121 >= 112, scanned but not moved
        BlockPos airCell = new BlockPos(x + 1, y, z + 1);       // inside, but an empty source cell

        level.setBlock(inside, Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
        level.setBlock(bedrockInside, Blocks.BEDROCK.defaultBlockState(), 2);
        level.setBlock(outside, Blocks.EMERALD_BLOCK.defaultBlockState(), 2);

        ExplosionChaos.floater(level, x, y, z, 15, 50);

        check(helper, level.getBlockState(inside).isAir(),
                "An inside-sphere block must be cleared from its origin");
        check(helper, level.getBlockState(inside.above(50)).is(Blocks.DIAMOND_BLOCK),
                "An inside-sphere block must reappear exactly 50 blocks up with its blockstate preserved");
        check(helper, level.getBlockState(bedrockInside).isAir(),
                "Bedrock inside the sphere is not spared (faithful quirk): its origin is cleared");
        check(helper, level.getBlockState(bedrockInside.above(50)).is(Blocks.BEDROCK),
                "Unbreakable bedrock is lifted 50 up like everything else in the sphere");
        check(helper, level.getBlockState(outside).is(Blocks.EMERALD_BLOCK),
                "A cell at squared distance >= 112 stays put");
        check(helper, level.getBlockState(outside.above(50)).isAir(),
                "The outside cell must not be duplicated at the destination band");
        check(helper, level.getBlockState(airCell.above(50)).isAir(),
                "Air source cells must not place a block at the destination (source guards save != air)");

        clear(level, inside, inside.above(50), bedrockInside, bedrockInside.above(50),
                outside, outside.above(50));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "float_move_isolated")
    public static void moveRenamesAndTeleportsWithSourceRadii(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = helper.absolutePos(new BlockPos(2, 40, 2));
        double x = c.getX();
        double y = c.getY();
        double z = c.getZ();
        int ix = c.getX();
        int iy = c.getY();
        int iz = c.getZ();

        // Pig well inside the teleport radius: renamed AND lifted +50.
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 2, 2));
        pig.moveTo(x + 2.0D, y, z + 2.0D, 0.0F, 0.0F);

        // Sheep inside the teleport radius: gets the jeb_ rainbow tag AND lifted.
        Sheep sheep = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(2, 2, 2));
        sheep.moveTo(x + 2.0D, y, z - 2.0D, 0.0F, 0.0F);

        // Cow in the 15..30 rename band but outside the <15 teleport band: renamed, NOT lifted.
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 2, 2));
        cow.moveTo(x + 15.0D, y, z + 3.0D, 0.0F, 0.0F);

        // Cow outside the +/-16 search box: never found, so never touched (even though < 30 away).
        Cow farCow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 2, 2));
        farCow.moveTo(x + 18.0D, y, z, 0.0F, 0.0F);

        // Item entity inside the teleport radius: lifted, but never renamed (not a Mob).
        ItemEntity item = new ItemEntity(level, x + 1.0D, y, z + 1.0D, new ItemStack(Items.DIAMOND));
        item.setDeltaMovement(0.0D, 0.0D, 0.0D);
        level.addFreshEntity(item);

        ExplosionChaos.move(level, ix, iy, iz, 15, 0, 50, 0);

        check(helper, isDinnerboneOrGrumm(nameOf(pig)),
                "A mob inside the rename radius must be tagged Dinnerbone or Grumm");
        check(helper, pig.getY() == y + 50.0D,
                "A mob inside the teleport radius must be flung up exactly 50 blocks");

        check(helper, "jeb_".equals(nameOf(sheep)),
                "A sheep must get the jeb_ rainbow tag instead of Dinnerbone/Grumm");
        check(helper, sheep.getY() == y + 50.0D, "The sheep must also be lifted 50 blocks");

        check(helper, isDinnerboneOrGrumm(nameOf(cow)),
                "A mob 15..30 blocks away must still be renamed");
        check(helper, cow.getY() == y,
                "A mob outside the <15 teleport radius must keep its Y (rename radius > teleport radius)");

        check(helper, farCow.getCustomName() == null && farCow.getY() == y,
                "A mob outside the +/-16 search box must be neither renamed nor lifted");

        check(helper, item.getCustomName() == null,
                "A non-mob entity must never be renamed");
        check(helper, item.getY() == y + 50.0D,
                "A non-mob entity inside the teleport radius must still be lifted 50 blocks");

        pig.discard();
        sheep.discard();
        cow.discard();
        farCow.discard();
        item.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "float_trigger_isolated")
    public static void redstonePowerDetonatesAndRemovesBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bomb = helper.absolutePos(new BlockPos(2, 40, 2));
        level.setBlock(bomb, ModBlocks.FLOAT_BOMB.get().defaultBlockState(), 3);
        check(helper, level.getBlockState(bomb).is(ModBlocks.FLOAT_BOMB.get()),
                "The Levitation Bomb must place as a normal block without self-triggering");

        BlockPos redstone = bomb.above();
        level.setBlock(redstone, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);

        check(helper, level.getBlockState(bomb).isAir(),
                "An indirectly powered Levitation Bomb must detonate and remove its own block");

        // The detonation's floater lifts the adjacent redstone block +50; clean the whole column it touched.
        clear(level, bomb, redstone, redstone.above(50));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "float_remote_isolated")
    public static void remoteDetonationReturnsDetonated(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bomb = helper.absolutePos(new BlockPos(2, 40, 2));
        level.setBlock(bomb, ModBlocks.FLOAT_BOMB.get().defaultBlockState(), 2);

        RemoteDetonation.Attempt attempt = RemoteDetonation.trigger(level, bomb);
        check(helper, attempt.compatible() && attempt.result() == DetonationResult.DETONATED,
                "float_bomb implements IBomb, so a remote detonator must fire it and receive DETONATED");
        check(helper, level.getBlockState(bomb).isAir(),
                "A successful remote detonation must remove the bomb block");

        clear(level, bomb);
        helper.succeed();
    }

    private static String nameOf(net.minecraft.world.entity.Entity entity) {
        return entity.getCustomName() == null ? null : entity.getCustomName().getString();
    }

    private static boolean isDinnerboneOrGrumm(String name) {
        return "Dinnerbone".equals(name) || "Grumm".equals(name);
    }

    private static void clear(ServerLevel level, BlockPos... positions) {
        for (BlockPos pos : positions) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
