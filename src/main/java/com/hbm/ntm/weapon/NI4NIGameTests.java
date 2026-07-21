package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.NI4NIBeamEntity;
import com.hbm.ntm.entity.NI4NICoinEntity;
import com.hbm.ntm.item.NI4NIItem;
import com.hbm.ntm.item.SednaGunItem;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
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
public final class NI4NIGameTests {
    private NI4NIGameTests() { }

    @GameTest(template = "empty")
    public static void receiverKeepsTheInfiniteArcContract(GameTestHelper helper) {
        NI4NIItem gun = ModItems.GUN_N_I_4_N_I.get();
        helper.assertTrue(NI4NIItem.DRAW_TICKS == 5 && NI4NIItem.INSPECT_TICKS == 39
                        && NI4NIItem.FIRE_DELAY == 10 && NI4NIItem.BASE_DAMAGE == 35.0F
                        && NI4NIItem.MAX_COINS == 4 && NI4NIItem.COIN_RECHARGE_TICKS == 80
                        && gun.gunCrosshair() == SednaCrosshair.CIRCLE
                        && !gun.gunShowAmmoCounter() && !gun.gunShowDurability(),
                "N I 4 N I must keep its infinite receiver, timings, damage, and four-coin reserve");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void coinsRechargeAndSecondaryThrowsOne(GameTestHelper helper) {
        Player player = armedPlayer(helper, 0, 0);
        ItemStack stack = player.getMainHandItem();
        for (int tick = 0; tick < NI4NIItem.COIN_RECHARGE_TICKS; tick++) {
            stack.getItem().inventoryTick(stack, helper.getLevel(), player, 0, true);
        }
        helper.assertTrue(NI4NIItem.coinCount(stack) == 1 && NI4NIItem.coinCharge(stack) == 0,
                "one coin must recharge after eighty inventory ticks");

        SednaGunItem.handleInput(player, GunInput.SECONDARY);
        List<NI4NICoinEntity> coins = helper.getLevel().getEntitiesOfClass(NI4NICoinEntity.class,
                new AABB(player.position(), player.position()).inflate(8.0D));
        helper.assertTrue(coins.size() == 1 && NI4NIItem.coinCount(stack) == 0,
                "secondary fire must throw one owned coin and spend it from the reserve");
        helper.assertTrue(coins.getFirst().getOwner() == player
                        && coins.getFirst().getDeltaMovement().y > 0.49D,
                "the tossed coin must keep its player and upward kick");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void arcChoosesCoinAndMultipliesTheRicochet(GameTestHelper helper) {
        Player player = armedPlayer(helper, 0, 0);
        Vec3 start = player.getEyePosition();
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 3, 5));
        NI4NICoinEntity coin = new NI4NICoinEntity(ModEntities.NI4NI_COIN.get(), helper.getLevel());
        coin.setOwner(player);
        coin.setPos(start.add(0.0D, 0.0D, 5.0D));
        helper.getLevel().addFreshEntity(coin);

        NI4NIBeamEntity beam = new NI4NIBeamEntity(helper.getLevel(), player,
                NI4NIItem.BASE_DAMAGE, Vec3.ZERO);
        beam.performHitscan();

        helper.assertTrue(!coin.isAlive() && beam.beamLength() < 6.0F,
                "the first arc must prefer and consume a coin on its ray");
        helper.assertTrue(!zombie.isAlive(),
                "the ricochet must multiply 35 damage by 1.25 before seeking a hostile");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void primaryFiresWithoutAmmoOrWear(GameTestHelper helper) {
        Player player = armedPlayer(helper, 4, 0);
        ItemStack stack = player.getMainHandItem();
        SednaGunItem.handleInput(player, GunInput.PRIMARY);

        List<NI4NIBeamEntity> beams = helper.getLevel().getEntitiesOfClass(NI4NIBeamEntity.class,
                new AABB(player.position(), player.position()).inflate(260.0D));
        helper.assertTrue(beams.size() == 1 && beams.getFirst().beamDamage() == 35.0F,
                "primary fire must create one 35-damage arc");
        helper.assertTrue(NI4NIItem.coinCount(stack) == 4
                        && NI4NIItem.state(stack) == NI4NIItem.GunState.COOLDOWN
                        && NI4NIItem.timer(stack) == 10,
                "primary fire must preserve coins and enter the ten-tick cooldown");
        helper.succeed();
    }

    private static Player armedPlayer(GameTestHelper helper, int coins, int charge) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(ModItems.GUN_N_I_4_N_I.get());
        NI4NIItem.setTestState(stack, NI4NIItem.GunState.IDLE, 0, coins, charge,
                NI4NIItem.GunAnimation.CYCLE, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }
}
