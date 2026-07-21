package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.BolterItem;
import com.hbm.ntm.item.SednaGunItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
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
public final class BolterGameTests {
    private BolterGameTests() { }

    @GameTest(template = "empty")
    public static void ammoIdentitiesKeepTheirSourceProfiles(GameTestHelper helper) {
        for (Bolt75AmmoType type : Bolt75AmmoType.values()) {
            ItemStack stack = type.createStack(ModItems.AMMO_STANDARD.get(), 8);
            helper.assertTrue(StandardAmmoTypes.fromStack(stack) == type
                            && StandardAmmoTypes.fromLegacyMetadata(type.legacyMetadata()) == type,
                    type + " must survive both modern components and source metadata");
        }
        helper.assertTrue(Bolt75AmmoType.STANDARD.legacyMetadata() == 38
                        && Bolt75AmmoType.INCENDIARY.legacyMetadata() == 39
                        && Bolt75AmmoType.EXPLOSIVE.legacyMetadata() == 40,
                ".75 bolts must keep source EnumAmmo ordinals 38-40");
        helper.assertTrue(Bolt75AmmoType.STANDARD.tinyImpactExplosion()
                        && Bolt75AmmoType.STANDARD.impactExplosionRange() == 2.0D
                        && Bolt75AmmoType.INCENDIARY.damageMultiplier() == 0.8F
                        && Bolt75AmmoType.INCENDIARY.armorPiercing() == 0.1F
                        && Bolt75AmmoType.INCENDIARY.phosphorusTicks() == 300
                        && Bolt75AmmoType.INCENDIARY.phosphorusOnImpact(),
                "Standard and incendiary bolts must keep their source impact behavior");
        helper.assertTrue(Bolt75AmmoType.EXPLOSIVE.damageMultiplier() == 1.5F
                        && Bolt75AmmoType.EXPLOSIVE.armorPiercing() == -0.25F
                        && Bolt75AmmoType.EXPLOSIVE.impactExplosionRange() == 5.0D
                        && Bolt75AmmoType.EXPLOSIVE.explodesBeforeDirectHit()
                        && Bolt75AmmoType.EXPLOSIVE.tracerDarkColor() == 0xFF9E082E
                        && Bolt75AmmoType.EXPLOSIVE.tracerLightColor() == 0xFFFF8A79,
                "Explosive bolts must keep their source damage, armor, blast and tracer profile");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void receiverKeepsSourceConfigurationAndSpawnsEmpty(GameTestHelper helper) {
        BolterItem gun = ModItems.GUN_BOLTER.get();
        ItemStack stack = new ItemStack(gun);
        helper.assertTrue(gun.gunDurability() == 3_000.0F && gun.gunCapacity() == 30
                        && gun.gunAutomatic() && gun.gunCrosshair() == SednaCrosshair.L_CIRCLE
                        && gun.gunAimFovMultiplier() == 0.67F
                        && gun.recoilVerticalSigma() == 1.5F
                        && gun.recoilHorizontalSigma() == 1.5F,
                "Bolter must keep the source receiver, aim and gaussian recoil configuration");
        helper.assertTrue(BolterItem.DRAW_TICKS == 20 && BolterItem.INSPECT_TICKS == 31
                        && BolterItem.FIRE_DELAY == 2 && BolterItem.RELOAD_TICKS == 40
                        && BolterItem.JAM_TICKS == 55 && BolterItem.BASE_DAMAGE == 15.0F
                        && BolterItem.HIP_SPREAD == 0.005F,
                "Bolter must keep source timings, base damage and innate spread");
        helper.assertTrue(BolterItem.rounds(stack) == 0
                        && BolterItem.loadedAmmo(stack) == Bolt75AmmoType.STANDARD,
                "Fresh Bolter must be empty with standard .75 bolts selected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void automaticReceiverRefiresEveryTwoTicks(GameTestHelper helper) {
        Player player = armed(helper, Bolt75AmmoType.EXPLOSIVE, 3, 2);
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        tickHeld(player, BolterItem.FIRE_DELAY);
        List<BulletEntity> shots = bullets(helper).stream()
                .filter(bullet -> bullet.getOwner() == player).toList();
        helper.assertTrue(shots.size() == 2 && shots.stream().allMatch(
                        shot -> shot.ammoType() == Bolt75AmmoType.EXPLOSIVE
                                && shot.damage() == 22.5F)
                        && BolterItem.rounds(player.getMainHandItem()) == 1,
                "Held fire must launch a 22.5-damage explosive bolt every two ticks");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullReloadLocksPartialMagazineIdentity(GameTestHelper helper) {
        Player player = armed(helper, Bolt75AmmoType.INCENDIARY, 2, 2);
        player.getInventory().add(
                Bolt75AmmoType.STANDARD.createStack(ModItems.AMMO_STANDARD.get(), 30));
        player.getInventory().add(
                Bolt75AmmoType.INCENDIARY.createStack(ModItems.AMMO_STANDARD.get(), 28));
        ItemStack gun = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(BolterItem.amountBeforeReload(gun) == 2
                        && BolterItem.timer(gun) == BolterItem.RELOAD_TICKS,
                "Bolter must begin its forty-tick full reload from two rounds");
        tickHeld(player, BolterItem.RELOAD_TICKS);
        helper.assertTrue(BolterItem.rounds(gun) == 30
                        && BolterItem.loadedAmmo(gun) == Bolt75AmmoType.INCENDIARY
                        && countAmmo(player, Bolt75AmmoType.STANDARD) == 30
                        && countAmmo(player, Bolt75AmmoType.INCENDIARY) == 0,
                "Partial reload must consume only the selected incendiary bolt identity");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "bolter_impact_isolated")
    public static void incendiaryImpactAppliesPhosphorusBeforeDamage(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ServerLevel level = helper.getLevel();
        Vec3 targetBase = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 2, 7)));
        Zombie target = EntityType.ZOMBIE.create(level);
        if (target == null) throw new IllegalStateException("could not create Bolter target");
        target.setPos(targetBase);
        target.setInvulnerable(true);
        level.addFreshEntity(target);
        BulletEntity bolt = new BulletEntity(level, player, Bolt75AmmoType.INCENDIARY,
                12.0F, 0.0F, new Vec3(targetBase.x - 4.0D, targetBase.y + 0.8D, targetBase.z),
                new Vec3(1.0D, 0.0D, 0.0D));
        level.addFreshEntity(bolt);
        bolt.tick();
        helper.assertTrue(target.getHealth() == 20.0F
                        && WeaponStatusEvents.phosphorusTicks(target) >= 300,
                "Incendiary bolts must apply phosphorus even when direct damage is rejected");
        target.discard();
        helper.succeed();
    }

    private static Player armed(GameTestHelper helper, Bolt75AmmoType ammo, int rounds, int x) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_BOLTER.get());
        BolterItem.setTestState(gun, BolterItem.GunState.IDLE, 0, rounds, ammo,
                0.0F, false, false, BolterItem.GunAnimation.CYCLE);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(x, 3, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(Player player, int ticks) {
        BolterItem gun = (BolterItem) player.getMainHandItem().getItem();
        for (int i = 0; i < ticks; i++) {
            gun.inventoryTick(player.getMainHandItem(), player.level(), player,
                    player.getInventory().selected, true);
        }
    }

    private static List<BulletEntity> bullets(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(BulletEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(64.0D));
    }

    private static int countAmmo(Player player, Bolt75AmmoType type) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.AMMO_STANDARD.get())
                    && StandardAmmoTypes.fromStack(stack) == type) count += stack.getCount();
        }
        return count;
    }
}
