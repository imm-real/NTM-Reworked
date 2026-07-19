package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.BalefireBlock;
import com.hbm.ntm.block.NukeBalefireBlock;
import com.hbm.ntm.blockentity.NukeBalefireBlockEntity;
import com.hbm.ntm.explosion.DetonationResult;
import com.hbm.ntm.nuclear.BalefireEntity;
import com.hbm.ntm.nuclear.BalefireExplosion;
import com.hbm.ntm.nuclear.MushroomCloudEntity;
import com.hbm.ntm.radiation.RadiationSystem;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NukeFstbmbGameTests {
    private NukeFstbmbGameTests() { }

    @GameTest(template = "empty")
    public static void loadingRequiresEggAndBattery(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        NukeBalefireBlockEntity bomb = bomb(helper, pos);
        check(helper, !bomb.isLoaded(), "Empty Balefire Bomb must not be loaded");

        bomb.setItem(NukeBalefireBlockEntity.SLOT_EGG, new ItemStack(ModItems.EGG_BALEFIRE.get()));
        check(helper, bomb.hasEgg() && !bomb.isLoaded(),
                "Egg alone must not load the bomb");

        bomb.setItem(NukeBalefireBlockEntity.SLOT_BATTERY, new ItemStack(ModItems.BATTERY_SPARK.get()));
        check(helper, bomb.getBattery() == 1 && bomb.isLoaded(),
                "Egg plus Spark Battery must load the bomb (battery type 1)");

        bomb.setItem(NukeBalefireBlockEntity.SLOT_BATTERY, new ItemStack(ModItems.BATTERY_TRIXITE.get()));
        check(helper, bomb.getBattery() == 2 && bomb.isLoaded(),
                "Trixite battery must report type 2 and also load the bomb");

        bomb.setItem(NukeBalefireBlockEntity.SLOT_EGG, ItemStack.EMPTY);
        check(helper, !bomb.isLoaded(), "Removing the egg must unload the bomb");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missingComponentDetonationIsRejected(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        DetonationResult result = ModBlocks.NUKE_FSTBMB.get()
                .detonateRemotely(helper.getLevel(), helper.absolutePos(pos));
        check(helper, result == DetonationResult.ERROR_MISSING_COMPONENT,
                "Unloaded Balefire Bomb must report ERROR_MISSING_COMPONENT");
        check(helper, !helper.getBlockState(pos).isAir(),
                "Rejected detonation must leave the bomb block in place");
        check(helper, !ModBlocks.NUKE_FSTBMB.get().detonate(helper.getLevel(), helper.absolutePos(pos)),
                "Unloaded bomb must refuse the redstone detonate path");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void loadedDetonationSpawnsBalefireAndBaleCloud(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        NukeBalefireBlockEntity bomb = bomb(helper, pos);
        load(bomb);

        check(helper, ModBlocks.NUKE_FSTBMB.get().detonate(helper.getLevel(), helper.absolutePos(pos)),
                "Loaded Balefire Bomb must detonate");
        check(helper, helper.getBlockState(pos).isAir(), "Detonation must remove the bomb block");
        check(helper, bomb.getItem(0).isEmpty() && bomb.getItem(1).isEmpty(),
                "Detonation must clear the loaded components");

        AABB box = new AABB(helper.absolutePos(pos)).inflate(3.0D);
        List<BalefireEntity> balefires = helper.getLevel().getEntitiesOfClass(BalefireEntity.class, box);
        List<MushroomCloudEntity> clouds = helper.getLevel().getEntitiesOfClass(MushroomCloudEntity.class, box);
        check(helper, balefires.size() == 1 && balefires.getFirst().destructionRange() == 250,
                "Detonation must spawn one Balefire spiral with destructionRange 250");
        check(helper, clouds.size() == 1 && clouds.getFirst().cloudType() == 1,
                "Detonation must spawn the green-forward (type 1) bale Torex cloud");
        // Discard before the heavy full-radius spiral or damage sweep can run.
        balefires.forEach(BalefireEntity::discard);
        clouds.forEach(MushroomCloudEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void timerButtonSetsSecondsAndStartGatesOnLoad(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        NukeBalefireBlockEntity bomb = bomb(helper, pos);

        bomb.handleButtonPacket(30, 1);
        check(helper, bomb.timer() == 600, "Timer meta 1 must set timer to value * 20 ticks");
        check(helper, bomb.getMinutes().equals("00") && bomb.getSeconds().equals("30"),
                "600 ticks must format as 00:30");

        bomb.handleButtonPacket(0, 0);
        check(helper, !bomb.started(), "Start must be refused while the bomb is unloaded");

        load(bomb);
        bomb.handleButtonPacket(0, 0);
        check(helper, bomb.started(), "Start must arm the loaded bomb");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tickCountdownAndExplodesAtZero(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        NukeBalefireBlockEntity bomb = bomb(helper, pos);
        load(bomb);
        bomb.timer = 1;
        bomb.started = true;

        BlockState state = bomb.getBlockState();
        // One manual tick decrements 1 -> 0 and triggers explode() synchronously.
        NukeBalefireBlockEntity.tick(helper.getLevel(), bomb.getBlockPos(), state, bomb);

        check(helper, helper.getBlockState(pos).isAir(), "Timer reaching zero must remove the bomb block");
        List<BalefireEntity> balefires = helper.getLevel().getEntitiesOfClass(BalefireEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(3.0D));
        check(helper, balefires.size() == 1, "Timer expiry must spawn the Balefire spiral");
        balefires.forEach(BalefireEntity::discard);
        helper.getLevel().getEntitiesOfClass(MushroomCloudEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(3.0D)).forEach(MushroomCloudEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void reducedRadiusSpiralClearsAndConvertsTerrain(GameTestHelper helper) {
        // Small test rig: radius-2 spiral over an isolated 5x5 stone platform.
        // Actual clearing stays within the center 3x3 (dist > 0 only for sqrt(x^2+z^2) < 2).
        int cx = 4;
        int cz = 4;
        int top = 15;
        int bottom = 1;
        for (int dx = cx - 2; dx <= cx + 2; dx++) {
            for (int dz = cz - 2; dz <= cz + 2; dz++) {
                for (int y = bottom; y <= top; y++) {
                    helper.setBlock(new BlockPos(dx, y, dz), Blocks.STONE);
                }
            }
        }

        BlockPos center = helper.absolutePos(new BlockPos(cx, top, cz));
        BalefireExplosion exp = new BalefireExplosion(center.getX(), center.getY(), center.getZ(),
                helper.getLevel(), 2);
        boolean done = false;
        int guard = 0;
        while (!done && guard++ < 256) done = exp.update();
        check(helper, done, "The test balefire spiral must complete within nlimit");

        int air = 0;
        int sellafield = 0;
        for (int y = bottom; y <= top; y++) {
            BlockState state = helper.getBlockState(new BlockPos(cx, y, cz));
            if (state.isAir()) air++;
            if (state.is(ModBlocks.SELLAFIELD_SLAKED.get())) sellafield++;
        }
        check(helper, air > 0, "The center column must be cleared to air near the surface");
        check(helper, sellafield > 0, "Stone near the column floor must convert to Slaked Sellafite");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void balefireBlockBurnsAndNeedsSupport(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.tickCount = 200;
        BalefireBlock balefire = ModBlocks.BALEFIRE.get();

        BlockPos firePos = helper.absolutePos(new BlockPos(2, 2, 2));
        balefire.defaultBlockState().entityInside(helper.getLevel(), firePos, player);
        check(helper, player.getRemainingFireTicks() > 0, "Standing in balefire must set the entity on fire");
        var data = RadiationSystem.data(player);
        check(helper, Math.abs(data.radEnv() - 0.5F) < 0.0001F,
                "Balefire must deliver the amp-9 radiation dose (0.5 RAD/tick) as contamination");

        // Supported balefire survives; floating balefire (no support, no flammable neighbor) self-clears.
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
        check(helper, balefire.defaultBlockState().canSurvive(helper.getLevel(),
                        helper.absolutePos(new BlockPos(3, 3, 3))),
                "Balefire on a solid top must survive");
        check(helper, !balefire.defaultBlockState().canSurvive(helper.getLevel(),
                        helper.absolutePos(new BlockPos(6, 4, 6))),
                "Floating balefire without fuel or support must not survive");
        helper.succeed();
    }

    private static BlockPos placeBomb(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.NUKE_FSTBMB.get().defaultBlockState()
                .setValue(NukeBalefireBlock.FACING, Direction.SOUTH));
        return pos;
    }

    private static NukeBalefireBlockEntity bomb(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof NukeBalefireBlockEntity bomb) return bomb;
        helper.fail("Expected Balefire Bomb block entity");
        throw new IllegalStateException();
    }

    private static void load(NukeBalefireBlockEntity bomb) {
        bomb.setItem(NukeBalefireBlockEntity.SLOT_EGG, new ItemStack(ModItems.EGG_BALEFIRE.get()));
        bomb.setItem(NukeBalefireBlockEntity.SLOT_BATTERY, new ItemStack(ModItems.BATTERY_SPARK.get()));
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
