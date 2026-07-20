package com.hbm.ntm.weapon;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.SellafieldBlock;
import com.hbm.ntm.entity.FlattenedMobEntity;
import com.hbm.ntm.entity.PowerFistBeamEntity;
import com.hbm.ntm.entity.PowerFistRubbleEntity;
import com.hbm.ntm.item.PowerFistItem;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerFistGameTests {
    private PowerFistGameTests() { }

    @GameTest(template = "empty")
    public static void modeCyclePreservesDamageAndRecreatesSourceEnchantments(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack stack = new ItemStack(ModItems.MULTITOOL_DIG.get());
        stack.setDamageValue(137);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setShiftKeyDown(true);

        List<Item> expected = List.of(
                ModItems.MULTITOOL_SILK.get(), ModItems.MULTITOOL_EXT.get(),
                ModItems.MULTITOOL_MINER.get(), ModItems.MULTITOOL_HIT.get(),
                ModItems.MULTITOOL_BEAM.get(), ModItems.MULTITOOL_SKY.get(),
                ModItems.MULTITOOL_MEGA.get(), ModItems.MULTITOOL_JOULE.get(),
                ModItems.MULTITOOL_DECON.get(), ModItems.MULTITOOL_PANE.get(),
                ModItems.MULTITOOL_DIG.get());
        for (Item item : expected) {
            ItemStack current = player.getMainHandItem();
            ((PowerFistItem) current.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            ItemStack next = player.getMainHandItem();
            helper.assertTrue(next.is(item) && next.getDamageValue() == 137,
                    "Every mode transition must replace the item and preserve damage");
        }

        ItemStack dig = player.getMainHandItem();
        helper.assertTrue(enchantment(helper, dig, Enchantments.LOOTING) == 3
                        && enchantment(helper, dig, Enchantments.FORTUNE) == 3,
                "Pane Punch -> Digging Claw must recreate Looting III and Fortune III");
        ((PowerFistItem) dig.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(enchantment(helper, player.getMainHandItem(), Enchantments.SILK_TOUCH) == 3,
                "Digging Claw -> Silk Touch Claw must retain the source-invalid Silk Touch III");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void extractorTurnsOneSmeltableBlockIntoOneFurnaceResult(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack extractor = new ItemStack(ModItems.MULTITOOL_EXT.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, extractor);
        BlockPos ore = helper.absolutePos(new BlockPos(3, 3, 3));
        helper.getLevel().setBlockAndUpdate(ore, Blocks.IRON_ORE.defaultBlockState());

        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ore), Direction.UP, ore, false));
        ((PowerFistItem) extractor.getItem()).useOn(context);

        helper.assertTrue(helper.getLevel().getBlockState(ore).isAir(),
                "The Ore Extractor must replace a smeltable block with air");
        helper.assertTrue(player.getInventory().contains(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT)),
                "The Ore Extractor must insert exactly the furnace result");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void superPunchLevelsExactlyFiveByFiveAndKeepsUnbreakableBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(6, 3, 6));
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlockAndUpdate(center.offset(x, 0, z), Blocks.STONE.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(center, Blocks.BEDROCK.defaultBlockState());
        level.setBlockAndUpdate(center.offset(3, 0, 0), Blocks.STONE.defaultBlockState());

        PowerFistItem.levelDown(level, center, 2);

        helper.assertTrue(level.getBlockState(center).is(Blocks.BEDROCK)
                        && level.getBlockState(center.offset(3, 0, 0)).is(Blocks.STONE),
                "Super Punch must honor resistance 6000 and the source radius-two square");
        AABB rubbleBounds = new AABB(center).inflate(4.0D);
        helper.assertTrue(entities(level, PowerFistRubbleEntity.class, rubbleBounds).size() == 24,
                "Every removable block in the 5x5 layer must become one rubble entity");
        helper.assertTrue(entities(level, PowerFistRubbleEntity.class, rubbleBounds).stream()
                        .allMatch(rubble -> Math.abs(rubble.getDeltaMovement().y - 0.4D) < 1.0E-9D),
                "Every Super Punch fragment must start with the exact source 0.4 upward velocity");
        entities(level, PowerFistRubbleEntity.class, rubbleBounds).forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void airModesSpawnExactBeamsAndFifteenLightningBolts(GameTestHelper helper) {
        Player player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MULTITOOL_MINER.get()));
        ((PowerFistItem) player.getMainHandItem().getItem())
                .use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MULTITOOL_BEAM.get()));
        ((PowerFistItem) player.getMainHandItem().getItem())
                .use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MULTITOOL_SKY.get()));
        ((PowerFistItem) player.getMainHandItem().getItem())
                .use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        AABB effectBounds = player.getBoundingBox().inflate(64.0D);
        List<PowerFistBeamEntity> beams = entities(
                helper.getLevel(), PowerFistBeamEntity.class, effectBounds).stream()
                .filter(beam -> beam.getOwner() == player)
                .toList();
        List<LightningBolt> lightning = entities(
                helper.getLevel(), LightningBolt.class, effectBounds);
        helper.assertTrue(beams.size() == 2,
                "Mining Laser and Zapper must each spawn one tracked source beam");
        helper.assertTrue(beams.stream().filter(beam -> beam.getType() == ModEntities.POWER_FIST_MINER_BEAM.get()).count() == 1
                        && beams.stream().filter(beam -> beam.getType() == ModEntities.POWER_FIST_LASER_BEAM.get()).count() == 1,
                "The two Power Fist shots must be one mining beam and one immolator beam");
        helper.assertTrue(lightning.size() == 15,
                "Crack the Sky must spawn exactly fifteen vanilla lightning bolts");
        beams.forEach(Entity::discard);
        lightning.forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void panePunchFlattensMobsIntoPortraitPanesButSparesPlayers(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack fist = new ItemStack(ModItems.MULTITOOL_PANE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, fist);

        BlockPos relative = new BlockPos(4, 2, 2);
        LivingEntity pig = helper.spawn(EntityType.PIG, relative);
        ((PowerFistItem) fist.getItem()).hurtEnemy(fist, pig, player);

        helper.assertTrue(!pig.isAlive(), "The Pane Punch must remove the punched mob");
        AABB paneBounds = new AABB(helper.absolutePos(relative)).inflate(2.0D);
        List<FlattenedMobEntity> panes = entities(helper.getLevel(), FlattenedMobEntity.class, paneBounds);
        helper.assertTrue(panes.size() == 1,
                "The punched mob must become exactly one flattened pane entity");
        FlattenedMobEntity pane = panes.get(0);
        helper.assertTrue(pane.victimTypeId().equals("minecraft:pig"),
                "The pane must remember which mob it used to be");
        helper.assertTrue(Math.abs(pane.getYRot() - player.getYRot()) < 1.0E-4F,
                "The pane must stand flat broadside to its puncher");
        helper.assertTrue(Math.abs(pane.getBbWidth() - pig.getBbWidth()) < 1.0E-4F
                        && Math.abs(pane.getBbHeight() - pig.getBbHeight()) < 1.0E-4F,
                "The pane must keep the victim's bounding-box footprint");

        Player victim = helper.makeMockPlayer(GameType.SURVIVAL);
        ((PowerFistItem) fist.getItem()).hurtEnemy(fist, victim, player);
        helper.assertTrue(victim.isAlive(), "Players must not be flattened into panes");

        pane.hurt(helper.getLevel().damageSources().generic(), 1.0F);
        helper.assertTrue(!pane.isAlive(), "One hit of any kind must shatter the pane");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void decontaminatorUsesEveryExactSourceTerrainConversion(GameTestHelper helper) {
        helper.assertTrue(convert(ModBlocks.WASTE_EARTH.get().defaultBlockState(), 3, 1)
                        .is(Blocks.GRASS_BLOCK), "Dead Grass must clean into Grass on the source 2/3 roll");
        helper.assertTrue(convert(ModBlocks.WASTE_MYCELIUM.get().defaultBlockState(), 5, 0)
                        .is(Blocks.MYCELIUM), "Glowing Mycelium must clean into Mycelium on the source 1/5 roll");
        helper.assertTrue(convert(ModBlocks.WASTE_TRINITITE.get().defaultBlockState(), 3, 0)
                        .is(Blocks.SAND), "Trinitite Ore must clean into Sand on the source 1/3 roll");
        helper.assertTrue(convert(ModBlocks.WASTE_TRINITITE_RED.get().defaultBlockState(), 3, 0)
                        .is(Blocks.RED_SAND), "Red Trinitite Ore must clean into Red Sand on the source 1/3 roll");
        helper.assertTrue(convert(ModBlocks.WASTE_LOG.get().defaultBlockState(), 3, 1)
                        .is(Blocks.OAK_LOG), "Charred Log must clean into the source default Oak Log");
        helper.assertTrue(convert(ModBlocks.WASTE_PLANKS.get().defaultBlockState(), 3, 1)
                        .is(Blocks.OAK_PLANKS), "Charred Planks must clean into the source default Oak Planks");
        helper.assertTrue(convert(ModBlocks.BLOCK_TRINITITE.get().defaultBlockState(), 10, 0)
                        .is(ModBlocks.get("block_lead").get()), "Trinitite blocks must clean into Lead at 1/10");
        helper.assertTrue(convert(ModBlocks.BLOCK_WASTE.get().defaultBlockState(), 10, 0)
                        .is(ModBlocks.get("block_lead").get()), "Nuclear Waste blocks must clean into Lead at 1/10");

        BlockState unchanged = ModBlocks.WASTE_EARTH.get().defaultBlockState();
        helper.assertTrue(convert(unchanged, 3, 0) == unchanged,
                "A failed source roll must leave the block untouched");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void decontaminatorPreservesTheSourceSellafiteBranchOrder(GameTestHelper helper) {
        BlockState levelFive = ModBlocks.SELLAFIELD.get().defaultBlockState()
                .setValue(SellafieldBlock.LEVEL, 5);
        int[] calls = {0};
        BlockState firstBranch = PowerFistItem.decontaminatedState(levelFive, bound -> {
            helper.assertTrue(bound == 10, "Level-five Sellafite must try its source 1/10 branch first");
            calls[0]++;
            return 0;
        });
        helper.assertTrue(calls[0] == 1 && firstBranch.getValue(SellafieldBlock.LEVEL) == 4,
                "Successful level-five 1/10 cooling must stop before the fallback roll");

        int[] sequence = {1, 0};
        int[] bounds = {10, 5};
        int[] index = {0};
        BlockState fallback = PowerFistItem.decontaminatedState(levelFive, bound -> {
            helper.assertTrue(index[0] < bounds.length && bound == bounds[index[0]],
                    "Failed level-five cooling must then use the independent source 1/5 roll");
            return sequence[index[0]++];
        });
        helper.assertTrue(index[0] == 2 && fallback.getValue(SellafieldBlock.LEVEL) == 4,
                "The independent level-five fallback must cool to level four");

        BlockState levelThree = ModBlocks.SELLAFIELD.get().defaultBlockState()
                .setValue(SellafieldBlock.LEVEL, 3);
        helper.assertTrue(convert(levelThree, 5, 0).getValue(SellafieldBlock.LEVEL) == 2,
                "Sellafite levels one through four must cool by one at 1/5");
        BlockState levelZero = ModBlocks.SELLAFIELD.get().defaultBlockState();
        helper.assertTrue(convert(levelZero, 5, 0).is(ModBlocks.SELLAFIELD_SLAKED.get()),
                "Sellafite level zero must become Slaked Sellafite at 1/5");
        helper.succeed();
    }

    private static BlockState convert(BlockState state, int expectedBound, int result) {
        return PowerFistItem.decontaminatedState(state, bound -> {
            if (bound != expectedBound) throw new AssertionError(
                    "Expected nextInt(" + expectedBound + ") but got nextInt(" + bound + ")");
            return result;
        });
    }

    private static int enchantment(GameTestHelper helper, ItemStack stack,
                                   net.minecraft.resources.ResourceKey<Enchantment> key) {
        Holder<Enchantment> holder = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
    }

    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        player.setYRot(-90.0F);
        player.setXRot(0.0F);
        return player;
    }

    private static <T extends Entity> List<T> entities(ServerLevel level, Class<T> type, AABB bounds) {
        return level.getEntitiesOfClass(type, bounds);
    }
}
