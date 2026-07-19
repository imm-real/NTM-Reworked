package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.AssemblyMachineBlockEntity;
import com.hbm.ntm.inventory.AssemblyMachineMenu;
import com.hbm.ntm.recipe.AssemblyRecipes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

public record AssemblySelectRecipePayload(@Nullable ResourceLocation recipeId) implements CustomPacketPayload {
    public static final Type<AssemblySelectRecipePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "assembly_select_recipe"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssemblySelectRecipePayload> STREAM_CODEC =
            StreamCodec.ofMember(AssemblySelectRecipePayload::encode, AssemblySelectRecipePayload::decode);

    @Override public Type<AssemblySelectRecipePayload> type() { return TYPE; }
    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(recipeId != null);
        if (recipeId != null) buffer.writeResourceLocation(recipeId);
    }
    private static AssemblySelectRecipePayload decode(RegistryFriendlyByteBuf buffer) {
        return new AssemblySelectRecipePayload(buffer.readBoolean() ? buffer.readResourceLocation() : null);
    }

    public static void handle(AssemblySelectRecipePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof AssemblyMachineMenu menu)) return;
            AssemblyMachineBlockEntity machine = menu.blockEntity();
            if (machine == null) return;
            if (payload.recipeId() == null) machine.clearRecipe();
            else if (AssemblyRecipes.get(payload.recipeId()) != null) machine.selectRecipe(payload.recipeId(), false);
        });
    }
}
