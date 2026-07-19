package com.hbm.ntm.registry;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.energy.HeNetworkManager;
import com.hbm.ntm.radiation.RadiationData;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, HbmNtm.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<HeNetworkManager>> HE_NETWORKS = ATTACHMENTS.register(
            "he_networks",
            () -> AttachmentType.builder(HeNetworkManager::new).build()
    );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> CHUNK_RADIATION = ATTACHMENTS.register(
            "chunk_radiation",
            () -> AttachmentType.builder(() -> 0.0F)
                    .serialize(Codec.FLOAT)
                    .build()
    );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> RECEIVED_GUIDE_BOOK =
            ATTACHMENTS.register(
                    "received_guide_book",
                    () -> AttachmentType.builder(() -> false)
                            .serialize(Codec.BOOL, Boolean::booleanValue)
                            .copyOnDeath()
                            .build()
            );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> DNT_JETPACK_ENABLED =
            ATTACHMENTS.register(
                    "dnt_jetpack_enabled",
                    () -> AttachmentType.builder(() -> true)
                            .serialize(Codec.BOOL, Boolean::booleanValue)
                            .copyOnDeath()
                            .build()
            );

    /** Current Space-key state. Server authoritative and intentionally not persisted. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> DNT_JETPACK_ACTIVE =
            ATTACHMENTS.register(
                    "dnt_jetpack_active",
                    () -> AttachmentType.builder(() -> false).build()
            );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> DNT_HUD_ENABLED =
            ATTACHMENTS.register(
                    "dnt_hud_enabled",
                    () -> AttachmentType.builder(() -> true)
                            .serialize(Codec.BOOL, Boolean::booleanValue)
                            .copyOnDeath()
                            .build()
            );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<RadiationData>> RADIATION = ATTACHMENTS.register(
            "radiation",
            () -> AttachmentType.serializable(RadiationData::new)
                    .sync(new AttachmentSyncHandler<>() {
                        @Override
                        public boolean sendToPlayer(IAttachmentHolder holder, net.minecraft.server.level.ServerPlayer to) {
                            return holder == to;
                        }

                        @Override
                        public void write(RegistryFriendlyByteBuf buf, RadiationData data, boolean initialSync) {
                            buf.writeFloat(data.radiation());
                            buf.writeFloat(data.digamma());
                            buf.writeVarInt(data.asbestos());
                            buf.writeVarInt(data.blackLung());
                            buf.writeVarInt(data.contagion());
                            buf.writeVarInt(data.radAwayTicks());
                            buf.writeVarInt(data.radXTicks());
                            buf.writeVarInt(data.contamination().size());
                            for (RadiationData.ContaminationEffect effect : data.contamination()) {
                                buf.writeFloat(effect.maxRadiation());
                                buf.writeVarInt(effect.maxTime());
                                buf.writeVarInt(effect.time());
                                buf.writeBoolean(effect.bypassResistance());
                            }
                        }

                        @Override
                        public RadiationData read(
                                IAttachmentHolder holder,
                                RegistryFriendlyByteBuf buf,
                                @Nullable RadiationData previousValue
                        ) {
                            RadiationData data = previousValue == null ? new RadiationData() : previousValue;
                            float radiation = buf.readFloat();
                            float digamma = buf.readFloat();
                            int asbestos = buf.readVarInt();
                            int blackLung = buf.readVarInt();
                            int contagion = buf.readVarInt();
                            int radAwayTicks = buf.readVarInt();
                            int radXTicks = buf.readVarInt();
                            int contaminationCount = Math.min(buf.readVarInt(), 1024);
                            List<RadiationData.ContaminationEffect> contamination = new ArrayList<>(contaminationCount);
                            for (int i = 0; i < contaminationCount; i++) {
                                contamination.add(new RadiationData.ContaminationEffect(
                                        buf.readFloat(),
                                        buf.readVarInt(),
                                        buf.readVarInt(),
                                        buf.readBoolean()
                                ));
                            }
                            data.applySyncedState(
                                    radiation,
                                    digamma,
                                    asbestos,
                                    blackLung,
                                    contagion,
                                    radAwayTicks,
                                    radXTicks,
                                    contamination
                            );
                            return data;
                        }
                    })
                    .build()
    );

    private ModAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
