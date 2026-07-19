package com.hbm.ntm.network;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.NukeFstbmbMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Source {@code AuxButtonPacket} for the Balefire Bomb GUI. {@code meta 0} = start button (arms only when
 * loaded); {@code meta 1} = timer field, {@code value} seconds. Routed through the open menu, matching the
 * target's other control payloads.
 */
public record NukeFstbmbButtonPayload(int value, int meta) implements CustomPacketPayload {
    public static final Type<NukeFstbmbButtonPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "nuke_fstbmb_button"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NukeFstbmbButtonPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                buffer.writeVarInt(payload.value);
                buffer.writeVarInt(payload.meta);
            }, buffer -> new NukeFstbmbButtonPayload(buffer.readVarInt(), buffer.readVarInt()));

    @Override public Type<NukeFstbmbButtonPayload> type() { return TYPE; }

    public static void handle(NukeFstbmbButtonPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof NukeFstbmbMenu menu
                    && menu.blockEntity() != null) {
                menu.blockEntity().handleButtonPacket(payload.value, payload.meta);
            }
        });
    }
}
