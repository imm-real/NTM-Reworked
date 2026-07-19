package com.hbm.ntm.hazard;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.item.HazmatArmorItem;
import com.hbm.ntm.radiation.RadiationSystem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContagionGameTests {
    private ContagionGameTests() { }

    @GameTest(template = "empty")
    public static void mkunicornInjectsExactSourceDurationAndConsumesItself(GameTestHelper helper) {
        var attacker = helper.makeMockPlayer(GameType.SURVIVAL);
        var target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        ItemStack syringe = new ItemStack(ModItems.SYRINGE_MKUNICORN.get());

        boolean result = syringe.getItem().hurtEnemy(syringe, target, attacker);
        check(helper, RadiationSystem.data(target).contagion() == ContagionSystem.CONTAGION_TICKS,
                "MKUNICORN must apply exactly three source hours of contagion");
        check(helper, syringe.isEmpty() && !result,
                "MKUNICORN must consume itself and preserve ItemSyringe#hitEntity's false return");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void contagionTransmitsAfterFiveMinutesAndContaminatesItems(GameTestHelper helper) {
        var source = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        var target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 2));
        var drop = EntityType.ITEM.create(helper.getLevel());
        check(helper, drop != null, "Expected an item entity");
        drop.setItem(new ItemStack(ModItems.REACHER.get()));
        drop.moveTo(helper.absolutePos(new BlockPos(3, 2, 3)).getCenter());
        helper.getLevel().addFreshEntity(drop);

        RadiationSystem.data(source).setContagion(ContagionSystem.TRANSMISSION_START - 20);
        ContagionSystem.tick(source);

        check(helper, RadiationSystem.data(target).contagion() == ContagionSystem.CONTAGION_TICKS,
                "An unprotected nearby living entity must receive the full three-hour contagion");
        check(helper, ContagionSystem.isContaminated(drop.getItem()),
                "Nearby dropped items must receive the exact ntmContagion marker");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bacteriaFilterStillRequiresACompleteHaz2Suit(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack helmet = new ItemStack(ModItems.HAZMAT_HELMET.get());
        HazmatArmorItem.installFilter(helmet, new ItemStack(ModItems.GAS_MASK_FILTER.get()));
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.HAZMAT_PLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.HAZMAT_LEGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.HAZMAT_BOOTS.get()));

        check(helper, HazardSystem.hasProtection(player, HazardProtection.BACTERIA, 0),
                "The standard filter must retain bacteria taxonomy coverage");
        check(helper, !ContagionSystem.hasCompleteProtection(player),
                "The source yellow suit must not be promoted to absent Haz2 protection");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void contagionPreservesDamageAndExpiryQuirks(GameTestHelper helper) {
        var target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        RadiationSystem.data(target).setContagion(1);
        check(helper, ContagionSystem.amplifyIncomingDamage(target, 99.0F) == 198.0F
                        && ContagionSystem.amplifyIncomingDamage(target, 100.0F) == 100.0F,
                "Contagion must double only source damage values below 100");

        ContagionSystem.tick(target);
        check(helper, RadiationSystem.data(target).contagion() == 0 && target.isAlive(),
                "The source's unreachable contagion-at-zero death branch must remain unreachable");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
