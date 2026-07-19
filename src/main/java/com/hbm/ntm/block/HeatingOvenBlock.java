package com.hbm.ntm.block;

import com.mojang.serialization.MapCodec;

public final class HeatingOvenBlock extends ThermalMultiblockBlock {
    public static final MapCodec<HeatingOvenBlock> CODEC = simpleCodec(HeatingOvenBlock::new);

    public HeatingOvenBlock(Properties properties) {
        super(properties, Kind.HEATING_OVEN);
    }

    @Override
    protected MapCodec<? extends HeatingOvenBlock> codec() {
        return CODEC;
    }
}
