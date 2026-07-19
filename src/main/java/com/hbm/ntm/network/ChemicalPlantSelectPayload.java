package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.ChemicalPlantMenu;
import com.hbm.ntm.recipe.ChemicalPlantRecipes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

public record ChemicalPlantSelectPayload(@Nullable ResourceLocation recipeId) implements CustomPacketPayload {
    public static final Type<ChemicalPlantSelectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "chemical_plant_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChemicalPlantSelectPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                        buffer.writeBoolean(payload.recipeId != null);
                        if (payload.recipeId != null) buffer.writeResourceLocation(payload.recipeId);
                    }, buffer -> new ChemicalPlantSelectPayload(
                            buffer.readBoolean() ? buffer.readResourceLocation() : null));
    @Override public Type<ChemicalPlantSelectPayload> type() { return TYPE; }
    public static void handle(ChemicalPlantSelectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().containerMenu instanceof ChemicalPlantMenu menu)
                    || menu.blockEntity() == null) return;
            if (payload.recipeId == null) menu.blockEntity().clearRecipe();
            else if (ChemicalPlantRecipes.get(payload.recipeId) != null) {
                menu.blockEntity().selectRecipe(payload.recipeId);
            }
        });
    }
}
