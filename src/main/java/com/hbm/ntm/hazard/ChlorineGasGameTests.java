package com.hbm.ntm.hazard;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.ChlorineGasBlock;
import com.hbm.ntm.item.HazmatArmorItem;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.RenderShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChlorineGasGameTests {
    private ChlorineGasGameTests() { }

    @GameTest(template = "empty")
    public static void chlorineAppliesAllFiveExactSourceEffects(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ChlorineGasBlock gas = ModBlocks.CHLORINE_GAS.get();
        gas.defaultBlockState().entityInside(helper.getLevel(),
                helper.absolutePos(new BlockPos(2, 2, 2)), player);

        check(helper, effect(player, MobEffects.BLINDNESS, 100, 0)
                        && effect(player, MobEffects.POISON, 400, 2)
                        && effect(player, MobEffects.WITHER, 20, 1)
                        && effect(player, MobEffects.MOVEMENT_SLOWDOWN, 600, 1)
                        && effect(player, MobEffects.DIG_SLOWDOWN, 600, 2),
                "Unprotected Chlorine Gas must apply the source blindness, poison, wither, slowness and mining-fatigue effects");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lungFilterBlocksEveryEffectAndTakesOneWear(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack helmet = new ItemStack(ModItems.HAZMAT_HELMET.get());
        HazmatArmorItem.installFilter(helmet, new ItemStack(ModItems.GAS_MASK_FILTER.get()));
        player.setItemSlot(EquipmentSlot.HEAD, helmet);

        ModBlocks.CHLORINE_GAS.get().defaultBlockState().entityInside(helper.getLevel(),
                helper.absolutePos(new BlockPos(2, 2, 2)), player);
        check(helper, player.getActiveEffects().isEmpty(),
                "Chemical-gas protection must block every Chlorine Gas effect");
        check(helper, HazmatArmorItem.installedFilter(helmet).getDamageValue() == 1,
                "Each protected Chlorine Gas collision must wear the filter by exactly one");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chlorineRetainsVisibleReplaceableNonCollidingGasTraits(GameTestHelper helper) {
        var state = ModBlocks.CHLORINE_GAS.get().defaultBlockState();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        check(helper, state.getRenderShape() == RenderShape.MODEL && state.canBeReplaced()
                        && state.getShape(helper.getLevel(), pos).isEmpty()
                        && state.getCollisionShape(helper.getLevel(), pos).isEmpty(),
                "Chlorine Gas must remain visible, replaceable, shapeless and non-colliding");
        check(helper, ModBlocks.CHLORINE_GAS.get().getExplosionResistance() == 0.0F,
                "Chlorine Gas must preserve zero source resistance");
        helper.succeed();
    }

    private static boolean effect(net.minecraft.world.entity.LivingEntity living,
                                  net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
                                  int duration, int amplifier) {
        var instance = living.getEffect(effect);
        return instance != null && instance.getDuration() == duration && instance.getAmplifier() == amplifier;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
