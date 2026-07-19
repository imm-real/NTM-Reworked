package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.anvil.AnvilRecipes;
import com.hbm.ntm.inventory.AnvilMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AnvilCraftPayload(ResourceLocation recipeId, boolean bulk) implements CustomPacketPayload {
    public static final Type<AnvilCraftPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "anvil_craft"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AnvilCraftPayload> STREAM_CODEC =
            StreamCodec.ofMember(AnvilCraftPayload::encode, AnvilCraftPayload::decode);

    @Override public Type<AnvilCraftPayload> type() { return TYPE; }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeResourceLocation(recipeId);
        buffer.writeBoolean(bulk);
    }

    private static AnvilCraftPayload decode(RegistryFriendlyByteBuf buffer) {
        return new AnvilCraftPayload(buffer.readResourceLocation(), buffer.readBoolean());
    }

    public static void handle(AnvilCraftPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof AnvilMenu menu)) return;
            AnvilRecipes.Construction recipe = AnvilRecipes.byId(payload.recipeId());
            if (recipe == null || !recipe.validForTier(menu.tier())) return;
            AnvilRecipes.craft(player, recipe, payload.bulk());
        });
    }
}
