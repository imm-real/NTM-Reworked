package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.explosion.DetonationResult;
import com.hbm.ntm.explosion.RemoteDetonation;
import com.hbm.ntm.item.DeadMansDetonatorItem;
import com.hbm.ntm.item.DetonatorEvents;
import com.hbm.ntm.item.DetonatorItem;
import com.hbm.ntm.item.MultiDetonatorItem;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DetonatorGameTests {
    private DetonatorGameTests() {
    }

    @GameTest(template = "empty")
    public static void linksRetainCoordinatesOrderAndDuplicates(GameTestHelper helper) {
        ItemStack single = new ItemStack(ModItems.DETONATOR.get());
        BlockPos linked = new BlockPos(-1234, 71, 9876);
        DetonatorItem.writeLink(single, linked);
        check(helper, linked.equals(DetonatorItem.readLink(single)),
                "Single detonator must retain its exact source x/y/z coordinates");

        ItemStack multi = new ItemStack(ModItems.DETONATOR_MULTI.get());
        MultiDetonatorItem.addLocation(multi, linked);
        MultiDetonatorItem.addLocation(multi, linked);
        MultiDetonatorItem.addLocation(multi, BlockPos.ZERO);
        check(helper, MultiDetonatorItem.locations(multi).equals(List.of(linked, linked, BlockPos.ZERO)),
                "Multi Detonator must preserve insertion order and duplicate locations");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void remoteCompatibilityMatchesSourceBombContract(GameTestHelper helper) {
        BlockPos conventional = new BlockPos(2, 2, 2);
        helper.setBlock(conventional, ModBlocks.C4.get());
        RemoteDetonation.Attempt c4 = RemoteDetonation.trigger(helper.getLevel(), helper.absolutePos(conventional));
        check(helper, !c4.compatible() && c4.result() == DetonationResult.ERROR_NO_BOMB,
                "Ordinary C-4 must not implement the source IBomb remote contract");
        check(helper, helper.getBlockState(conventional).is(ModBlocks.C4.get()),
                "Rejected ordinary C-4 must remain placed");

        BlockPos gadget = new BlockPos(4, 2, 2);
        helper.setBlock(gadget, ModBlocks.NUKE_GADGET.get());
        RemoteDetonation.Attempt incomplete = RemoteDetonation.trigger(helper.getLevel(), helper.absolutePos(gadget));
        check(helper, incomplete.compatible() && incomplete.result() == DetonationResult.ERROR_MISSING_COMPONENT,
                "Incomplete nuclear devices must answer with the source component-missing result");
        check(helper, helper.getBlockState(gadget).is(ModBlocks.NUKE_GADGET.get()),
                "An incomplete remotely triggered nuke must remain placed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "detonator_charge_isolated")
    public static void remoteChargeDetonatesImmediately(GameTestHelper helper) {
        BlockPos support = new BlockPos(2, 1, 2);
        BlockPos charge = support.above();
        helper.setBlock(support, Blocks.STONE);
        helper.setBlock(charge, ModBlocks.CHARGE_DYNAMITE.get());

        RemoteDetonation.Attempt attempt = RemoteDetonation.trigger(helper.getLevel(), helper.absolutePos(charge));
        check(helper, attempt.compatible() && attempt.result() == DetonationResult.DETONATED,
                "Time Bomb must report successful immediate remote detonation");
        check(helper, helper.getBlockState(charge).isAir(),
                "Remote detonation must consume the charge block");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void playerDeathConsumesOnlyLinkedDeadMansSwitches(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack linked = new ItemStack(ModItems.DETONATOR_DEADMAN.get());
        DetonatorItem.writeLink(linked, helper.absolutePos(new BlockPos(4, 2, 2)));
        ItemStack unlinked = new ItemStack(ModItems.DETONATOR_DEADMAN.get());
        player.getInventory().setItem(0, linked);
        player.getInventory().setItem(1, unlinked);

        int consumed = DetonatorEvents.triggerDeathSwitches(player, helper.getLevel());
        check(helper, consumed == 1 && player.getInventory().getItem(0).isEmpty(),
                "A linked dead man's switch must be consumed on player death even when its target is absent");
        check(helper, player.getInventory().getItem(1).is(ModItems.DETONATOR_DEADMAN.get()),
                "The source death scan must leave an unlinked switch in the inventory");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void droppedDeadMansSwitchAlwaysConsumesItself(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.DETONATOR_DEADMAN.get());
        ItemEntity dropped = new ItemEntity(helper.getLevel(), 2.5D, 2.5D, 2.5D, stack);
        ((DeadMansDetonatorItem) stack.getItem()).onEntityItemUpdate(stack, dropped);
        check(helper, dropped.isRemoved(),
                "Dropped dead man's switches must disappear even without a linked target");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
