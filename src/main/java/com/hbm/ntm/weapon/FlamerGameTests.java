package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.FlameProjectileEntity;
import com.hbm.ntm.item.FlamerGunItem;
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
public final class FlamerGameTests {
    private FlamerGameTests() { }

    @GameTest(template = "empty")
    public static void fuelIdentitiesAndProjectileProfilesMatchSource(GameTestHelper helper) {
        for (FlamerFuelType fuel : FlamerFuelType.values()) {
            ItemStack stack = fuel.createStack(ModItems.AMMO_STANDARD.get(), 2);
            helper.assertTrue(FlamerFuelType.fromStack(stack) == fuel
                            && StandardAmmoTypes.fromStack(stack) == fuel
                            && StandardAmmoTypes.fromLegacyMetadata(fuel.legacyMetadata()) == fuel,
                    fuel + " must preserve its source EnumAmmo identity");
        }
        helper.assertTrue(FlamerFuelType.DIESEL.legacyMetadata() == 63
                        && FlamerFuelType.BALEFIRE.legacyMetadata() == 66
                        && FlamerFuelType.RELOAD_AMOUNT == 500,
                "flamer fuel metadata and one-tank reload count must remain 63-66 and 500");
        helper.assertTrue(FlameProjectileEntity.life(FlamerFuelType.DIESEL,
                                FlamerGunItem.Variant.FLAMETHROWER) == 100
                        && FlameProjectileEntity.life(FlamerFuelType.GAS,
                                FlamerGunItem.Variant.FLAMETHROWER) == 10
                        && FlameProjectileEntity.life(FlamerFuelType.NAPALM,
                                FlamerGunItem.Variant.FLAMETHROWER) == 200
                        && FlameProjectileEntity.life(FlamerFuelType.BALEFIRE,
                                FlamerGunItem.Variant.TOPAZ) == 60
                        && FlameProjectileEntity.gravity(FlamerFuelType.NAPALM,
                                FlamerGunItem.Variant.TOPAZ) == 0.0F
                        && FlameProjectileEntity.gravity(FlamerFuelType.DIESEL,
                                FlamerGunItem.Variant.DAYBREAKER) == 0.035F,
                "base, Topaz, and Daybreaker flight profiles must match XFactoryFlamer");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void receiversKeepSourceValuesAndSpawnEmpty(GameTestHelper helper) {
        FlamerGunItem regular = ModItems.GUN_FLAMER.get();
        FlamerGunItem topaz = ModItems.GUN_FLAMER_TOPAZ.get();
        FlamerGunItem daybreaker = ModItems.GUN_FLAMER_DAYBREAKER.get();
        helper.assertTrue(regular.gunDurability() == 20_000.0F && regular.gunCapacity() == 300
                        && regular.baseDamage() == 1.0F && regular.variant().fireDelay() == 1,
                "Flamethrower must retain its 20000/300/1 receiver");
        helper.assertTrue(topaz.gunCapacity() == 500 && topaz.baseDamage() == 1.5F
                        && topaz.variant().projectiles() == 2 && topaz.variant().fireDelay() == 1,
                "Mister Topaz must retain its 500 tank and paired flame stream");
        helper.assertTrue(daybreaker.gunCapacity() == 50 && daybreaker.baseDamage() == 25.0F
                        && daybreaker.variant().projectiles() == 1 && daybreaker.variant().fireDelay() == 10,
                "Daybreaker must retain its 50 tank and ten-tick 25-damage receiver");
        helper.assertTrue(FlamerGunItem.rounds(new ItemStack(regular)) == 0
                        && FlamerGunItem.rounds(new ItemStack(topaz)) == 0
                        && FlamerGunItem.rounds(new ItemStack(daybreaker)) == 0,
                "MagazineFullReload flamers must spawn empty");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void threeReceiversSpawnTheirSourceStreams(GameTestHelper helper) {
        Player regular = armed(helper, ModItems.GUN_FLAMER.get(),
                FlamerGunItem.Variant.FLAMETHROWER, FlamerFuelType.DIESEL, 2, 2);
        Player topaz = armed(helper, ModItems.GUN_FLAMER_TOPAZ.get(),
                FlamerGunItem.Variant.TOPAZ, FlamerFuelType.BALEFIRE, 2, 5);
        Player daybreaker = armed(helper, ModItems.GUN_FLAMER_DAYBREAKER.get(),
                FlamerGunItem.Variant.DAYBREAKER, FlamerFuelType.NAPALM, 1, 8);
        SednaGunItem.handleInput(regular, GunInput.PRIMARY);
        SednaGunItem.handleInput(topaz, GunInput.PRIMARY);
        SednaGunItem.handleInput(daybreaker, GunInput.PRIMARY);

        List<FlameProjectileEntity> shots = projectiles(helper);
        helper.assertTrue(shots.stream().filter(shot -> shot.getOwner() == regular).count() == 1
                        && shots.stream().filter(shot -> shot.getOwner() == topaz).count() == 2
                        && shots.stream().filter(shot -> shot.getOwner() == daybreaker).count() == 1,
                "regular, Topaz, and Daybreaker must emit one, two, and one projectile per cycle");
        helper.assertTrue(shots.stream().anyMatch(shot -> shot.getOwner() == regular
                                && shot.fuel() == FlamerFuelType.DIESEL && shot.damage() == 1.0F)
                        && shots.stream().filter(shot -> shot.getOwner() == topaz)
                                .allMatch(shot -> shot.fuel() == FlamerFuelType.BALEFIRE
                                        && shot.damage() == 1.5F)
                        && shots.stream().anyMatch(shot -> shot.getOwner() == daybreaker
                                && shot.fuel() == FlamerFuelType.NAPALM && shot.damage() == 25.0F),
                "each stream must retain the loaded fuel and source receiver damage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oneFuelTankFillsAndPartialLoadsStayTypeLocked(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_FLAMER.get());
        FlamerGunItem.setTestState(gun, FlamerGunItem.GunState.IDLE,
                0, 100, FlamerFuelType.DIESEL, 0.0F, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        ItemStack gas = FlamerFuelType.GAS.createStack(ModItems.AMMO_STANDARD.get(), 2);
        player.getInventory().add(gas);

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(FlamerGunItem.state(gun) == FlamerGunItem.GunState.IDLE
                        && FlamerGunItem.rounds(gun) == 100
                        && countFuel(player, FlamerFuelType.GAS) == 2,
                "a partially loaded diesel tank must reject other fuel identities");

        ItemStack diesel = FlamerFuelType.DIESEL.createStack(ModItems.AMMO_STANDARD.get(), 2);
        player.getInventory().add(diesel);
        SednaGunItem.handleInput(player, GunInput.RELOAD);
        tick(player, FlamerGunItem.Variant.FLAMETHROWER.reloadTicks());
        helper.assertTrue(FlamerGunItem.rounds(gun) == 300
                        && FlamerGunItem.loadedFuel(gun) == FlamerFuelType.DIESEL
                        && countFuel(player, FlamerFuelType.DIESEL) == 1,
                "one 500-unit source tank must cap a partial 300-unit magazine and consume one item");
        helper.succeed();
    }

    private static Player armed(GameTestHelper helper, FlamerGunItem gunItem,
                                FlamerGunItem.Variant variant, FlamerFuelType fuel,
                                int rounds, int x) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(gunItem);
        FlamerGunItem.setTestState(gun, FlamerGunItem.GunState.IDLE, 0, rounds, fuel, 0.0F, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(x, 3, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tick(Player player, int ticks) {
        FlamerGunItem gun = (FlamerGunItem) player.getMainHandItem().getItem();
        for (int i = 0; i < ticks; i++) {
            gun.inventoryTick(player.getMainHandItem(), player.level(), player,
                    player.getInventory().selected, true);
        }
    }

    private static List<FlameProjectileEntity> projectiles(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntitiesOfClass(FlameProjectileEntity.class,
                new AABB(origin).inflate(64.0D));
    }

    private static int countFuel(Player player, FlamerFuelType fuel) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.AMMO_STANDARD.get()) && StandardAmmoTypes.fromStack(stack) == fuel) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
