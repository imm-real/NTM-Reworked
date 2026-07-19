package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.ShredderBeamEntity;
import com.hbm.ntm.entity.ShredderSubmunitionEntity;
import com.hbm.ntm.item.SednaGunItem;
import com.hbm.ntm.item.ShredderItem;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShredderGameTests {
    private ShredderGameTests() { }

    @GameTest(template = "empty")
    public static void sourceConfigurationAndEmptyBelt(GameTestHelper helper) {
        ShredderItem item = ModItems.GUN_AUTOSHOTGUN_SHREDDER.get();
        ItemStack fresh = new ItemStack(item);

        helper.assertTrue(ShredderItem.DURABILITY == 2_000 && ShredderItem.DRAW_TICKS == 10
                        && ShredderItem.INSPECT_TICKS == 33 && ShredderItem.FIRE_DELAY == 10
                        && ShredderItem.DRY_DELAY == 10 && ShredderItem.JAM_TICKS == 19,
                "Shredder action timing must match XFactory12ga:367");
        helper.assertTrue(item.baseDamage() == 50.0F && item.gunAutomatic() && item.gunBeltFed()
                        && item.gunCrosshair() == SednaCrosshair.L_CIRCLE && item.gunHideCrosshairWhenAimed(),
                "Shredder must be full-auto, 50 base damage, belt-fed, large-circle reticle, hide-when-aimed");
        helper.assertTrue(item.recoilVertical() == 1.5F && item.recoilVerticalSigma() == 1.5F
                        && item.recoilHorizontalSigma() == 0.5F,
                "Recoil must be the source setupRecoil(gaussian*1.5 + 1.5, gaussian*0.5)");
        // A fresh belt weapon stores no rounds; the G12/20 default is loose-container identity only.
        helper.assertTrue(ShredderItem.beltCount(fresh) == 0
                        && item.gunCapacity() == 0
                        && ShredderItem.loadedAmmo(fresh) == Shotgun12GaugeAmmoType.BUCKSHOT,
                "A fresh Shredder must have an empty belt and the source default G12 identity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void beamAndSubmunitionDamageLayering(GameTestHelper helper) {
        // Beam damage = 50 * mult * projectiles; submunition damage = beamDamage * mult (the double multiply).
        helper.assertTrue(ShredderItem.beamDamage(Shotgun12GaugeAmmoType.BUCKSHOT) == 50.0F
                        && ShredderItem.beamDamage(Shotgun12GaugeAmmoType.SLUG) == 50.0F
                        && ShredderItem.beamDamage(Shotgun12GaugeAmmoType.MAGNUM) == 100.0F
                        && ShredderItem.beamDamage(Shotgun12GaugeAmmoType.EXPLOSIVE) == 125.0F
                        && ShredderItem.beamDamage(Shotgun12GaugeAmmoType.PHOSPHORUS) == 50.0F,
                "Beam damage must be 50/50/50/100/125/50 for the six accepted loads");
        float buckSub = ShredderItem.beamDamage(Shotgun12GaugeAmmoType.BUCKSHOT)
                * Shotgun12GaugeAmmoType.BUCKSHOT.damageMultiplier();
        float magnumSub = ShredderItem.beamDamage(Shotgun12GaugeAmmoType.MAGNUM)
                * Shotgun12GaugeAmmoType.MAGNUM.damageMultiplier();
        float explosiveSub = ShredderItem.beamDamage(Shotgun12GaugeAmmoType.EXPLOSIVE)
                * Shotgun12GaugeAmmoType.EXPLOSIVE.damageMultiplier();
        helper.assertTrue(buckSub == 6.25F && magnumSub == 50.0F && explosiveSub == 312.5F,
                "Submunition damage must be beamDamage * mult: buckshot 6.25, magnum 50, explosive 312.5");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void beltFiresInventoryAmmoAndConsumesOne(GameTestHelper helper) {
        Player player = armedPlayer(helper, 0.0F, 2);
        player.getInventory().add(Shotgun12GaugeAmmoType.MAGNUM.createStack(ModItems.AMMO_STANDARD.get(), 4));

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        List<ShredderBeamEntity> beams = beams(helper, player);
        helper.assertTrue(beams.size() == 1
                        && beams.get(0).ammoType() == Shotgun12GaugeAmmoType.MAGNUM
                        && beams.get(0).beamDamage() == 100.0F,
                "The belt fires the magnum shredder beam (100 damage) from the inventory shell");
        helper.assertTrue(countAmmo(player, Shotgun12GaugeAmmoType.MAGNUM) == 3,
                "Firing consumes exactly one magnum shell from the inventory belt, not an internal magazine");
        helper.assertTrue(ShredderItem.state(player.getMainHandItem()) == ShredderItem.GunState.COOLDOWN
                        && ShredderItem.timer(player.getMainHandItem()) == 10,
                "A shot enters COOLDOWN with the ten-tick automatic delay");
        beams.forEach(net.minecraft.world.entity.Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void beltSelectsEarliestSlotThenSwitchesWhenEmptied(GameTestHelper helper) {
        Player player = armedPlayer(helper, 0.0F, 4);
        // Buckshot occupies the earlier slot, so it is fed first; one slug sits later.
        player.getInventory().add(Shotgun12GaugeAmmoType.BUCKSHOT.createStack(ModItems.AMMO_STANDARD.get(), 1));
        player.getInventory().add(Shotgun12GaugeAmmoType.SLUG.createStack(ModItems.AMMO_STANDARD.get(), 4));

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        List<ShredderBeamEntity> first = beams(helper, player);
        helper.assertTrue(first.size() == 1 && first.get(0).ammoType() == Shotgun12GaugeAmmoType.BUCKSHOT,
                "The earliest accepted shell (buckshot) is fed first");
        first.forEach(net.minecraft.world.entity.Entity::discard);

        // The single buckshot is spent; auto-refire ten ticks later must re-select slug and re-cache magtype.
        tickHeld(helper, player, 10);
        List<ShredderBeamEntity> second = beams(helper, player);
        helper.assertTrue(second.size() == 1 && second.get(0).ammoType() == Shotgun12GaugeAmmoType.SLUG,
                "With buckshot emptied the belt switches to the next accepted load (slug)");
        second.forEach(net.minecraft.world.entity.Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rejectedAmmoDryFiresAndConsumesNothing(GameTestHelper helper) {
        Player player = armedPlayer(helper, 0.0F, 2);
        // Black-powder loads are not accepted by the belt (only the six non-black-powder loads are).
        player.getInventory().add(
                Shotgun12GaugeAmmoType.BLACK_POWDER_BUCKSHOT.createStack(ModItems.AMMO_STANDARD.get(), 8));
        player.getInventory().add(
                Shotgun12GaugeAmmoType.BLACK_POWDER_SLUG.createStack(ModItems.AMMO_STANDARD.get(), 8));

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        ItemStack stack = player.getMainHandItem();
        helper.assertTrue(beams(helper, player).isEmpty()
                        && ShredderItem.animation(stack) == ShredderItem.GunAnimation.CYCLE_DRY
                        && ShredderItem.state(stack) == ShredderItem.GunState.COOLDOWN,
                "Only black-powder shells present -> the belt reports zero ammo and the gun dry-fires");
        helper.assertTrue(countAmmo(player, Shotgun12GaugeAmmoType.BLACK_POWDER_BUCKSHOT) == 8
                        && countAmmo(player, Shotgun12GaugeAmmoType.BLACK_POWDER_SLUG) == 8,
                "A rejected dry-fire must not consume any shell");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void heldTriggerAutoFiresThenDryLoopsWhenEmpty(GameTestHelper helper) {
        Player player = armedPlayer(helper, 0.0F, 2);
        player.getInventory().add(Shotgun12GaugeAmmoType.SLUG.createStack(ModItems.AMMO_STANDARD.get(), 3));

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        helper.assertTrue(beams(helper, player).size() == 1
                        && countAmmo(player, Shotgun12GaugeAmmoType.SLUG) == 2
                        && ShredderItem.primaryHeld(player.getMainHandItem()),
                "The first press fires one beam and stores the held state");
        tickHeld(helper, player, 9);
        helper.assertTrue(countAmmo(player, Shotgun12GaugeAmmoType.SLUG) == 2,
                "No refire may occur before the ten-tick delay elapses");
        tickHeld(helper, player, 1);
        helper.assertTrue(countAmmo(player, Shotgun12GaugeAmmoType.SLUG) == 1,
                "Holding refires exactly once every ten ticks (COOLDOWN boundary)");
        tickHeld(helper, player, 10);
        helper.assertTrue(countAmmo(player, Shotgun12GaugeAmmoType.SLUG) == 0,
                "The third round fires on the next boundary, emptying the belt");
        discardBeams(helper, player);

        // With the belt empty the held trigger loops CYCLE_DRY (refireAfterDry) and spawns no beam.
        tickHeld(helper, player, 20);
        helper.assertTrue(beams(helper, player).isEmpty()
                        && ShredderItem.animation(player.getMainHandItem()) == ShredderItem.GunAnimation.CYCLE_DRY
                        && ShredderItem.state(player.getMainHandItem()) == ShredderItem.GunState.COOLDOWN,
                "An emptied automatic keeps dry-cycling while held and fires no beam");

        SednaGunItem.handleInput(player, GunInput.PRIMARY_RELEASE);
        tickHeld(helper, player, 10);
        helper.assertTrue(ShredderItem.state(player.getMainHandItem()) == ShredderItem.GunState.IDLE
                        && !ShredderItem.primaryHeld(player.getMainHandItem()),
                "Releasing the trigger breaks the dry loop back to IDLE (belt never auto-reloads)");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reloadKeyPlaysInspectWithoutReloading(GameTestHelper helper) {
        Player player = armedPlayer(helper, 0.0F, 2);
        player.getInventory().add(Shotgun12GaugeAmmoType.BUCKSHOT.createStack(ModItems.AMMO_STANDARD.get(), 8));
        ItemStack stack = player.getMainHandItem();

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        helper.assertTrue(ShredderItem.animation(stack) == ShredderItem.GunAnimation.INSPECT
                        && ShredderItem.state(stack) == ShredderItem.GunState.IDLE,
                "MagazineBelt.canReload() is false, so RELOAD only plays a cancelable INSPECT and stays IDLE");
        helper.assertTrue(countAmmo(player, Shotgun12GaugeAmmoType.BUCKSHOT) == 8
                        && beams(helper, player).isEmpty(),
                "The inspect must not consume ammo or fire");
        helper.succeed();
    }

    // The shredder beam is a 250-block hitscan projectile: this test places a stone floor across
    // rel (3..7, 2, 3..7), fires straight down from rel (5, 6, 5), and then queries submunitions in
    // a 128-block box. That interaction/query volume does not fit the 1x1x1 "empty" template, so in
    // the shared defaultBatch a neighbouring test's transient blocks/entities can land in the beam's
    // start or hitscan column and make the block clip miss. Run it isolated (its own serial batch,
    // clean arena) exactly like the other long-range projectile/query tests (XFactory ballistics,
    // detonator/demolition/battery-drop). Production behaviour is unchanged.
    @GameTest(template = "empty", batch = "shredder_beam_isolated")
    public static void beamIntoBlockSpawnsSubmunitionsAlongFace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = armedPlayer(helper, 0.0F, 5);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 5, 5))));
        player.setYRot(0.0F);
        player.setXRot(90.0F); // straight down onto a floor, with no entity in the beam path
        for (int dx = 3; dx <= 7; dx++) {
            for (int dz = 3; dz <= 7; dz++) {
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(dx, 2, dz)),
                        Blocks.STONE.defaultBlockState());
            }
        }
        player.getInventory().add(Shotgun12GaugeAmmoType.BUCKSHOT.createStack(ModItems.AMMO_STANDARD.get(), 4));

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        List<ShredderSubmunitionEntity> subs = submunitions(helper, player);
        helper.assertTrue(subs.size() == 8
                        && subs.stream().allMatch(s -> s.ammoType() == Shotgun12GaugeAmmoType.BUCKSHOT),
                "A buckshot beam hitting a block must spawn eight plasma submunitions");
        discardBeams(helper, player);
        subs.forEach(net.minecraft.world.entity.Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void beamIntoEntityDealsNoDirectDamageButScattersSubmunitions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = armedPlayer(helper, 0.0F, 2);
        Vec3 feet = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 4)));
        player.setPos(feet);
        player.setYRot(-90.0F); // look toward +X (east)
        player.setXRot(0.0F);

        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) throw new IllegalStateException("could not create shredder test zombie");
        zombie.setPos(feet.add(3.0D, 0.0D, 0.0D));
        float fullHealth = zombie.getHealth();
        level.addFreshEntity(zombie);

        player.getInventory().add(Shotgun12GaugeAmmoType.MAGNUM.createStack(ModItems.AMMO_STANDARD.get(), 2));
        SednaGunItem.handleInput(player, GunInput.PRIMARY);

        List<ShredderSubmunitionEntity> subs = submunitions(helper, player);
        helper.assertTrue(zombie.getHealth() == fullHealth,
                "An entity beam impact deals NO direct beam damage (only the submunition burst)");
        helper.assertTrue(subs.size() == 4,
                "A magnum beam hitting an entity scatters four plasma submunitions");
        discardBeams(helper, player);
        subs.forEach(net.minecraft.world.entity.Entity::discard);
        zombie.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void explosiveSubmunitionExplodesOnImpact(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player owner = armedPlayer(helper, 0.0F, 2);
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 5, 4)));
        for (int dx = 3; dx <= 5; dx++) {
            for (int dz = 3; dz <= 5; dz++) {
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(dx, 3, dz)),
                        Blocks.STONE.defaultBlockState());
            }
        }

        Zombie victim = EntityType.ZOMBIE.create(level);
        if (victim == null) throw new IllegalStateException("could not create explosive test zombie");
        victim.setPos(origin.add(0.0D, -1.5D, 1.0D));
        float before = victim.getHealth();
        level.addFreshEntity(victim);

        ShredderSubmunitionEntity sub = new ShredderSubmunitionEntity(level, owner,
                Shotgun12GaugeAmmoType.EXPLOSIVE, 312.5F, origin, new Vec3(0.0D, -1.0D, 0.0D));
        level.addFreshEntity(sub);
        for (int i = 0; i < 10 && sub.isAlive(); i++) sub.tick();

        helper.assertTrue(!sub.isAlive(),
                "An explosive submunition detonates and dies on its first impact (inherited onImpact)");
        helper.assertTrue(victim.getHealth() < before,
                "The submunition's range-two explosion damages a nearby entity");
        victim.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void phosphorusSubmunitionIgnitesAndDamages(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player owner = armedPlayer(helper, 0.0F, 2);
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 4, 4)));

        Zombie victim = EntityType.ZOMBIE.create(level);
        if (victim == null) throw new IllegalStateException("could not create phosphorus test zombie");
        victim.setPos(origin.add(1.5D, -1.0D, 0.0D));
        level.addFreshEntity(victim);

        ShredderSubmunitionEntity sub = new ShredderSubmunitionEntity(level, owner,
                Shotgun12GaugeAmmoType.PHOSPHORUS, 6.25F, origin, new Vec3(1.0D, 0.0D, 0.0D));
        level.addFreshEntity(sub);
        for (int i = 0; i < 12 && sub.isAlive(); i++) sub.tick();

        helper.assertTrue(WeaponStatusEvents.phosphorusTicks(victim) >= 300,
                "A phosphorus submunition applies the source >=300 tick burn on a living hit");
        victim.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void submunitionRicochetsOffBlocksAndDiesAfterThree(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player owner = armedPlayer(helper, 0.0F, 2);
        // Hollow 5x5x5 stone box so the slow submunition keeps hitting walls.
        for (int x = 3; x <= 7; x++) {
            for (int y = 3; y <= 7; y++) {
                for (int z = 3; z <= 7; z++) {
                    boolean shell = x == 3 || x == 7 || y == 3 || y == 7 || z == 3 || z == 7;
                    if (shell) {
                        level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, z)),
                                Blocks.STONE.defaultBlockState());
                    }
                }
            }
        }
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 5, 5)));
        ShredderSubmunitionEntity sub = new ShredderSubmunitionEntity(level, owner,
                Shotgun12GaugeAmmoType.SLUG, 50.0F, center, new Vec3(1.0D, 0.05D, 0.03D));
        level.addFreshEntity(sub);
        for (int i = 0; i < 40 && sub.isAlive(); i++) sub.tick();

        helper.assertTrue(!sub.isAlive() && sub.ricochets() >= 3,
                "The 90-degree submunition ricochets up to three times off blocks, then dies on the next hit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wearReducesBeamDamage(GameTestHelper helper) {
        // wearDamageMultiplier: 1.0 below 75% wear, then linear falloff to 0.5 at 100%.
        helper.assertTrue(ShredderItem.wearDamageMultiplier(0.0F) == 1.0F
                        && ShredderItem.wearDamageMultiplier(1_500.0F) == 1.0F
                        && Math.abs(ShredderItem.wearDamageMultiplier(2_000.0F) - 0.5F) < 1.0E-4F,
                "Beam damage falls off only past 75% wear, reaching half at full wear");

        Player player = armedPlayer(helper, 2_000.0F, 2);
        player.getInventory().add(Shotgun12GaugeAmmoType.SLUG.createStack(ModItems.AMMO_STANDARD.get(), 2));
        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        List<ShredderBeamEntity> beams = beams(helper, player);
        helper.assertTrue(beams.size() == 1 && Math.abs(beams.get(0).beamDamage() - 25.0F) < 1.0E-3F,
                "A fully worn slug beam deals 50 * 0.5 = 25 damage");
        beams.forEach(net.minecraft.world.entity.Entity::discard);
        helper.succeed();
    }

    // ----- helpers -----

    private static Player armedPlayer(GameTestHelper helper, float wear, int x) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_AUTOSHOTGUN_SHREDDER.get());
        ShredderItem.setTestState(gun, ShredderItem.GunState.IDLE, 0,
                Shotgun12GaugeAmmoType.BUCKSHOT, wear, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(x, 2, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static void tickHeld(GameTestHelper helper, Player player, int ticks) {
        ShredderItem gun = (ShredderItem) player.getMainHandItem().getItem();
        for (int i = 0; i < ticks; i++) {
            gun.inventoryTick(player.getMainHandItem(), helper.getLevel(), player,
                    player.getInventory().selected, true);
        }
    }

    private static List<ShredderBeamEntity> beams(GameTestHelper helper, Player owner) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntitiesOfClass(ShredderBeamEntity.class,
                new AABB(origin).inflate(128.0D), beam -> beam.getOwner() == owner);
    }

    private static void discardBeams(GameTestHelper helper, Player owner) {
        beams(helper, owner).forEach(net.minecraft.world.entity.Entity::discard);
    }

    private static List<ShredderSubmunitionEntity> submunitions(GameTestHelper helper, Player owner) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntitiesOfClass(ShredderSubmunitionEntity.class,
                new AABB(origin).inflate(128.0D), sub -> sub.getOwner() == owner);
    }

    private static int countAmmo(Player player, Shotgun12GaugeAmmoType type) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.AMMO_STANDARD.get()) && StandardAmmoTypes.fromStack(stack) == type) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
