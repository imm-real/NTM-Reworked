package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.ChargeBlock;
import com.hbm.ntm.blockentity.ChargeBlockEntity;
import com.hbm.ntm.entity.PrimedExplosiveEntity;
import com.hbm.ntm.explosion.ChargeExplosion;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DemolitionChargeGameTests {
    private DemolitionChargeGameTests() {
    }

    @GameTest(template = "empty")
    public static void chargesMountOnAllSixFacesWithNoCollision(GameTestHelper helper) {
        BlockPos center = new BlockPos(4, 3, 4);
        for (Direction facing : Direction.values()) {
            BlockPos chargePos = center.relative(facing, 2);
            BlockPos support = chargePos.relative(facing.getOpposite());
            helper.setBlock(support, Blocks.STONE);
            BlockState state = ModBlocks.CHARGE_DYNAMITE.get().defaultBlockState()
                    .setValue(ChargeBlock.FACING, facing);
            helper.setBlock(chargePos, state);
            check(helper, state.canSurvive(helper.getLevel(), helper.absolutePos(chargePos)),
                    "Charge must survive on the clicked solid face: " + facing);
            check(helper, state.getCollisionShape(helper.getLevel(), helper.absolutePos(chargePos)).isEmpty(),
                    "Charges must preserve the original null collision box");
            double thickness = switch (facing.getAxis()) {
                case X -> state.getShape(helper.getLevel(), helper.absolutePos(chargePos)).bounds().getXsize();
                case Y -> state.getShape(helper.getLevel(), helper.absolutePos(chargePos)).bounds().getYsize();
                case Z -> state.getShape(helper.getLevel(), helper.absolutePos(chargePos)).bounds().getZsize();
            };
            check(helper, Math.abs(thickness - 0.375D) < 0.000001D,
                    "Every mounted charge must be exactly 6/16 of a block thick");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void timerCycleArmAndPausePreserveOriginalSequence(GameTestHelper helper) {
        BlockPos position = placeCharge(helper, ModBlocks.CHARGE_DYNAMITE.get().defaultBlockState());
        ChargeBlockEntity charge = charge(helper, position);
        int[] expected = {100, 200, 300, 600, 1_200, 3_600, 6_000, 0};
        for (int value : expected) {
            charge.cycleTimer();
            check(helper, charge.timer() == value, "Charge timer cycle diverged at " + value + " ticks");
        }
        check(helper, !charge.arm(), "A zero timer must not arm");
        charge.cycleTimer();
        check(helper, charge.arm() && charge.started(), "A positive timer must arm on sneak-click");
        check(helper, charge.pause() && !charge.started() && charge.timer() == 100,
                "First defuser action must pause without resetting the remaining timer");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chargeStateIntentionallyResetsAcrossReload(GameTestHelper helper) {
        BlockPos position = placeCharge(helper, ModBlocks.CHARGE_DYNAMITE.get().defaultBlockState());
        ChargeBlockEntity charge = charge(helper, position);
        charge.cycleTimer();
        charge.arm();
        CompoundTag saved = charge.saveWithoutMetadata(helper.getLevel().registryAccess());
        ChargeBlockEntity loaded = new ChargeBlockEntity(helper.absolutePos(position), helper.getBlockState(position));
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(helper, loaded.timer() == 0 && !loaded.started(),
                "The original charge does not persist its timer or armed state across reloads");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void defuserPausesThenSafelyDropsCharge(GameTestHelper helper) {
        BlockPos position = placeCharge(helper, ModBlocks.CHARGE_DYNAMITE.get().defaultBlockState());
        ChargeBlockEntity charge = charge(helper, position);
        charge.cycleTimer();
        charge.arm();
        ChargeBlock block = ModBlocks.CHARGE_DYNAMITE.get();
        var player = helper.makeMockPlayer(GameType.SURVIVAL);

        check(helper, block.useDefuser(helper.getLevel(), helper.absolutePos(position), player),
                "Defuser must interact with an armed charge");
        check(helper, helper.getBlockState(position).is(block) && !charge.started(),
                "First defuser use must only pause an armed charge");
        check(helper, block.useDefuser(helper.getLevel(), helper.absolutePos(position), player),
                "Second defuser use must dismantle the stopped charge");
        check(helper, helper.getBlockState(position).isAir(), "Safely dismantled charge must be removed");
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(position)).inflate(2.0D));
        check(helper, drops.stream().anyMatch(item -> item.getItem().is(ModItems.CHARGE_DYNAMITE_ITEM.get())),
                "Safe dismantling must drop one plain charge item");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void explosionChainingUsesFuseZeroChargeIdentity(GameTestHelper helper) {
        BlockPos position = placeCharge(helper, ModBlocks.CHARGE_C4.get().defaultBlockState());
        BlockPos absolute = helper.absolutePos(position);
        Explosion source = new Explosion(helper.getLevel(), null,
                absolute.getX() + 0.5D, absolute.getY() + 0.5D, absolute.getZ() + 0.5D,
                4.0F, false, Explosion.BlockInteraction.DESTROY);
        ModBlocks.CHARGE_C4.get().wasExploded(helper.getLevel(), absolute, source);
        List<PrimedExplosiveEntity> entities = helper.getLevel().getEntitiesOfClass(PrimedExplosiveEntity.class,
                new AABB(absolute).inflate(2.0D));
        check(helper, entities.size() == 1, "Explosion chaining must spawn exactly one fuse-zero entity callback");
        PrimedExplosiveEntity primed = entities.getFirst();
        check(helper, primed.getFuse() == 0 && primed.getBlockState().is(ModBlocks.CHARGE_C4.get()),
                "Chain entity must preserve fuse zero and Demolition Charge identity");
        primed.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void minerDropsBlocksWithoutHurtingEntities(GameTestHelper helper) {
        BlockPos blast = new BlockPos(4, 3, 4);
        helper.setBlock(blast.east(), Blocks.STONE);
        Zombie zombie = zombie(helper, blast.offset(2, 0, 0));
        float health = zombie.getHealth();
        ChargeExplosion.detonate(helper.getLevel(), helper.absolutePos(blast),
                ModBlocks.CHARGE_MINER.get().chargeType());
        check(helper, zombie.getHealth() == health, "Timed Mining Charge must not damage entities");
        check(helper, helper.getBlockState(blast.east()).isAir(), "Timed Mining Charge must destroy nearby stone");
        check(helper, hasDrop(helper, blast, Items.COBBLESTONE), "Timed Mining Charge must drop all destroyed stone");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "demolition_isolated")
    public static void semtexMinesWithoutDamageAndC4DamagesWithoutDrops(GameTestHelper helper) {
        BlockPos semtex = new BlockPos(4, 3, 4);
        helper.setBlock(semtex.east(), Blocks.DIAMOND_ORE);
        Zombie safeZombie = zombie(helper, semtex.offset(2, 0, 0));
        float health = safeZombie.getHealth();
        ChargeExplosion.detonate(helper.getLevel(), helper.absolutePos(semtex),
                ModBlocks.CHARGE_SEMTEX.get().chargeType());
        check(helper, safeZombie.getHealth() == health, "Semtex Mining Charge must not damage entities");
        check(helper, hasDrop(helper, semtex, Items.DIAMOND), "Semtex Mining Charge must apply mining drops");

        helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(semtex)).inflate(20.0D)).forEach(ItemEntity::discard);
        BlockPos c4 = new BlockPos(12, 3, 4);
        helper.setBlock(c4.east(), Blocks.DIAMOND_BLOCK);
        Zombie target = zombie(helper, c4.offset(2, 0, 0));
        ChargeExplosion.detonate(helper.getLevel(), helper.absolutePos(c4),
                ModBlocks.CHARGE_C4.get().chargeType());
        check(helper, !target.isAlive() || target.getHealth() < target.getMaxHealth(),
                "Demolition Charge must damage nearby entities");
        check(helper, !hasDrop(helper, c4, Items.DIAMOND_BLOCK),
                "Demolition Charge must not drop destroyed blocks");
        helper.succeed();
    }

    private static BlockPos placeCharge(GameTestHelper helper, BlockState state) {
        BlockPos support = new BlockPos(3, 1, 3);
        BlockPos position = support.above();
        helper.setBlock(support, Blocks.STONE);
        helper.setBlock(position, state.setValue(ChargeBlock.FACING, Direction.UP));
        return position;
    }

    private static ChargeBlockEntity charge(GameTestHelper helper, BlockPos position) {
        if (!(helper.getBlockEntity(position) instanceof ChargeBlockEntity charge)) {
            helper.fail("Expected charge block entity");
            throw new IllegalStateException();
        }
        return charge;
    }

    private static Zombie zombie(GameTestHelper helper, BlockPos relative) {
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        if (zombie == null) {
            helper.fail("Could not create zombie test entity");
            throw new IllegalStateException();
        }
        BlockPos absolute = helper.absolutePos(relative);
        zombie.setPos(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        zombie.setNoAi(true);
        helper.getLevel().addFreshEntity(zombie);
        return zombie;
    }

    private static boolean hasDrop(GameTestHelper helper, BlockPos center, net.minecraft.world.item.Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        new AABB(helper.absolutePos(center)).inflate(20.0D))
                .stream().anyMatch(entity -> entity.getItem().is(item));
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
