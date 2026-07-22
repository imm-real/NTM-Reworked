package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.AutoshotgunHereticItem;
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
public final class HereticGameTests {
    private HereticGameTests() { }

    @GameTest(template = "empty")
    public static void keepsItsDebugTenGaugeConfiguration(GameTestHelper helper) {
        AutoshotgunHereticItem item = ModItems.GUN_AUTOSHOTGUN_HERETIC.get();
        ItemStack fresh = new ItemStack(item);
        helper.assertTrue(AutoshotgunHereticItem.DURABILITY == 0
                        && !item.gunShowDurability()
                        && item.gunWear(fresh) == 0.0F
                        && AutoshotgunHereticItem.DRAW_TICKS == 20
                        && AutoshotgunHereticItem.INSPECT_TICKS == 65
                        && AutoshotgunHereticItem.FIRE_DELAY == 3
                        && AutoshotgunHereticItem.RELOAD_TICKS == 110,
                "The Heretic must retain its zero-wear debug receiver and source timings");
        helper.assertTrue(item.gunCapacity() == 250
                        && item.baseDamage() == 100.0F
                        && item.gunAutomatic()
                        && item.gunCrosshair() == SednaCrosshair.L_CIRCLE
                        && !item.gunHideCrosshairWhenAimed(),
                "The Heretic must retain its 250-shell automatic receiver and visible reticle");
        helper.assertTrue(AutoshotgunHereticItem.rounds(fresh) == 0
                        && AutoshotgunHereticItem.loadedAmmo(fresh) == Shotgun10GaugeAmmoType.BUCKSHOT,
                "A fresh Heretic must be empty and identify its default load as G10 buckshot");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void firesTenGaugeWithoutWear(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_AUTOSHOTGUN_HERETIC.get());
        AutoshotgunHereticItem.setTestState(gun, AutoshotgunHereticItem.GunState.IDLE, 0, 2,
                Shotgun10GaugeAmmoType.BUCKSHOT, 0.0F, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        List<BulletEntity> pellets = helper.getLevel().getEntitiesOfClass(BulletEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(64.0D),
                bullet -> bullet.getOwner() == player);
        helper.assertTrue(pellets.size() == 10
                        && pellets.stream().allMatch(pellet -> pellet.damage() == 10.0F),
                "G10 buckshot must fire ten 10-damage pellets from the 100-damage receiver");
        helper.assertTrue(AutoshotgunHereticItem.rounds(gun) == 1
                        && AutoshotgunHereticItem.timer(gun) == 3
                        && AutoshotgunHereticItem.wear(gun) == 0.0F,
                "Firing must consume one shell, use the three-tick delay and never add wear");
        helper.succeed();
    }
}
