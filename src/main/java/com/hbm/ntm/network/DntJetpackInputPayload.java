package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.registry.ModAttachments;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DntJetpackInputPayload(boolean jumpHeld, boolean toggleJetpack,
                                     boolean toggleHud) implements CustomPacketPayload {
    public static final Type<DntJetpackInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "dnt_jetpack_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DntJetpackInputPayload> STREAM_CODEC =
            StreamCodec.ofMember(DntJetpackInputPayload::encode, DntJetpackInputPayload::decode);

    @Override
    public Type<DntJetpackInputPayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(jumpHeld);
        buffer.writeBoolean(toggleJetpack);
        buffer.writeBoolean(toggleHud);
    }

    private static DntJetpackInputPayload decode(RegistryFriendlyByteBuf buffer) {
        return new DntJetpackInputPayload(buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
    }

    public static void handle(DntJetpackInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            player.setData(ModAttachments.DNT_JETPACK_ACTIVE, payload.jumpHeld);
            if (payload.toggleJetpack) {
                boolean enabled = !player.getData(ModAttachments.DNT_JETPACK_ENABLED);
                player.setData(ModAttachments.DNT_JETPACK_ENABLED, enabled);
                player.displayClientMessage(Component.translatable(
                        enabled ? "armor.dnt.jetpack_on" : "armor.dnt.jetpack_off"), true);
            }
            if (payload.toggleHud) {
                boolean enabled = !player.getData(ModAttachments.DNT_HUD_ENABLED);
                player.setData(ModAttachments.DNT_HUD_ENABLED, enabled);
                player.displayClientMessage(Component.translatable(
                        enabled ? "armor.dnt.hud_on" : "armor.dnt.hud_off"), true);
            }
            if (payload.toggleJetpack || payload.toggleHud) {
                PacketDistributor.sendToPlayer(player, new DntJetpackStatePayload(
                        player.getData(ModAttachments.DNT_JETPACK_ENABLED),
                        player.getData(ModAttachments.DNT_HUD_ENABLED)));
            }
        });
    }
}
