package com.hbm.ntm.guide;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.registry.ModAttachments;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuideBookGameTests {
    private GuideBookGameTests() {
    }

    @GameTest(template = "empty")
    public static void revisedStarterGuideHasCompleteUniquePagesAndLiveIcons(GameTestHelper helper) {
        List<GuideBookContent.Page> pages = GuideBookContent.pages();
        check(helper, pages.size() == 40,
                "Reworked starter guide must retain its complete forty-page progression manual");
        Set<String> ids = new HashSet<>();
        for (GuideBookContent.Page page : pages) {
            check(helper, ids.add(page.id()), "Guide page IDs must be unique: " + page.id());
            check(helper, GuideBookContent.english().containsKey(page.titleKey())
                            && GuideBookContent.english().containsKey(page.bodyKey()),
                    "Every page must provide localized title and body copy: " + page.id());
            for (String iconId : page.iconIds()) {
                ResourceLocation id = ResourceLocation.tryParse(iconId);
                check(helper, id != null && BuiltInRegistries.ITEM.getOptional(id).isPresent(),
                        "Guide icon must resolve to a live item: " + iconId);
            }
        }
        for (String required : List.of("steel", "assembly", "silicon", "conveyors", "ducts_and_tanks",
                "steam_cycle", "oil", "chemistry", "crucible", "foundry_transport", "port_scope")) {
            check(helper, ids.contains(required), "Expanded guide is missing required topic: " + required);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void guideIsGrantedOnlyOncePerPlayerSave(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        check(helper, !player.getData(ModAttachments.RECEIVED_GUIDE_BOOK),
                "Fresh player attachment must not claim the starter guide was received");
        check(helper, GuideBookEvents.giveIfNeeded(player),
                "First server-side grant must give the starter guide");
        check(helper, player.getData(ModAttachments.RECEIVED_GUIDE_BOOK)
                        && player.getInventory().countItem(ModItems.BOOK_GUIDE.get()) == 1,
                "First grant must persist the received flag and add exactly one guide");
        check(helper, !GuideBookEvents.giveIfNeeded(player)
                        && player.getInventory().countItem(ModItems.BOOK_GUIDE.get()) == 1,
                "Repeated login grant must not duplicate the guide");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void originalSurvivalRecipeAndStackLimitRemainIntact(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager().byKey(
                ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "book_guide")).orElseThrow();
        check(helper, holder.value() instanceof ShapelessRecipe recipe
                        && recipe.getIngredients().size() == 2
                        && recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(new ItemStack(Items.BOOK)))
                        && recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(new ItemStack(Items.IRON_INGOT)))
                        && recipe.getResultItem(helper.getLevel().registryAccess()).is(ModItems.BOOK_GUIDE.get()),
                "Guide must retain the source shapeless Book plus Iron Ingot survival recipe");
        check(helper, ModItems.BOOK_GUIDE.get().getDefaultMaxStackSize() == 1,
                "Guide Book must retain the source stack limit of one");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
