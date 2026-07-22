package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.WeaponModifierMenu;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WeaponModifierGameTests {
    private WeaponModifierGameTests() {}

    @GameTest(template = "empty")
    public static void silencerOnlyFitsThePortedReceiver(GameTestHelper helper) {
        ItemStack silencer = new ItemStack(ModItems.WEAPON_MOD_SILENCER.get());
        helper.assertTrue(WeaponModManager.isApplicable(
                        new ItemStack(ModItems.GUN_AM180.get()), silencer, 0),
                "Silencer must fit the American-180 receiver");
        helper.assertTrue(!WeaponModManager.isApplicable(
                        new ItemStack(ModItems.GUN_STAR_F.get()), silencer, 0),
                "Silencer must stay disabled for receivers without ported effects");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void installedIdsRoundTripInSourceOrder(GameTestHelper helper) {
        ItemStack gun = new ItemStack(ModItems.GUN_AM180.get());
        WeaponModManager.install(gun, 0, List.of(new ItemStack(ModItems.WEAPON_MOD_SILENCER.get())));
        helper.assertTrue(WeaponModManager.hasMod(gun, 0, WeaponModManager.SILENCER),
                "Installed silencer must use source ID 201");
        helper.assertTrue(WeaponModManager.installedMods(gun, 0).getFirst()
                        .is(ModItems.WEAPON_MOD_SILENCER.get()),
                "Installed silencer must rebuild its table stack");
        WeaponModManager.uninstall(gun, 0);
        helper.assertTrue(!WeaponModManager.hasMod(gun, 0, WeaponModManager.SILENCER),
                "Uninstall must clear the receiver list");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void takingGunCommitsWhileClosingCancels(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        WeaponModifierMenu commit = new WeaponModifierMenu(0, player.getInventory());
        commit.getSlot(0).set(new ItemStack(ModItems.GUN_AM180.get()));
        commit.getSlot(1).set(new ItemStack(ModItems.WEAPON_MOD_SILENCER.get()));
        ItemStack taken = commit.getSlot(0).remove(1);
        commit.getSlot(0).onTake(player, taken);
        helper.assertTrue(WeaponModManager.hasMod(taken, 0, WeaponModManager.SILENCER),
                "Taking the gun must commit displayed mods");
        helper.assertTrue(commit.getSlot(1).getItem().isEmpty(),
                "Committed mods must be absorbed by the gun");

        WeaponModifierMenu cancel = new WeaponModifierMenu(1, player.getInventory());
        ItemStack canceledGun = new ItemStack(ModItems.GUN_AM180.get());
        cancel.getSlot(0).set(canceledGun);
        cancel.getSlot(1).set(new ItemStack(ModItems.WEAPON_MOD_SILENCER.get()));
        cancel.removed(player);
        helper.assertTrue(!WeaponModManager.hasMod(canceledGun, 0, WeaponModManager.SILENCER),
                "Closing the table must cancel preview mods");
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                AABB.ofSize(player.position(), 4.0D, 4.0D, 4.0D));
        helper.assertTrue(drops.stream().anyMatch(item -> item.getItem().is(ModItems.GUN_AM180.get()))
                        && drops.stream().anyMatch(item -> item.getItem().is(ModItems.WEAPON_MOD_SILENCER.get())),
                "Closing the table must drop the gun and loose mod back to the player");
        helper.succeed();
    }
}
