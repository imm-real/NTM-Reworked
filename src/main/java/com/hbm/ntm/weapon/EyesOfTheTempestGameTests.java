package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.AberratorBeamEntity;
import com.hbm.ntm.entity.LingeringFireEntity;
import com.hbm.ntm.item.EyesOfTheTempestItem;
import com.hbm.ntm.item.SednaGunItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
public final class EyesOfTheTempestGameTests {
    private EyesOfTheTempestGameTests() { }

    @GameTest(template = "empty")
    public static void ammunitionKeepsTheSourceProfiles(GameTestHelper helper) {
        for (AberratorAmmoType type : AberratorAmmoType.values()) {
            ItemStack stack = type.createStack(ModItems.AMMO_SECRET.get(), 3);
            helper.assertTrue(SecretAmmoTypes.fromStack(stack) == type,
                    type + " must survive ammo_secret component serialization");
        }
        helper.assertTrue(AberratorAmmoType.V9.legacyMetadata() == 5
                        && AberratorAmmoType.BLACK_LIGHTNING.legacyMetadata() == 7
                        && AberratorAmmoType.V9.armorThresholdNegation() == 50.0F
                        && AberratorAmmoType.V9.armorPiercing() == 0.5F
                        && AberratorAmmoType.V9.spread() == 0.0F
                        && !AberratorAmmoType.V9.blackLightning()
                        && AberratorAmmoType.BLACK_LIGHTNING.blackLightning(),
                ".35-800 V9 identities and beam profiles must match XFactory35800");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void receiverKeepsTheSourceDualContract(GameTestHelper helper) {
        EyesOfTheTempestItem gun = ModItems.GUN_ABERRATOR_EOTT.get();
        ItemStack stack = new ItemStack(gun);
        helper.assertTrue(EyesOfTheTempestItem.DURABILITY == 2_000
                        && EyesOfTheTempestItem.CAPACITY == 5
                        && EyesOfTheTempestItem.DRAW_TICKS == 10
                        && EyesOfTheTempestItem.INSPECT_TICKS == 26
                        && EyesOfTheTempestItem.FIRE_DELAY == 13
                        && EyesOfTheTempestItem.DRY_TICKS == 21
                        && EyesOfTheTempestItem.RELOAD_TICKS == 51
                        && EyesOfTheTempestItem.BASE_DAMAGE == 100.0F
                        && !gun.gunAiming(stack) && gun.gunHasMirroredHud()
                        && !gun.gunShowDurability() && !gun.gunShowMirroredDurability(),
                "Eyes Of The Tempest must retain its dual 2000/5/100 receiver contract");
        helper.assertTrue(EyesOfTheTempestItem.rounds(stack, 0) == 0
                        && EyesOfTheTempestItem.rounds(stack, 1) == 0
                        && EyesOfTheTempestItem.loadedAmmo(stack, 0) == AberratorAmmoType.V9
                        && EyesOfTheTempestItem.loadedAmmo(stack, 1) == AberratorAmmoType.V9,
                "both fresh magazines must be empty with V9 selected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void primaryAndSecondaryFireIndependentBeams(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_ABERRATOR_EOTT.get());
        EyesOfTheTempestItem.setTestState(gun, 0, EyesOfTheTempestItem.GunState.IDLE,
                0, 1, AberratorAmmoType.V9);
        EyesOfTheTempestItem.setTestState(gun, 1, EyesOfTheTempestItem.GunState.IDLE,
                0, 1, AberratorAmmoType.BLACK_LIGHTNING);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2))));
        player.setYRot(0.0F);
        player.setXRot(0.0F);

        SednaGunItem.handleInput(player, GunInput.PRIMARY);
        SednaGunItem.handleInput(player, GunInput.SECONDARY);
        List<AberratorBeamEntity> beams = beams(helper);
        helper.assertTrue(beams.size() == 2
                        && beams.stream().allMatch(beam -> beam.beamDamage() == 100.0F)
                        && beams.stream().anyMatch(beam -> beam.ammoType() == AberratorAmmoType.V9)
                        && beams.stream().anyMatch(beam ->
                                beam.ammoType() == AberratorAmmoType.BLACK_LIGHTNING),
                "left and right inputs must spawn their own 100-damage beam identities");
        helper.assertTrue(EyesOfTheTempestItem.rounds(gun, 0) == 0
                        && EyesOfTheTempestItem.rounds(gun, 1) == 0
                        && EyesOfTheTempestItem.timer(gun, 0) == 13
                        && EyesOfTheTempestItem.timer(gun, 1) == 13,
                "both receivers must consume and cool down independently");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reloadKeepsBothMagazineTypesIndependent(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.GUN_ABERRATOR_EOTT.get());
        EyesOfTheTempestItem.setTestState(gun, 0, EyesOfTheTempestItem.GunState.IDLE,
                0, 1, AberratorAmmoType.V9);
        EyesOfTheTempestItem.setTestState(gun, 1, EyesOfTheTempestItem.GunState.IDLE,
                0, 1, AberratorAmmoType.BLACK_LIGHTNING);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.getInventory().add(AberratorAmmoType.V9.createStack(ModItems.AMMO_SECRET.get(), 4));
        player.getInventory().add(AberratorAmmoType.BLACK_LIGHTNING
                .createStack(ModItems.AMMO_SECRET.get(), 4));

        SednaGunItem.handleInput(player, GunInput.RELOAD);
        tick(player, EyesOfTheTempestItem.RELOAD_TICKS);
        helper.assertTrue(EyesOfTheTempestItem.rounds(gun, 0) == 5
                        && EyesOfTheTempestItem.rounds(gun, 1) == 5
                        && EyesOfTheTempestItem.loadedAmmo(gun, 0) == AberratorAmmoType.V9
                        && EyesOfTheTempestItem.loadedAmmo(gun, 1)
                        == AberratorAmmoType.BLACK_LIGHTNING,
                "a shared reload must fill both magazines without mixing their loaded identities");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blackLightningBurnsTargetsAndLeavesItsField(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        LivingEntity target = helper.spawn(EntityType.PIG, new BlockPos(3, 4, 7));
        Vec3 targetCenter = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        AberratorBeamEntity targetBeam = new AberratorBeamEntity(helper.getLevel(), owner,
                AberratorAmmoType.BLACK_LIGHTNING, 100.0F,
                targetCenter.add(0.0D, 0.0D, -5.0D), new Vec3(0.0D, 0.0D, 1.0D));
        targetBeam.performHitscan();
        helper.assertTrue(WeaponStatusEvents.blackFireTicks(target) == 200,
                "Black Lightning must add 200 source black-fire ticks on a living hit");

        BlockPos block = new BlockPos(7, 4, 7);
        helper.setBlock(block, Blocks.STONE);
        Vec3 blockCenter = Vec3.atCenterOf(helper.absolutePos(block));
        AberratorBeamEntity blockBeam = new AberratorBeamEntity(helper.getLevel(), owner,
                AberratorAmmoType.BLACK_LIGHTNING, 100.0F,
                blockCenter.add(0.0D, 0.0D, -5.0D), new Vec3(0.0D, 0.0D, 1.0D));
        blockBeam.performHitscan();
        List<LingeringFireEntity> fields = helper.getLevel().getEntitiesOfClass(
                LingeringFireEntity.class, new AABB(blockCenter, blockCenter).inflate(2.0D));
        helper.assertTrue(fields.size() == 1
                        && fields.getFirst().kind() == LingeringFireEntity.Kind.BLACK
                        && fields.getFirst().remainingDuration() == 200
                        && fields.getFirst().areaWidth() == 7.5D
                        && fields.getFirst().areaHeight() == 2.0D,
                "a block hit must create the source 7.5 by 2 black-fire field for 200 ticks");
        helper.succeed();
    }

    private static void tick(Player player, int ticks) {
        EyesOfTheTempestItem gun = (EyesOfTheTempestItem) player.getMainHandItem().getItem();
        for (int i = 0; i < ticks; i++) {
            gun.inventoryTick(player.getMainHandItem(), player.level(), player,
                    player.getInventory().selected, true);
        }
    }

    private static List<AberratorBeamEntity> beams(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntitiesOfClass(AberratorBeamEntity.class,
                new AABB(origin).inflate(260.0D));
    }
}
