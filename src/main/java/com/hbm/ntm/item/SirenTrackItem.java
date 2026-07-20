package com.hbm.ntm.item;

import com.hbm.ntm.registry.ModSounds;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;
import java.util.function.Supplier;

/** Twenty alarms on one cassette. Rewinding remains somebody else's problem. */
public final class SirenTrackItem extends Item {
    private static final String TRACK = "track";

    public SirenTrackItem() {
        super(new Properties().stacksTo(1));
    }

    public static ItemStack create(Item item, Track track) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putString(TRACK, track.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(track.legacyMetadata()));
        return stack;
    }

    public static Track track(ItemStack stack) {
        if (!(stack.getItem() instanceof SirenTrackItem)) return Track.NONE;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(TRACK)) return Track.byId(tag.getString(TRACK));
        CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return Track.byMetadata(model == null ? 0 : model.value());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        Track track = track(stack);
        tooltip.add(Component.literal("Siren sound cassette:"));
        tooltip.add(Component.literal("   Name: " + track.title()));
        tooltip.add(Component.literal("   Type: " + track.type().name()));
        tooltip.add(Component.literal("   Volume: " + track.range()));
    }

    public enum SoundType { LOOP, PASS, SOUND }

    public enum Track {
        NONE("none", " ", null, SoundType.SOUND, 0, 0, 0),
        HATCH("hatch", "Hatch Siren", ModSounds.SIREN_HATCH, SoundType.LOOP, 3_358_839, 250, 1),
        AUTOPILOT("autopilot", "Autopilot Disconnected", ModSounds.SIREN_AUTOPILOT,
                SoundType.LOOP, 11_908_533, 50, 2),
        AMS_SIREN("ams_siren", "AMS Siren", ModSounds.SIREN_AMS, SoundType.LOOP, 15_055_698, 50, 3),
        BLAST_DOOR("blast_door", "Blast Door Alarm", ModSounds.SIREN_BLAST_DOOR,
                SoundType.LOOP, 11_665_408, 50, 4),
        APC_LOOP("apc_loop", "APC Siren", ModSounds.SIREN_APC_LOOP, SoundType.LOOP, 3_565_216, 50, 5),
        KLAXON("klaxon", "Klaxon", ModSounds.SIREN_KLAXON, SoundType.LOOP, 8_421_504, 50, 6),
        KLAXON_A("klaxon_a", "Vault Door Alarm", ModSounds.SIREN_KLAXON_A,
                SoundType.LOOP, 0x8C810B, 50, 7),
        KLAXON_B("klaxon_b", "Security Alert", ModSounds.SIREN_KLAXON_B,
                SoundType.LOOP, 0x76818E, 50, 8),
        SIREN("siren", "Standard Siren", ModSounds.SIREN_REGULAR, SoundType.LOOP, 6_684_672, 100, 9),
        CLASSIC("classic", "Classic Siren", ModSounds.SIREN_CLASSIC, SoundType.LOOP, 0xC0CFE8, 100, 10),
        BANK_ALARM("bank_alarm", "Bank Alarm", ModSounds.SIREN_BANK, SoundType.LOOP, 3_572_962, 100, 11),
        BEEP_SIREN("beep_siren", "Beep Siren", ModSounds.SIREN_BEEP,
                SoundType.LOOP, 13_882_323, 100, 12),
        CONTAINER_ALARM("container_alarm", "Container Alarm", ModSounds.SIREN_CONTAINER,
                SoundType.LOOP, 14_727_839, 100, 13),
        SWEEP_SIREN("sweep_siren", "Sweep Siren", ModSounds.SIREN_SWEEP,
                SoundType.LOOP, 15_592_026, 500, 14),
        STRIDER_SIREN("strider_siren", "Missile Silo Siren", ModSounds.SIREN_STRIDER,
                SoundType.LOOP, 11_250_586, 500, 15),
        AIR_RAID("air_raid", "Air Raid Siren", ModSounds.SIREN_AIR_RAID,
                SoundType.LOOP, 0xDF3795, 500, 16),
        NOSTROMO_SIREN("nostromo_siren", "Nostromo Self Destruct", ModSounds.SIREN_NOSTROMO,
                SoundType.LOOP, 0x5DD800, 100, 17),
        EAS_ALARM("eas_alarm", "EAS Alarm Screech", ModSounds.SIREN_EAS,
                SoundType.LOOP, 0xB3A8C1, 50, 18),
        APC_PASS("apc_pass", "APC Pass", ModSounds.SIREN_APC_PASS,
                SoundType.PASS, 3_422_163, 50, 19),
        RAZORTRAIN("razortrain", "Razortrain Horn", ModSounds.SIREN_RAZORTRAIN,
                SoundType.SOUND, 7_819_501, 250, 20);

        private final String id;
        private final String title;
        private final Supplier<? extends SoundEvent> sound;
        private final SoundType type;
        private final int color;
        private final int range;
        private final int legacyMetadata;

        Track(String id, String title, Supplier<? extends SoundEvent> sound, SoundType type,
              int color, int range, int legacyMetadata) {
            this.id = id;
            this.title = title;
            this.sound = sound;
            this.type = type;
            this.color = color;
            this.range = range;
            this.legacyMetadata = legacyMetadata;
        }

        public String id() { return id; }
        public String title() { return title; }
        public SoundEvent sound() { return sound == null ? null : sound.get(); }
        public SoundType type() { return type; }
        public int color() { return color; }
        public int range() { return range; }
        public int legacyMetadata() { return legacyMetadata; }

        public static Track byId(String id) {
            for (Track track : values()) if (track.id.equals(id)) return track;
            return NONE;
        }

        public static Track byMetadata(int metadata) {
            for (Track track : values()) if (track.legacyMetadata == metadata) return track;
            return NONE;
        }
    }
}
