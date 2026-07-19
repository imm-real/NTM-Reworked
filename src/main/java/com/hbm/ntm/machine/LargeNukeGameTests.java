package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.LargeNukeBlock;
import com.hbm.ntm.block.LargeNukeType;
import com.hbm.ntm.blockentity.LargeNukeBlockEntity;
import com.hbm.ntm.hazard.HazardCarrier;
import com.hbm.ntm.nuclear.MushroomCloudEntity;
import com.hbm.ntm.nuclear.NuclearExplosionEntity;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LargeNukeGameTests {
    private LargeNukeGameTests() { }

    @GameTest(template = "empty")
    public static void readinessRequiresEachBombsExactComponents(GameTestHelper helper) {
        for (LargeNukeType type : LargeNukeType.values()) {
            BlockPos pos = new BlockPos(2 + type.ordinal() * 2, 2, 3);
            LargeNukeBlockEntity bomb = place(helper, pos, type);
            check(helper, !bomb.isReady(), type + " must not be ready while empty");
            fill(bomb, type, false);
            check(helper, bomb.isReady(), type + " must accept its exact source component set");
            bomb.setItem(0, new ItemStack(Blocks.DIRT));
            check(helper, !bomb.isReady(), type + " must reject a wrong component");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stagedThermonuclearRadiiPreserveSourceQuirks(GameTestHelper helper) {
        LargeNukeBlockEntity gadget = place(helper, new BlockPos(2, 2, 2), LargeNukeType.GADGET);
        fill(gadget, LargeNukeType.GADGET, true);
        check(helper, gadget.detonationRadius() == 150, "The Gadget default radius must be 150");

        LargeNukeBlockEntity boy = place(helper, new BlockPos(4, 2, 2), LargeNukeType.BOY);
        fill(boy, LargeNukeType.BOY, true);
        check(helper, boy.detonationRadius() == 120, "Little Boy default radius must be 120");

        LargeNukeBlockEntity mike = place(helper, new BlockPos(6, 2, 2), LargeNukeType.MIKE);
        fill(mike, LargeNukeType.MIKE, false);
        check(helper, mike.isReady() && !mike.isFilled() && mike.detonationRadius() == 250,
                "Source Ivy Mike bug must give its incomplete ready stage the full 250 radius");
        fill(mike, LargeNukeType.MIKE, true);
        check(helper, mike.isFilled() && mike.detonationRadius() == 250,
                "Completed Ivy Mike radius must be 250");

        LargeNukeBlockEntity tsar = place(helper, new BlockPos(8, 2, 2), LargeNukeType.TSAR);
        fill(tsar, LargeNukeType.TSAR, false);
        check(helper, tsar.isReady() && !tsar.isFilled() && tsar.detonationRadius() == 175,
                "Tsar Bomba without its optional core must fall back to Fat Man radius 175");
        fill(tsar, LargeNukeType.TSAR, true);
        check(helper, tsar.isFilled() && tsar.detonationRadius() == 500,
                "Completed Tsar Bomba radius must be 500");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inventoriesClampPersistAndDropComponents(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        LargeNukeBlockEntity bomb = place(helper, pos, LargeNukeType.BOY);
        bomb.setItem(0, new ItemStack(ModItems.BOY_SHIELDING.get(), 80));
        bomb.setItem(1, new ItemStack(ModItems.BOY_TARGET.get()));
        check(helper, bomb.getItem(0).getCount() == 64,
                "Little Boy must preserve its anomalous source component stack limit of 64");

        CompoundTag saved = bomb.saveWithoutMetadata(helper.getLevel().registryAccess());
        LargeNukeBlockEntity loaded = new LargeNukeBlockEntity(helper.absolutePos(pos), helper.getBlockState(pos));
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(helper, loaded.getItem(0).is(ModItems.BOY_SHIELDING.get())
                        && loaded.getItem(1).is(ModItems.BOY_TARGET.get()),
                "Large nuke components must persist through save/load");

        helper.setBlock(pos, Blocks.AIR);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(2.0D));
        check(helper, drops.stream().anyMatch(item -> item.getItem().is(ModItems.BOY_SHIELDING.get()))
                        && drops.stream().anyMatch(item -> item.getItem().is(ModItems.BOY_TARGET.get())),
                "Breaking an undetonated large nuke must recover stored components");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void detonationConsumesBombAndSpawnsExactRadiusSystems(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        LargeNukeBlockEntity bomb = place(helper, pos, LargeNukeType.TSAR);
        fill(bomb, LargeNukeType.TSAR, true);
        LargeNukeBlock block = (LargeNukeBlock) helper.getBlockState(pos).getBlock();
        check(helper, block.detonate(helper.getLevel(), helper.absolutePos(pos)),
                "Completed Tsar Bomba must detonate");
        check(helper, helper.getBlockState(pos).isAir(), "Detonation must remove the bomb block");

        List<NuclearExplosionEntity> explosions = helper.getLevel().getEntitiesOfClass(NuclearExplosionEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(3.0D));
        List<MushroomCloudEntity> clouds = helper.getLevel().getEntitiesOfClass(MushroomCloudEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(3.0D));
        check(helper, explosions.size() == 1 && explosions.getFirst().strength() == 1000
                        && explosions.getFirst().length() == 500 && explosions.getFirst().speed() == 100,
                "Tsar Bomba must create MK5 strength 1000, length 500 and speed 100");
        check(helper, clouds.size() == 1 && clouds.getFirst().cloudScale() > 3.0F,
                "Tsar Bomba must create a radius-scaled Torex cloud");
        explosions.forEach(NuclearExplosionEntity::discard);
        clouds.forEach(MushroomCloudEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void littleBoyUsesFixedSourceCloudScaleAndPartHazards(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        LargeNukeBlockEntity bomb = place(helper, pos, LargeNukeType.BOY);
        fill(bomb, LargeNukeType.BOY, true);
        LargeNukeBlock block = (LargeNukeBlock) helper.getBlockState(pos).getBlock();
        check(helper, block.detonate(helper.getLevel(), helper.absolutePos(pos)), "Little Boy must detonate");
        List<MushroomCloudEntity> clouds = helper.getLevel().getEntitiesOfClass(MushroomCloudEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(3.0D));
        check(helper, clouds.size() == 1 && clouds.getFirst().cloudScale() == 1.5F
                        && clouds.getFirst().maxAge() == 1350
                        && clouds.getFirst().getY() == helper.absolutePos(pos).getY() + 1.0D,
                "Little Boy must preserve the source y+1, fixed 1.5-scale, 1350-tick cloud");
        checkHazard(helper, ModItems.GADGET_CORE.get(), 5.0F, 0.0F, "Gadget core");
        checkHazard(helper, ModItems.BOY_TARGET.get(), 2.0F, 0.0F, "Little Boy target");
        checkHazard(helper, ModItems.BOY_BULLET.get(), 1.0F, 0.0F, "Little Boy projectile");
        checkHazard(helper, ModItems.BOY_PROPELLANT.get(), 0.0F, 2.0F, "Little Boy propellant");
        checkHazard(helper, ModItems.MIKE_CORE.get(), 0.25F, 0.0F, "Ivy Mike core");
        checkHazard(helper, ModItems.TSAR_CORE.get(), 7.5F, 0.0F, "Tsar core");
        helper.getLevel().getEntitiesOfClass(NuclearExplosionEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(3.0D)).forEach(NuclearExplosionEntity::discard);
        clouds.forEach(MushroomCloudEntity::discard);
        helper.succeed();
    }

    private static LargeNukeBlockEntity place(GameTestHelper helper, BlockPos pos, LargeNukeType type) {
        Block block = switch (type) {
            case GADGET -> ModBlocks.NUKE_GADGET.get();
            case BOY -> ModBlocks.NUKE_BOY.get();
            case MIKE -> ModBlocks.NUKE_MIKE.get();
            case TSAR -> ModBlocks.NUKE_TSAR.get();
        };
        helper.setBlock(pos, block.defaultBlockState().setValue(LargeNukeBlock.FACING,
                net.minecraft.core.Direction.SOUTH));
        if (helper.getBlockEntity(pos) instanceof LargeNukeBlockEntity bomb) return bomb;
        helper.fail("Expected " + type + " block entity");
        throw new IllegalStateException();
    }

    private static void fill(LargeNukeBlockEntity bomb, LargeNukeType type, boolean full) {
        bomb.clearContent();
        switch (type) {
            case GADGET -> {
                bomb.setItem(0, new ItemStack(ModItems.GADGET_WIREING.get()));
                for (int slot = 1; slot <= 4; slot++) bomb.setItem(slot, new ItemStack(ModItems.EARLY_EXPLOSIVE_LENSES.get()));
                bomb.setItem(5, new ItemStack(ModItems.GADGET_CORE.get()));
            }
            case BOY -> {
                bomb.setItem(0, new ItemStack(ModItems.BOY_SHIELDING.get()));
                bomb.setItem(1, new ItemStack(ModItems.BOY_TARGET.get()));
                bomb.setItem(2, new ItemStack(ModItems.BOY_BULLET.get()));
                bomb.setItem(3, new ItemStack(ModItems.BOY_PROPELLANT.get()));
                bomb.setItem(4, new ItemStack(ModItems.BOY_IGNITER.get()));
            }
            case MIKE -> {
                fillModernPrimary(bomb);
                if (full) {
                    bomb.setItem(5, new ItemStack(ModItems.MIKE_CORE.get()));
                    bomb.setItem(6, new ItemStack(ModItems.MIKE_DEUT.get()));
                    bomb.setItem(7, new ItemStack(ModItems.MIKE_COOLING_UNIT.get()));
                }
            }
            case TSAR -> {
                fillModernPrimary(bomb);
                if (full) bomb.setItem(5, new ItemStack(ModItems.TSAR_CORE.get()));
            }
        }
    }

    private static void fillModernPrimary(LargeNukeBlockEntity bomb) {
        for (int slot = 0; slot < 4; slot++) bomb.setItem(slot, new ItemStack(ModItems.EXPLOSIVE_LENSES.get()));
        bomb.setItem(4, new ItemStack(ModItems.MAN_CORE.get()));
    }

    private static void checkHazard(GameTestHelper helper, net.minecraft.world.item.Item item,
                                    float radiation, float explosive, String name) {
        check(helper, item instanceof HazardCarrier carrier
                        && carrier.hbm$getHazards(new ItemStack(item)).radiation() == radiation
                        && carrier.hbm$getHazards(new ItemStack(item)).explosive() == explosive,
                name + " hazards must match source values");
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
