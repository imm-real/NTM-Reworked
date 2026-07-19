package com.hbm.ntm.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** Ore chunks with their mineral identity written in component ink. */
public final class OreChunkItem extends Item {
    private static final String TYPE = "type";

    public OreChunkItem() {
        super(new Properties());
    }

    public static ItemStack rare(Item item, int count) {
        return create(item, ChunkType.RARE, count);
    }

    public static ItemStack create(Item item, ChunkType type, int count) {
        ItemStack stack = new ItemStack(item, count);
        CompoundTag data = new CompoundTag();
        data.putString(TYPE, type.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(type.legacyMetadata()));
        return stack;
    }

    public static ChunkType type(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ChunkType stored = ChunkType.byId(data.getString(TYPE));
        if (stored != null) {
            return stored;
        }
        CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (model != null) {
            ChunkType legacy = ChunkType.byLegacyMetadata(model.value());
            if (legacy != null) {
                return legacy;
            }
        }
        return ChunkType.RARE;
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return "item.hbm.chunk_ore." + type(stack).id();
    }

    public enum ChunkType {
        RARE("rare", 0),
        MALACHITE("malachite", 1),
        CRYOLITE("cryolite", 2),
        MOONSTONE("moonstone", 3);

        private final String id;
        private final int legacyMetadata;

        ChunkType(String id, int legacyMetadata) {
            this.id = id;
            this.legacyMetadata = legacyMetadata;
        }

        public String id() {
            return id;
        }

        public int legacyMetadata() {
            return legacyMetadata;
        }

        public static ChunkType byId(String id) {
            for (ChunkType type : values()) {
                if (type.id.equals(id)) {
                    return type;
                }
            }
            return null;
        }

        public static ChunkType byLegacyMetadata(int metadata) {
            for (ChunkType type : values()) {
                if (type.legacyMetadata == metadata) {
                    return type;
                }
            }
            return null;
        }
    }
}
