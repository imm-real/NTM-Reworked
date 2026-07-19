package com.hbm.ntm.block;

import com.mojang.serialization.MapCodec;

public final class FireboxBlock extends ThermalMultiblockBlock {
    public static final MapCodec<FireboxBlock> CODEC = simpleCodec(FireboxBlock::new);

    public FireboxBlock(Properties properties) {
        super(properties, Kind.FIREBOX);
    }

    @Override
    protected MapCodec<? extends FireboxBlock> codec() {
        return CODEC;
    }
}
