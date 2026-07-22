package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.FireExtinguisherProjectileEntity;
import com.hbm.ntm.item.FireExtinguisherItem;
import com.hbm.ntm.item.SednaGunItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FireExtinguisherGameTests {
    private FireExtinguisherGameTests() { }

    @GameTest(template = "empty")
    public static void tanksAndReceiverKeepSourceValues(GameTestHelper helper) {
        for (FireExtinguisherAmmoType type : FireExtinguisherAmmoType.values()) {
            ItemStack tank = type.createStack(ModItems.AMMO_FIREEXT.get(), 2);
            helper.assertTrue(FireExtinguisherAmmoType.fromStack(tank) == type,
                    type + " must preserve its source extinguisher tank identity");
        }
        FireExtinguisherItem gun = ModItems.GUN_FIREEXT.get();
        helper.assertTrue(gun.gunDurability() == 5_000.0F && gun.gunCapacity() == 300
                        && gun.gunAutomatic() && gun.gunCrosshair() == SednaCrosshair.L_CIRCLE,
                "Fire Extinguisher must retain its 5000 durability, 300 tank, automatic fire, and circle sight");
        helper.assertTrue(FireExtinguisherItem.wearSpread(2_500.0F) == 0.0F
                        && FireExtinguisherItem.wearSpread(5_000.0F) == 0.125F
                        && FireExtinguisherItem.jamChance(3_200.0F) == 0.0F
                        && FireExtinguisherItem.jamChance(5_000.0F) == 1.0F,
                "source wear must grow to 0.125 spread and the standard jam curve");
        helper.assertTrue(FireExtinguisherItem.rounds(new ItemStack(gun)) == 0,
                "MagazineFullReload must leave a fresh extinguisher empty");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oneTankFillsAndPartialLoadsStayTypeLocked(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_FIREEXT.get());
        FireExtinguisherItem.setTestState(gun, FireExtinguisherItem.GunState.IDLE,
                0, 100, FireExtinguisherAmmoType.WATER, 0.0F, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.getInventory().add(FireExtinguisherAmmoType.FOAM.createStack(ModItems.AMMO_FIREEXT.get(), 2));
        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(FireExtinguisherItem.state(gun) == FireExtinguisherItem.GunState.IDLE
                        && FireExtinguisherItem.rounds(gun) == 100,
                "a partial water load must reject a different tank identity");

        player.getInventory().add(FireExtinguisherAmmoType.WATER.createStack(ModItems.AMMO_FIREEXT.get(), 2));
        SednaGunItem.handleInput(player, GunInput.RELOAD);
        tick(player, FireExtinguisherItem.RELOAD_TICKS);
        helper.assertTrue(FireExtinguisherItem.rounds(gun) == 300
                        && FireExtinguisherItem.loadedType(gun) == FireExtinguisherAmmoType.WATER
                        && count(player, FireExtinguisherAmmoType.WATER) == 1,
                "one source tank must fill the 300-unit magazine and consume one item");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allThreeLoadsSpawnTheirSourceProjectiles(GameTestHelper helper) {
        int x = 2;
        for (FireExtinguisherAmmoType type : FireExtinguisherAmmoType.values()) {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            ItemStack gun = new ItemStack(ModItems.GUN_FIREEXT.get());
            FireExtinguisherItem.setTestState(gun, FireExtinguisherItem.GunState.IDLE,
                    0, 1, type, 0.0F, false);
            player.setItemInHand(InteractionHand.MAIN_HAND, gun);
            player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(x++, 3, 2))));
            player.setYRot(0.0F);
            player.setXRot(0.0F);
            SednaGunItem.handleInput(player, GunInput.PRIMARY);
        }
        List<FireExtinguisherProjectileEntity> shots = helper.getLevel().getEntitiesOfClass(
                FireExtinguisherProjectileEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(64.0D));
        helper.assertTrue(shots.size() == 3
                        && shots.stream().map(FireExtinguisherProjectileEntity::ammoType).distinct().count() == 3
                        && shots.stream().allMatch(shot -> Math.abs(shot.getDeltaMovement().length()
                                - FireExtinguisherProjectileEntity.SPEED) < 0.0001D),
                "water, foam, and sand must each launch at the source 0.75 velocity");
        helper.succeed();
    }

    private static void tick(Player player, int ticks) {
        FireExtinguisherItem gun = (FireExtinguisherItem) player.getMainHandItem().getItem();
        for (int i = 0; i < ticks; i++) {
            gun.inventoryTick(player.getMainHandItem(), player.level(), player,
                    player.getInventory().selected, true);
        }
    }

    private static int count(Player player, FireExtinguisherAmmoType type) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.AMMO_FIREEXT.get())
                    && FireExtinguisherAmmoType.fromStack(stack) == type) count += stack.getCount();
        }
        return count;
    }
}
