package com.hbm.ntm.recipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.hbm.ntm.HbmNtm;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AssemblyRecipes extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile Map<ResourceLocation, AssemblyRecipe> recipes = Map.of();
    private final RegistryAccess registries;

    private AssemblyRecipes(RegistryAccess registries) {
        super(GSON, "assembly_machine");
        this.registries = registries;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(AssemblyRecipes::addReloadListener);
    }

    private static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new AssemblyRecipes(event.getRegistryAccess()));
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager resources,
                         ProfilerFiller profiler) {
        RegistryOps<JsonElement> ops = RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, registries);
        Map<ResourceLocation, AssemblyRecipe> loaded = new LinkedHashMap<>();
        objects.forEach((fileId, json) -> AssemblyRecipe.CODEC.parse(ops, json)
                .resultOrPartial(message -> HbmNtm.LOGGER.error("Invalid Assembly recipe {}: {}", fileId, message))
                .ifPresent(recipe -> {
                    if (loaded.putIfAbsent(recipe.id(), recipe) != null) {
                        HbmNtm.LOGGER.error("Duplicate Assembly recipe id {} from {}", recipe.id(), fileId);
                    }
                }));
        recipes = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
        HbmNtm.LOGGER.info("Loaded {} Assembly Machine recipe(s)", recipes.size());
    }

    public static AssemblyRecipe get(ResourceLocation id) { return recipes.get(id); }

    public static AssemblyRecipe byName(String name) {
        for (AssemblyRecipe recipe : recipes.values()) if (recipe.name().equals(name)) return recipe;
        return null;
    }

    public static List<AssemblyRecipe> all() { return List.copyOf(recipes.values()); }
}
