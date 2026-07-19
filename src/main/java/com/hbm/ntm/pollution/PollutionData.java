package com.hbm.ntm.pollution;

import com.hbm.ntm.config.HbmConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/** Persistent 64x64 pollution field. The grime has coordinates. */
public final class PollutionData extends SavedData {
    private static final String FILE_NAME = "hbmpollution";
    private static final SavedData.Factory<PollutionData> FACTORY =
            new SavedData.Factory<>(PollutionData::new, PollutionData::load);
    private final Map<Long, Entry> pollution = new HashMap<>();

    public static PollutionData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    public float get(BlockPos position, Type type) {
        Entry entry = pollution.get(key(position.getX() >> 6, position.getZ() >> 6));
        return entry == null ? 0.0F : entry.values[type.ordinal()];
    }

    public void increment(BlockPos position, Type type, float amount) {
        if (!HbmConfig.ENABLE_POLLUTION.get() || !Float.isFinite(amount) || amount == 0.0F) {
            return;
        }
        long key = key(position.getX() >> 6, position.getZ() >> 6);
        Entry entry = pollution.computeIfAbsent(key, ignored -> new Entry());
        int index = type.ordinal();
        entry.values[index] = clamp(entry.values[index] + amount * HbmConfig.POLLUTION_MULTIPLIER.get().floatValue());
        setDirty();
    }

    public void spreadAndDecay() {
        Map<Long, Entry> next = new HashMap<>();
        for (Map.Entry<Long, Entry> sourceEntry : pollution.entrySet()) {
            int x = x(sourceEntry.getKey());
            int z = z(sourceEntry.getKey());
            Entry source = sourceEntry.getValue().copy();
            float[] spread = new float[Type.values().length];

            int soot = Type.SOOT.ordinal();
            int poison = Type.POISON.ordinal();
            int heavy = Type.HEAVYMETAL.ordinal();
            if (source.values[soot] > 10.0F) {
                spread[soot] = source.values[soot] * 0.05F;
                source.values[soot] *= 0.8F;
            }
            source.values[soot] *= 0.99F;
            source.values[heavy] *= 0.9995F;
            if (source.values[poison] > 10.0F) {
                spread[poison] = source.values[poison] * 0.025F;
                source.values[poison] *= 0.9F;
            } else {
                source.values[poison] *= 0.995F;
            }

            add(next, key(x, z), source.values);
            add(next, key(x + 1, z), spread);
            add(next, key(x - 1, z), spread);
            add(next, key(x, z + 1), spread);
            add(next, key(x, z - 1), spread);
        }
        pollution.clear();
        next.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        pollution.putAll(next);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Map.Entry<Long, Entry> mapEntry : pollution.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("chunkX", x(mapEntry.getKey()));
            entry.putInt("chunkZ", z(mapEntry.getKey()));
            for (Type type : Type.values()) {
                entry.putFloat(type.serializedName, mapEntry.getValue().values[type.ordinal()]);
            }
            entries.add(entry);
        }
        tag.put("entries", entries);
        return tag;
    }

    private static PollutionData load(CompoundTag tag, HolderLookup.Provider registries) {
        PollutionData data = new PollutionData();
        ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entryTag = entries.getCompound(i);
            Entry entry = new Entry();
            for (Type type : Type.values()) {
                entry.values[type.ordinal()] = clamp(entryTag.getFloat(type.serializedName));
            }
            if (!entry.isEmpty()) {
                data.pollution.put(key(entryTag.getInt("chunkX"), entryTag.getInt("chunkZ")), entry);
            }
        }
        return data;
    }

    private static void add(Map<Long, Entry> target, long key, float[] values) {
        Entry entry = target.computeIfAbsent(key, ignored -> new Entry());
        for (int i = 0; i < values.length; i++) {
            entry.values[i] += values[i];
        }
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(10_000.0F, Float.isFinite(value) ? value : 0.0F));
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int x(long key) {
        return (int) (key >> 32);
    }

    private static int z(long key) {
        return (int) key;
    }

    private static final class Entry {
        private final float[] values = new float[Type.values().length];

        private Entry copy() {
            Entry copy = new Entry();
            System.arraycopy(values, 0, copy.values, 0, values.length);
            return copy;
        }

        private boolean isEmpty() {
            for (float value : values) {
                if (value > 0.0F) {
                    return false;
                }
            }
            return true;
        }
    }

    public enum Type {
        SOOT("soot"),
        POISON("poison"),
        HEAVYMETAL("heavymetal"),
        FALLOUT("fallout");

        private final String serializedName;

        Type(String serializedName) {
            this.serializedName = serializedName;
        }
    }
}
