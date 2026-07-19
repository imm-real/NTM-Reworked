package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.item.G3Item;
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
public final class G3ZebraGameTests {
    private G3ZebraGameTests() { }

    @GameTest(template = "empty")
    public static void receiverKeepsSourceConfigurationAndSpawnsEmpty(GameTestHelper helper) {
        G3Item gun = ModItems.GUN_G3_ZEBRA.get();
        ItemStack stack = new ItemStack(gun);
        helper.assertTrue(gun.variant() == G3Item.Variant.ZEBRA
                        && gun.gunDurability() == 6_000.0F && gun.baseDamage() == 7.5F
                        && gun.gunCapacity() == 30 && gun.gunAutomatic()
                        && gun.recoilVerticalSigma() == 0.125F
                        && gun.recoilHorizontalSigma() == 0.125F,
                "Zebra must retain its B-side durability, damage, magazine and recoil");
        helper.assertTrue(gun.gunAimFovMultiplier() == 0.34F
                        && gun.gunScopeTexture() != null
                        && gun.gunScopeTexture().getPath().equals("textures/misc/scope_bolt.png"),
                "Zebra must retain the permanent scope and source 66 percent FOV reduction");
        helper.assertTrue(G3Item.rounds(stack) == 0
                        && G3Item.loadedAmmo(stack) == FiveFiveSixAmmoType.HOLLOW_POINT
                        && G3Item.mode(stack) == 0,
                "Fresh Zebra must spawn empty with JHP selected and automatic mode active");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "g3_zebra_ballistics_isolated")
    public static void everyLoadBecomesIncendiaryWithoutChangingItsBallistics(GameTestHelper helper) {
        Player player = armed(helper, FiveFiveSixAmmoType.HOLLOW_POINT, 1, 2);
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        BulletEntity fired = ownerShot(bullets(helper), player);
        helper.assertTrue(fired.ammoType() == FiveFiveSixAmmoType.HOLLOW_POINT
                        && fired.damage() == 11.25F && fired.incendiary()
                        && G3Item.rounds(player.getMainHandItem()) == 0,
                "Zebra JHP must fire as the source 7.5 x 1.5 incendiary load");

        ServerLevel level = helper.getLevel();
        Vec3 targetBase = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 2, 7)));
        Zombie target = zombie(level, targetBase);
        BulletEntity impact = new BulletEntity(level, player, FiveFiveSixAmmoType.SOFT_POINT,
                7.5F, 0.0F, new Vec3(targetBase.x - 4.0D, targetBase.y + 0.8D, targetBase.z),
                new Vec3(1.0D, 0.0D, 0.0D), true);
        level.addFreshEntity(impact);
        impact.tick();
        helper.assertTrue(target.getHealth() < 20.0F
                        && WeaponStatusEvents.phosphorusTicks(target) >= 300,
                "A successful Zebra hit must apply the source 300-tick phosphorus status");
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullReloadKeepsTheSelectedIncendiaryLoad(GameTestHelper helper) {
        Player player = armed(helper, FiveFiveSixAmmoType.ARMOR_PIERCING, 2, 2);
        player.getInventory().add(
                FiveFiveSixAmmoType.SOFT_POINT.createStack(ModItems.AMMO_STANDARD.get(), 30));
        player.getInventory().add(
                FiveFiveSixAmmoType.ARMOR_PIERCING.createStack(ModItems.AMMO_STANDARD.get(), 28));
        ItemStack gun = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        tickHeld(player, G3Item.RELOAD_TICKS);
        helper.assertTrue(G3Item.rounds(gun) == 30
                        && G3Item.loadedAmmo(gun) == FiveFiveSixAmmoType.ARMOR_PIERCING
                        && countAmmo(player, FiveFiveSixAmmoType.SOFT_POINT) == 30
                        && countAmmo(player, FiveFiveSixAmmoType.ARMOR_PIERCING) == 0,
                "Partial Zebra reloads must preserve the selected 5.56 identity");
        helper.succeed();
    }

    private static Player armed(GameTestHelper helper, FiveFiveSixAmmoType ammo, int rounds, int x) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_G3_ZEBRA.get());
        G3Item.setTestState(gun, G3Item.GunState.IDLE, 0, rounds, ammo, 0.0F, false, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(x, 3, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(Player player, int ticks) {
        G3Item gun = (G3Item) player.getMainHandItem().getItem();
        for (int i = 0; i < ticks; i++) {
            gun.inventoryTick(player.getMainHandItem(), player.level(), player,
                    player.getInventory().selected, true);
        }
    }

    private static List<BulletEntity> bullets(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntitiesOfClass(BulletEntity.class,
                new AABB(origin).inflate(64.0D));
    }

    private static BulletEntity ownerShot(List<BulletEntity> bullets, Player owner) {
        return bullets.stream().filter(bullet -> bullet.getOwner() == owner).findFirst()
                .orElseThrow(() -> new AssertionError("Expected a Zebra bullet owned by test player"));
    }

    private static int countAmmo(Player player, FiveFiveSixAmmoType type) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.AMMO_STANDARD.get()) && StandardAmmoTypes.fromStack(stack) == type) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static Zombie zombie(ServerLevel level, Vec3 position) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) throw new IllegalStateException("Could not create Zebra test zombie");
        zombie.setPos(position);
        level.addFreshEntity(zombie);
        return zombie;
    }
}
