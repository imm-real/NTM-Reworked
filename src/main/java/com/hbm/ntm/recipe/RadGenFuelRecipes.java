package com.hbm.ntm.recipe;

import com.hbm.ntm.item.NuclearWasteItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Things radioactive enough to make the generator happy. */
public final class RadGenFuelRecipes {
    public static final ResourceLocation SHORT = id("nuclear_waste_short");
    public static final ResourceLocation SHORT_TINY = id("nuclear_waste_short_tiny");
    public static final ResourceLocation LONG = id("nuclear_waste_long");
    public static final ResourceLocation LONG_TINY = id("nuclear_waste_long_tiny");
    public static final ResourceLocation NUCLEAR_SCRAP = id("scrap_nuclear");
    public static final ResourceLocation RADIATION_GEM = id("gem_rad");

    private static final Map<ResourceLocation, Definition> DEFINITIONS = definitions();

    private RadGenFuelRecipes() { }

    /** Missing nuclear waste is not permission to burn a potato instead. */
    @Nullable
    public static Fuel find(ItemStack input) {
        if (input.isEmpty()) return null;
        Definition definition = DEFINITIONS.get(BuiltInRegistries.ITEM.getKey(input.getItem()));
        if (definition == null) return null;

        ItemStack output = ItemStack.EMPTY;
        if (definition.output() != null) {
            Optional<Item> registered = BuiltInRegistries.ITEM.getOptional(definition.output());
            if (registered.isEmpty() || registered.get() == Items.AIR) return null;
            output = definition.copyComponents() && input.getItem() instanceof NuclearWasteItem
                    ? NuclearWasteItem.stack(registered.get(), NuclearWasteItem.variant(input), 1)
                    : new ItemStack(registered.get());
        }
        return new Fuel(definition.power(), definition.duration(), output);
    }

    /** Keep identity, discard the handwritten luggage tags. */
    public static ItemStack processingCopy(ItemStack input) {
        if (input.isEmpty()) return ItemStack.EMPTY;
        if (input.getItem() instanceof NuclearWasteItem) {
            return NuclearWasteItem.stack(input.getItem(), NuclearWasteItem.variant(input), 1);
        }
        return new ItemStack(input.getItem());
    }

    @Nullable
    public static Definition definition(ResourceLocation input) {
        return DEFINITIONS.get(input);
    }

    private static Map<ResourceLocation, Definition> definitions() {
        Map<ResourceLocation, Definition> fuels = new LinkedHashMap<>();
        fuels.put(SHORT, new Definition(1_500, 30 * 60 * 20,
                id("nuclear_waste_short_depleted"), true));
        fuels.put(SHORT_TINY, new Definition(150, 3 * 60 * 20,
                id("nuclear_waste_short_depleted_tiny"), true));
        fuels.put(LONG, new Definition(500, 2 * 60 * 60 * 20,
                id("nuclear_waste_long_depleted"), true));
        fuels.put(LONG_TINY, new Definition(50, 12 * 60 * 20,
                id("nuclear_waste_long_depleted_tiny"), true));
        fuels.put(NUCLEAR_SCRAP, new Definition(50, 5 * 60 * 20, null, false));
        fuels.put(RADIATION_GEM, new Definition(25_000, 30 * 60 * 20,
                BuiltInRegistries.ITEM.getKey(Items.DIAMOND), false));
        return Map.copyOf(fuels);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("hbm", path);
    }

    public record Definition(int power, int duration, @Nullable ResourceLocation output,
                             boolean copyComponents) { }

    public record Fuel(int power, int duration, ItemStack output) {
        public Fuel {
            output = output.copy();
        }

        @Override public ItemStack output() { return output.copy(); }
    }
}
