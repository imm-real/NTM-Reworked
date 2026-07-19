package com.hbm.ntm.block;

import com.mojang.serialization.MapCodec;

public final class StirlingBlock extends ThermalMultiblockBlock {
    public static final MapCodec<StirlingBlock> CODEC = simpleCodec(StirlingBlock::new);

    public StirlingBlock(Properties properties) {
        super(properties, Kind.STIRLING);
    }

    @Override
    protected MapCodec<? extends StirlingBlock> codec() {
        return CODEC;
    }
}
