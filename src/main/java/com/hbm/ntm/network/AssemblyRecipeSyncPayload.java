package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.recipe.AssemblyClientRecipes;
import com.hbm.ntm.recipe.AssemblyRecipe;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record AssemblyRecipeSyncPayload(List<AssemblyRecipe> recipes) implements CustomPacketPayload {
    public static final Type<AssemblyRecipeSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "assembly_recipes"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssemblyRecipeSyncPayload> STREAM_CODEC =
            StreamCodec.ofMember(AssemblyRecipeSyncPayload::encode, AssemblyRecipeSyncPayload::decode);

    public static AssemblyRecipeSyncPayload from(List<AssemblyRecipe> recipes) {
        return new AssemblyRecipeSyncPayload(List.copyOf(recipes));
    }

    @Override public Type<AssemblyRecipeSyncPayload> type() { return TYPE; }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(recipes.size());
        for (AssemblyRecipe recipe : recipes) {
            buffer.writeResourceLocation(recipe.id());
            buffer.writeUtf(recipe.name());
            buffer.writeVarInt(recipe.inputs().size());
            for (AssemblyRecipe.Input input : recipe.inputs()) {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, input.ingredient());
                buffer.writeVarInt(input.count());
            }
            writeFluid(buffer, recipe.fluidInput());
            ItemStack.STREAM_CODEC.encode(buffer, recipe.output());
            writeFluid(buffer, recipe.fluidOutput());
            buffer.writeVarInt(recipe.duration());
            buffer.writeVarLong(recipe.power());
            buffer.writeVarInt(recipe.pools().size());
            for (String pool : recipe.pools()) buffer.writeUtf(pool);
            buffer.writeBoolean(recipe.autoswitch().isPresent());
            recipe.autoswitch().ifPresent(buffer::writeUtf);
        }
    }

    private static AssemblyRecipeSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<AssemblyRecipe> recipes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ResourceLocation id = buffer.readResourceLocation();
            String name = buffer.readUtf();
            int inputCount = buffer.readVarInt();
            List<AssemblyRecipe.Input> inputs = new ArrayList<>(inputCount);
            for (int lane = 0; lane < inputCount; lane++) {
                inputs.add(new AssemblyRecipe.Input(Ingredient.CONTENTS_STREAM_CODEC.decode(buffer), buffer.readVarInt()));
            }
            Optional<AssemblyRecipe.FluidIo> fluidInput = readFluid(buffer);
            ItemStack output = ItemStack.STREAM_CODEC.decode(buffer);
            Optional<AssemblyRecipe.FluidIo> fluidOutput = readFluid(buffer);
            int duration = buffer.readVarInt();
            long power = buffer.readVarLong();
            int poolCount = buffer.readVarInt();
            List<String> pools = new ArrayList<>(poolCount);
            for (int pool = 0; pool < poolCount; pool++) pools.add(buffer.readUtf());
            Optional<String> autoswitch = buffer.readBoolean() ? Optional.of(buffer.readUtf()) : Optional.empty();
            recipes.add(new AssemblyRecipe(id, name, inputs, fluidInput, output, fluidOutput,
                    duration, power, pools, autoswitch));
        }
        return new AssemblyRecipeSyncPayload(recipes);
    }

    private static void writeFluid(RegistryFriendlyByteBuf buffer, Optional<AssemblyRecipe.FluidIo> fluid) {
        buffer.writeBoolean(fluid.isPresent());
        fluid.ifPresent(value -> { buffer.writeResourceLocation(value.fluid()); buffer.writeVarInt(value.amount()); });
    }

    private static Optional<AssemblyRecipe.FluidIo> readFluid(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? Optional.of(new AssemblyRecipe.FluidIo(
                buffer.readResourceLocation(), buffer.readVarInt())) : Optional.empty();
    }

    public static void handle(AssemblyRecipeSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> AssemblyClientRecipes.replace(payload.recipes()));
    }
}
