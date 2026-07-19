package com.hbm.ntm.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;

/** Full-height foundry basin for nine-unit and block molds. */
public final class FoundryBasinBlock extends FoundryMoldBlock {
    public static final MapCodec<FoundryBasinBlock> CODEC = simpleCodec(FoundryBasinBlock::new);

    public FoundryBasinBlock(Properties properties) {
        super(properties, true);
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
}
