package com.hbm.ntm.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** World-global, one-way record of the B92 moon shot. */
public final class MoonDestructionData extends SavedData {
    private static final String FILE_NAME = "hbm_moon_destruction";
    private static final String DESTROYED = "destroyed";
    private static final SavedData.Factory<MoonDestructionData> FACTORY =
            new SavedData.Factory<>(MoonDestructionData::new, MoonDestructionData::load);

    private boolean destroyed;

    public static MoonDestructionData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    /** @return true only for the first destruction in this world save. */
    public boolean destroy() {
        if (destroyed) return false;
        destroyed = true;
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(DESTROYED, destroyed);
        return tag;
    }

    static MoonDestructionData load(CompoundTag tag, HolderLookup.Provider registries) {
        MoonDestructionData data = new MoonDestructionData();
        data.destroyed = tag.getBoolean(DESTROYED);
        return data;
    }
}
