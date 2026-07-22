package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.ChargeThrowerProjectileEntity;
import com.hbm.ntm.item.ChargeThrowerItem;
import com.hbm.ntm.item.DrillItem;
import com.hbm.ntm.item.SednaGunItem;
import com.hbm.ntm.item.SourceFluidContainerItem;
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
public final class ToolWeaponGameTests {
    private ToolWeaponGameTests() { }

    @GameTest(template = "empty")
    public static void chargeThrowerKeepsAllThreePayloads(GameTestHelper helper) {
        for (ChargeThrowerAmmoType type : ChargeThrowerAmmoType.values()) {
            ItemStack ammo = type.createStack(ModItems.AMMO_STANDARD.get(), 2);
            helper.assertTrue(StandardAmmoTypes.fromStack(ammo) == type,
                    type + " must preserve its source ammo_standard identity");
        }
        ChargeThrowerItem gun = ModItems.GUN_CHARGE_THROWER.get();
        helper.assertTrue(gun.gunDurability() == 3_000.0F && gun.gunCapacity() == 1
                        && gun.gunAutomatic() && gun.gunSecondaryAutomatic()
                        && gun.gunCrosshair() == SednaCrosshair.L_CIRCUMFLEX,
                "Charge Thrower must retain its source receiver values");
        helper.assertTrue(ChargeThrowerAmmoType.HOOK.projectileLifetime() == 6_000
                        && ChargeThrowerAmmoType.MORTAR.damage() == 2.5F
                        && ChargeThrowerAmmoType.CHARGED_MORTAR.damage() == 5.0F,
                "hook lifetime and both mortar profiles must retain their source values");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chargeThrowerSpawnsEachPayload(GameTestHelper helper) {
        int x = 2;
        for (ChargeThrowerAmmoType type : ChargeThrowerAmmoType.values()) {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            ItemStack gun = new ItemStack(ModItems.GUN_CHARGE_THROWER.get());
            ChargeThrowerItem.setTestState(gun, ChargeThrowerItem.GunState.IDLE,
                    0, 1, type, 0.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, gun);
            player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(x++, 4, 2))));
            player.setYRot(0.0F);
            player.setXRot(0.0F);
            SednaGunItem.handleInput(player, GunInput.PRIMARY);
        }
        List<ChargeThrowerProjectileEntity> shots = helper.getLevel().getEntitiesOfClass(
                ChargeThrowerProjectileEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(64.0D));
        helper.assertTrue(shots.size() == 3
                        && shots.stream().map(ChargeThrowerProjectileEntity::ammoType).distinct().count() == 3
                        && shots.stream().allMatch(shot -> Math.abs(shot.getDeltaMovement().length()
                                - ChargeThrowerAmmoType.SPEED) < 0.0001D),
                "all three Charge Thrower payloads must launch at source speed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void drillUsesOnlySourceEngineFuels(GameTestHelper helper) {
        DrillItem gun = ModItems.GUN_DRILL.get();
        helper.assertTrue(gun.gunDurability() == 3_000.0F && gun.gunCapacity() == 4_000
                        && gun.gunAutomatic() && gun.gunCrosshair() == SednaCrosshair.L_CIRCUMFLEX,
                "Powered Drill must retain its source receiver and engine capacity");

        for (SourceFluidContainerItem.ContainedFluid fluid : List.of(
                SourceFluidContainerItem.ContainedFluid.GASOLINE,
                SourceFluidContainerItem.ContainedFluid.GASOLINE_LEADED,
                SourceFluidContainerItem.ContainedFluid.COALGAS,
                SourceFluidContainerItem.ContainedFluid.COALGAS_LEADED)) {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            ItemStack drill = new ItemStack(gun);
            DrillItem.setTestState(drill, DrillItem.GunState.IDLE, 0, 0, false);
            player.setItemInHand(InteractionHand.MAIN_HAND, drill);
            player.getInventory().add(SourceFluidContainerItem.create(ModItems.CANISTER_FULL.get(), fluid, 1));
            SednaGunItem.handleInput(player, GunInput.RELOAD);
            helper.assertTrue(DrillItem.fuel(drill) == 1_000,
                    fluid + " must add one source canister to the drill engine");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void drillConsumesTenMillibucketsPerCycle(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack drill = new ItemStack(ModItems.GUN_DRILL.get());
        DrillItem.setTestState(drill, DrillItem.GunState.IDLE, 0, 100, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, drill);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 4, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(DrillItem.fuel(drill) == 90
                        && DrillItem.state(drill) == DrillItem.GunState.COOLDOWN,
                "one drill cycle must consume exactly 10 mB and enter its 20-tick cooldown");
        helper.succeed();
    }
}
