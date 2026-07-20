package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.anvil.AnvilRecipes;
import com.hbm.ntm.blockentity.SirenBlockEntity;
import com.hbm.ntm.item.SirenTrackItem;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SirenGameTests {
    private SirenGameTests() { }

    @GameTest(template = "empty")
    public static void cassetteCatalogKeepsEverySourceTrack(GameTestHelper helper) {
        check(helper, SirenTrackItem.Track.values().length == 21,
                "The blank tape and all twenty source siren tracks must exist");
        for (int metadata = 1; metadata <= 20; metadata++) {
            SirenTrackItem.Track track = SirenTrackItem.Track.byMetadata(metadata);
            ItemStack cassette = SirenTrackItem.create(ModItems.SIREN_TRACK.get(), track);
            check(helper, track != SirenTrackItem.Track.NONE && SirenTrackItem.track(cassette) == track,
                    "Siren cassette metadata " + metadata + " must survive the item conversion");
            AnvilRecipes.Construction recipe = AnvilRecipes.byId(ResourceLocation.fromNamespaceAndPath(
                    HbmNtm.MOD_ID, "anvil/siren_track_" + track.id()));
            check(helper, recipe != null && recipe.validForTier(2) && !recipe.validForTier(1)
                            && recipe.overlay() == AnvilRecipes.Overlay.CONSTRUCTION
                            && recipe.inputs().size() == 2
                            && recipe.inputs().get(0).count() == 1
                            && recipe.inputs().get(1).count() == 1
                            && SirenTrackItem.track(recipe.outputs().getFirst().stack().get()) == track,
                    "Siren cassette " + metadata + " must keep its source Tier-2 construction");
        }
        check(helper, SirenTrackItem.Track.HATCH.range() == 250
                        && SirenTrackItem.Track.SWEEP_SIREN.range() == 500
                        && SirenTrackItem.Track.RAZORTRAIN.type() == SirenTrackItem.SoundType.SOUND,
                "Track type and range must keep the source catalog values");
        check(helper, ModBlocks.MACHINE_SIREN.get().defaultDestroyTime() == 5.0F
                        && ModBlocks.MACHINE_SIREN.get().getExplosionResistance() == 6.0F,
                "The siren must keep source hardness 5 and effective resistance 6");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void loopCassetteFollowsRedstoneLevel(GameTestHelper helper) {
        BlockPos position = new BlockPos(2, 2, 2);
        SirenBlockEntity siren = place(helper, position, SirenTrackItem.Track.HATCH);
        siren.tickForTest(helper.getLevel());
        check(helper, !siren.active(), "An unpowered loop cassette must stay quiet");

        helper.setBlock(position.east(), Blocks.REDSTONE_BLOCK);
        siren.tickForTest(helper.getLevel());
        check(helper, siren.active(), "A powered loop cassette must run continuously");

        helper.setBlock(position.east(), Blocks.AIR);
        siren.tickForTest(helper.getLevel());
        check(helper, !siren.active(), "Removing redstone must stop a loop cassette");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oneShotCassetteNeedsAnotherRisingEdge(GameTestHelper helper) {
        BlockPos position = new BlockPos(2, 2, 2);
        SirenBlockEntity siren = place(helper, position, SirenTrackItem.Track.APC_PASS);
        helper.setBlock(position.east(), Blocks.REDSTONE_BLOCK);

        siren.tickForTest(helper.getLevel());
        check(helper, siren.pulseSerial() == 1 && !siren.active(),
                "PASS cassettes must emit once instead of pretending to loop");
        siren.tickForTest(helper.getLevel());
        check(helper, siren.pulseSerial() == 1,
                "Holding redstone must not repeatedly trigger a one-shot cassette");

        helper.setBlock(position.east(), Blocks.AIR);
        siren.tickForTest(helper.getLevel());
        helper.setBlock(position.east(), Blocks.REDSTONE_BLOCK);
        siren.tickForTest(helper.getLevel());
        check(helper, siren.pulseSerial() == 2,
                "A second redstone edge must trigger the cassette a second time");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void slotAcceptsOnlyCassettesAndAutomationGetsNothing(GameTestHelper helper) {
        BlockPos position = new BlockPos(2, 2, 2);
        SirenBlockEntity siren = place(helper, position, SirenTrackItem.Track.HATCH);
        check(helper, siren.canPlaceItem(0,
                        SirenTrackItem.create(ModItems.SIREN_TRACK.get(), SirenTrackItem.Track.KLAXON))
                        && !siren.canPlaceItem(0, new ItemStack(Items.MUSIC_DISC_13)),
                "The siren slot must accept siren cassettes and reject ordinary records");
        for (Direction direction : Direction.values()) {
            check(helper, siren.getSlotsForFace(direction).length == 1
                            && siren.getSlotsForFace(direction)[0] == 0
                            && !siren.canPlaceItemThroughFace(0, siren.getItem(0), direction)
                            && !siren.canTakeItemThroughFace(0, siren.getItem(0), direction),
                    "Source sirens must expose the cassette slot but reject sided insertion and extraction");
        }
        helper.succeed();
    }

    private static SirenBlockEntity place(GameTestHelper helper, BlockPos position,
                                          SirenTrackItem.Track track) {
        helper.setBlock(position, ModBlocks.MACHINE_SIREN.get());
        SirenBlockEntity siren = helper.getBlockEntity(position);
        siren.setItem(0, SirenTrackItem.create(ModItems.SIREN_TRACK.get(), track));
        return siren;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
