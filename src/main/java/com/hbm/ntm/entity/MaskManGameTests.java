package com.hbm.ntm.entity;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.item.MaskFilterStorage;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaskManGameTests {
    private MaskManGameTests() {
    }

    @GameTest(template = "empty")
    public static void sourceBossStatsAndDamageCapStayIntact(GameTestHelper helper) {
        MaskManEntity boss = helper.spawn(ModEntities.MASK_MAN.get(), new BlockPos(2, 1, 2));
        boss.setNoAi(true);
        helper.assertTrue(boss.getMaxHealth() == 1_000.0F
                        && boss.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0.25D
                        && boss.getAttributeValue(Attributes.FOLLOW_RANGE) == 100.0D
                        && boss.getAttributeValue(Attributes.ATTACK_DAMAGE) == 15.0D
                        && boss.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) == 1.0D,
                "Mask Man must retain his source boss attributes");

        boss.hurt(helper.getLevel().damageSources().generic(), 200.0F);
        helper.assertTrue(boss.getHealth() == 875.0F,
                "Damage above 50 must be halved only for its excess");
        float beforeFire = boss.getHealth();
        boss.invulnerableTime = 0;
        boss.hurt(helper.getLevel().damageSources().inFire(), 40.0F);
        helper.assertTrue(boss.getHealth() == beforeFire,
                "Mask Man must remain immune to fire");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceDropsIncludeFilteredMaskCoinAndSkull(GameTestHelper helper) {
        BlockPos position = new BlockPos(2, 1, 2);
        BlockPos absolutePosition = helper.absolutePos(position);
        MaskManEntity boss = helper.spawn(ModEntities.MASK_MAN.get(), position);
        boss.setNoAi(true);
        boss.hurt(helper.getLevel().damageSources().generic(), 10_000.0F);

        helper.runAfterDelay(2, () -> {
            boolean mask = false;
            boolean coin = false;
            boolean skull = false;
            for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(absolutePosition).inflate(4.0D))) {
                if (item.getItem().is(ModItems.GAS_MASK_M65.get())) {
                    mask = MaskFilterStorage.installed(item.getItem()).is(ModItems.GAS_MASK_FILTER_COMBO.get());
                } else if (item.getItem().is(ModItems.COIN_MASKMAN.get())) {
                    coin = true;
                } else if (item.getItem().is(Items.SKELETON_SKULL)) {
                    skull = true;
                }
            }
            helper.assertTrue(mask && coin && skull,
                    "Mask Man must drop his combo-filtered M65, coin, and skull");
            helper.succeed();
        });
    }

}
