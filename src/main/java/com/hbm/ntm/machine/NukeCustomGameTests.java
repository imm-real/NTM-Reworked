package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.NukeCustomBlock;
import com.hbm.ntm.blockentity.NukeCustomBlockEntity;
import com.hbm.ntm.explosion.DetonationResult;
import com.hbm.ntm.explosion.RemoteDetonation;
import com.hbm.ntm.nuclear.CustomNukeExplosion;
import com.hbm.ntm.nuclear.FleijaCloudEntity;
import com.hbm.ntm.nuclear.FleijaExplosionEntity;
import com.hbm.ntm.nuclear.FleijaRainbowCloudEntity;
import com.hbm.ntm.nuclear.MushroomCloudEntity;
import com.hbm.ntm.nuclear.NuclearExplosionEntity;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NukeCustomGameTests {
    private NukeCustomGameTests() { }

    private static final float EPSILON = 0.0001F;

    // === Yield computation (pure function of the 27 slots) ===

    @GameTest(template = "empty")
    public static void fissileInputsSumIntoNuclearYield(GameTestHelper helper) {
        NonNullList<ItemStack> slots = empty();
        slots.set(0, new ItemStack(item("ingot_u235"), 4)); // 15 * 4 = 60
        CustomNukeExplosion.Yields y = CustomNukeExplosion.computeYields(slots);
        // TNT stays 0, so the tnt<16 gate zeroes the nuclear stage.
        check(helper, y.nuke() == 0F, "Nuclear must be gated to 0 while TNT < 16");

        slots.set(1, new ItemStack(ModItems.CUSTOM_TNT.get(), 2)); // TNT 10 * 2 = 20
        y = CustomNukeExplosion.computeYields(slots);
        check(helper, approx(y.tnt(), 20F), "Two custom TNT charges must give TNT 20");
        check(helper, approx(y.nuke(), 60F), "Four U235 ingots past the TNT gate must give Nuclear 60");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void multiplierScalesLinearlyWithStackSize(GameTestHelper helper) {
        NonNullList<ItemStack> slots = empty();
        slots.set(0, new ItemStack(Items.GUNPOWDER, 1));   // TNT 0.8 base
        slots.set(1, new ItemStack(Items.REDSTONE, 2));    // MULT 1.05, count 2 -> *2.1 (linear quirk)
        CustomNukeExplosion.Yields y = CustomNukeExplosion.computeYields(slots);
        check(helper, approx(y.tnt(), 0.8F * 2.1F),
                "Two redstone in one slot must scale the multiplier linearly to *2.1, not *1.05");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gatingThresholdsRunInOrder(GameTestHelper helper) {
        // nuke < 100 zeroes hydro even with lithium present.
        NonNullList<ItemStack> slots = empty();
        slots.set(0, new ItemStack(ModItems.CUSTOM_TNT.get(), 2)); // TNT 20
        slots.set(1, new ItemStack(item("ingot_u235"), 4));        // Nuclear 60
        slots.set(2, new ItemStack(item("lithium"), 1));           // Hydrogen 20
        CustomNukeExplosion.Yields y = CustomNukeExplosion.computeYields(slots);
        check(helper, approx(y.nuke(), 60F) && y.hydro() == 0F,
                "Hydrogen must be gated to 0 while Nuclear < 100");

        // nuke < 50 zeroes schrabidium.
        slots.set(1, new ItemStack(item("ingot_u235"), 2)); // Nuclear 30 (< 50)
        slots.set(2, new ItemStack(ModItems.CUSTOM_SCHRAB.get(), 1));
        y = CustomNukeExplosion.computeYields(slots);
        check(helper, y.schrab() == 0F, "Schrabidium must be gated to 0 while Nuclear < 50");

        // No AMAT/EUPH inputs exist in the target -> those stages are always 0 (unreachable).
        slots.set(1, new ItemStack(item("ingot_u235"), 8)); // Nuclear 120 (past every gate)
        y = CustomNukeExplosion.computeYields(slots);
        check(helper, y.amat() == 0F && y.euph() == 0F,
                "Antimatter and Anti-Mass stages must be unreachable with no registered inputs");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void displayAdjustersMatchBranchMath(GameTestHelper helper) {
        NonNullList<ItemStack> slots = empty();
        slots.set(0, new ItemStack(ModItems.CUSTOM_TNT.get(), 2)); // TNT 20
        slots.set(1, new ItemStack(item("ingot_u235"), 4));        // Nuclear 60
        CustomNukeExplosion.Yields y = CustomNukeExplosion.computeYields(slots);
        // nukeAdj = min(nuke + tnt/2, 200) = min(60 + 10, 200) = 70.
        check(helper, approx(y.nukeAdj(), 70F), "nukeAdj must add half the TNT level and cap at 200");
        // schrabAdj is 0 while schrab is 0.
        check(helper, y.schrabAdj() == 0F, "schrabAdj must be 0 when the schrabidium stage is empty");

        // Cap check: pile in schrabidium so the raw stage exceeds its cap.
        NonNullList<ItemStack> capped = empty();
        capped.set(0, new ItemStack(ModItems.CUSTOM_TNT.get(), 2));      // TNT 20
        capped.set(1, new ItemStack(item("ingot_u235"), 8));            // Nuclear 120
        for (int s = 2; s < 27; s++) capped.set(s, new ItemStack(item("ingot_schrabidium"), 64));
        CustomNukeExplosion.Yields cy = CustomNukeExplosion.computeYields(capped);
        check(helper, cy.schrabAdj() == CustomNukeExplosion.MAX_SCHRAB,
                "schrabAdj must clamp to the 250 cap");
        helper.succeed();
    }

    // === Dispatch: one branch fires, coordinate quirks included ===

    @GameTest(template = "empty")
    public static void nuclearBranchCentersExplosionAtDoubleOffsetYPlusFive(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        BlockPos abs = helper.absolutePos(pos);
        NukeCustomBlockEntity bomb = bomb(helper, pos);
        bomb.setItem(0, new ItemStack(ModItems.CUSTOM_TNT.get(), 2)); // TNT 20
        bomb.setItem(1, new ItemStack(item("ingot_u235"), 4));        // Nuclear 60 -> +tnt/2 = 70

        check(helper, ModBlocks.NUKE_CUSTOM.get().detonate(helper.getLevel(), abs), "Nuke must detonate");
        check(helper, helper.getBlockState(pos).isAir(), "Detonation must remove the block");

        List<NuclearExplosionEntity> blasts = nuclear(helper, pos);
        List<MushroomCloudEntity> clouds = mushroom(helper, pos);
        check(helper, blasts.size() == 1 && clouds.size() == 1,
                "The nuclear branch must spawn one MK5 blast and one mushroom cloud");
        check(helper, fleija(helper, pos).isEmpty(), "The nuclear branch must not spawn a FLEIJA blast");

        NuclearExplosionEntity blast = blasts.getFirst();
        // block +0.5 (explode) then +0.5 (branch) = double offset; nuclear MK5 sits at Y + 5.
        check(helper, near(blast.getX(), abs.getX() + 1.0D) && near(blast.getY(), abs.getY() + 5.5D)
                        && near(blast.getZ(), abs.getZ() + 1.0D),
                "The nuclear blast must sit at the double-offset X/Z and Y + 5 center");
        // radius (int)nuke == 70 -> strength 140, matching statFac.
        check(helper, blast.strength() == 140, "Radius 70 must map to MK5 strength 140");
        check(helper, near(clouds.getFirst().getY(), abs.getY() + 5.5D), "The mushroom cloud must sit at Y + 5");
        discardBlast(helper, pos);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void largeTntBranchCentersBlastAtYPlusHalf(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        BlockPos abs = helper.absolutePos(pos);
        NukeCustomBlockEntity bomb = bomb(helper, pos);
        bomb.setItem(0, new ItemStack(ModItems.CUSTOM_TNT.get(), 8)); // TNT 80 (>= 75), no fissile

        ModBlocks.NUKE_CUSTOM.get().detonate(helper.getLevel(), abs);
        List<NuclearExplosionEntity> blasts = nuclear(helper, pos);
        check(helper, blasts.size() == 1 && mushroom(helper, pos).size() == 1,
                "TNT >= 75 must spawn a no-rad MK5 blast plus a mushroom cloud");
        NuclearExplosionEntity blast = blasts.getFirst();
        // Unlike the nuclear branch, the large-TNT MK5 blast is centered at Y + 0.5, not Y + 5.
        check(helper, near(blast.getY(), abs.getY() + 1.0D),
                "The large-TNT blast must sit at Y + 0.5 (double offset), distinct from the nuclear Y + 5");
        check(helper, blast.strength() == 160, "Radius 80 must map to MK5 strength 160");
        discardBlast(helper, pos);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void schrabidiumBranchSpawnsNonRainbowFleija(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        BlockPos abs = helper.absolutePos(pos);
        NukeCustomBlockEntity bomb = bomb(helper, pos);
        bomb.setItem(0, new ItemStack(ModItems.CUSTOM_TNT.get(), 2)); // TNT 20
        bomb.setItem(1, new ItemStack(item("ingot_u235"), 4));        // Nuclear 60 (>= 50)
        bomb.setItem(2, new ItemStack(ModItems.CUSTOM_SCHRAB.get(), 1)); // Schrabidium 15

        ModBlocks.NUKE_CUSTOM.get().detonate(helper.getLevel(), abs);
        List<FleijaExplosionEntity> blasts = fleija(helper, pos);
        check(helper, blasts.size() == 1, "The schrabidium branch must spawn one FLEIJA blast");
        check(helper, fleijaCloud(helper, pos).size() == 1 && rainbow(helper, pos).isEmpty(),
                "The schrabidium branch must spawn the non-rainbow FLEIJA cloud, not the rainbow one");
        check(helper, nuclear(helper, pos).isEmpty(), "The schrabidium branch must not spawn an MK5 blast");
        FleijaExplosionEntity blast = blasts.getFirst();
        check(helper, near(blast.getX(), abs.getX() + 1.0D) && near(blast.getY(), abs.getY() + 1.0D)
                        && near(blast.getZ(), abs.getZ() + 1.0D),
                "The FLEIJA blast must sit at the double-offset centre");
        // schrab 15 + nuke/8 (7.5) + tnt/16 (1.25) = 23.75 -> (int) 23.
        check(helper, blast.radius() == 23, "The schrabidium blast radius must fold in the bonus math");
        discardBlast(helper, pos);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void smallTntBranchSpawnsNoNuclearSystems(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(3, 3, 3));
        // A tiny conventional charge fires a vanilla explosion and no HBM blast entity.
        CustomNukeExplosion.explodeCustom(level, abs.getX(), abs.getY(), abs.getZ(),
                0.8F, 0F, 0F, 0F, 0F, 0F, 0F);
        check(helper, nuclear(helper, new BlockPos(3, 3, 3)).isEmpty()
                        && mushroom(helper, new BlockPos(3, 3, 3)).isEmpty()
                        && fleija(helper, new BlockPos(3, 3, 3)).isEmpty(),
                "The small-TNT path must be a plain vanilla explosion with no MK5/FLEIJA systems");
        helper.succeed();
    }

    // === Device behaviour ===

    @GameTest(template = "empty")
    public static void redstonePowerDetonatesInPlace(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        NukeCustomBlockEntity bomb = bomb(helper, pos);
        bomb.setItem(0, new ItemStack(ModItems.CUSTOM_TNT.get(), 2)); // TNT 20
        bomb.setItem(1, new ItemStack(item("ingot_u235"), 4));        // Nuclear 60

        helper.setBlock(pos.above(), Blocks.REDSTONE_BLOCK);
        check(helper, helper.getBlockState(pos).isAir(), "Redstone power must detonate and remove the block");
        check(helper, nuclear(helper, pos).size() == 1, "Redstone detonation must fire the nuclear branch once");
        discardBlast(helper, pos);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void alwaysDetonatesInPlaceBecauseFallingIsWithheld(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        NukeCustomBlockEntity bomb = bomb(helper, pos);
        // custom_fall is not registered, so the device never enters falling mode.
        check(helper, !bomb.isFalling(), "The device must stay planted while custom_fall is unavailable");

        BlockPos abs = helper.absolutePos(pos);
        RemoteDetonation.Attempt attempt = RemoteDetonation.trigger(helper.getLevel(), abs);
        check(helper, attempt.compatible() && attempt.result() == DetonationResult.DETONATED,
                "An empty custom nuke must still report DETONATED to a remote detonator");
        check(helper, helper.getBlockState(pos).isAir(), "Remote detonation must remove the block");
        // An empty device fires no branch.
        check(helper, nuclear(helper, pos).isEmpty() && fleija(helper, pos).isEmpty(),
                "An empty custom nuke must remove its block but fire no explosion branch");
        discardBlast(helper, pos);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inventoryRoundTripsWithCustomNameAndStackLimit(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        NukeCustomBlockEntity bomb = bomb(helper, pos);
        bomb.setItem(0, new ItemStack(item("ingot_u235"), 64));
        bomb.setItem(26, new ItemStack(ModItems.CUSTOM_SCHRAB.get(), 1));
        bomb.setCustomName(net.minecraft.network.chat.Component.literal("Doomsday"));
        check(helper, bomb.getItem(0).getCount() == 64, "Custom nuke slots must accept the stack limit of 64");

        var tag = bomb.saveWithoutMetadata(helper.getLevel().registryAccess());
        NukeCustomBlockEntity loaded = new NukeCustomBlockEntity(helper.absolutePos(pos), helper.getBlockState(pos));
        loaded.loadWithComponents(tag, helper.getLevel().registryAccess());
        check(helper, loaded.getItem(0).is(item("ingot_u235")) && loaded.getItem(0).getCount() == 64
                        && loaded.getItem(26).is(ModItems.CUSTOM_SCHRAB.get())
                        && loaded.getDisplayName().getString().equals("Doomsday"),
                "The 27-slot inventory and custom name must survive save/load");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingScattersEveryStoredStack(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        NukeCustomBlockEntity bomb = bomb(helper, pos);
        bomb.setItem(0, new ItemStack(item("ingot_u235"), 5));
        bomb.setItem(13, new ItemStack(ModItems.CUSTOM_NUKE.get(), 1));
        helper.setBlock(pos, Blocks.AIR);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(2.0D));
        int u235 = countOf(drops, item("ingot_u235"));
        int rods = countOf(drops, ModItems.CUSTOM_NUKE.get());
        check(helper, u235 == 5 && rods == 1,
                "Breaking a loaded custom nuke must scatter all stored items (5 U235 + 1 rod)");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void containerStaysHopperProof(GameTestHelper helper) {
        BlockPos pos = placeBomb(helper);
        NukeCustomBlockEntity bomb = bomb(helper, pos);
        check(helper, bomb.getSlotsForFace(Direction.UP).length == 0
                        && bomb.getSlotsForFace(Direction.DOWN).length == 0,
                "No slots may be accessible from any side (hopper-proof)");
        check(helper, !bomb.canPlaceItemThroughFace(0, new ItemStack(item("ingot_u235")), Direction.UP),
                "Automation insertion must be rejected");
        helper.succeed();
    }

    // === helpers ===

    private static BlockPos placeBomb(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.NUKE_CUSTOM.get().defaultBlockState()
                .setValue(NukeCustomBlock.FACING, Direction.SOUTH));
        return pos;
    }

    private static NukeCustomBlockEntity bomb(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof NukeCustomBlockEntity bomb) return bomb;
        helper.fail("Expected custom nuke block entity");
        throw new IllegalStateException();
    }

    private static NonNullList<ItemStack> empty() {
        return NonNullList.withSize(NukeCustomBlockEntity.SLOTS, ItemStack.EMPTY);
    }

    private static Item item(String id) {
        return ModItems.get(id).get();
    }

    private static List<NuclearExplosionEntity> nuclear(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(NuclearExplosionEntity.class, region(helper, pos));
    }

    private static List<MushroomCloudEntity> mushroom(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(MushroomCloudEntity.class, region(helper, pos));
    }

    private static List<FleijaExplosionEntity> fleija(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(FleijaExplosionEntity.class, region(helper, pos));
    }

    private static List<FleijaCloudEntity> fleijaCloud(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(FleijaCloudEntity.class, region(helper, pos));
    }

    private static List<FleijaRainbowCloudEntity> rainbow(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(FleijaRainbowCloudEntity.class, region(helper, pos));
    }

    private static AABB region(GameTestHelper helper, BlockPos pos) {
        return new AABB(helper.absolutePos(pos)).inflate(8.0D);
    }

    private static void discardBlast(GameTestHelper helper, BlockPos pos) {
        nuclear(helper, pos).forEach(Entity::discard);
        mushroom(helper, pos).forEach(Entity::discard);
        fleija(helper, pos).forEach(Entity::discard);
        fleijaCloud(helper, pos).forEach(Entity::discard);
        rainbow(helper, pos).forEach(Entity::discard);
    }

    private static int countOf(List<ItemEntity> drops, Item item) {
        return drops.stream().filter(entity -> entity.getItem().is(item))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static boolean approx(float actual, float expected) {
        return Math.abs(actual - expected) < EPSILON;
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-6D;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
