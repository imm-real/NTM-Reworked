package com.hbm.ntm.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** World-global, one-way record of the B93 solar shot. */
public final class SunDestructionData extends SavedData {
    private static final String FILE_NAME = "hbm_sun_destruction";
    private static final String DESTROYED = "destroyed";
    private static final SavedData.Factory<SunDestructionData> FACTORY =
            new SavedData.Factory<>(SunDestructionData::new, SunDestructionData::load);

    private boolean destroyed;

    public static SunDestructionData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    /** @return true only for the first solar destruction in this world save. */
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

    static SunDestructionData load(CompoundTag tag, HolderLookup.Provider registries) {
        SunDestructionData data = new SunDestructionData();
        data.destroyed = tag.getBoolean(DESTROYED);
        return data;
    }
}
