package com.hbm.ntm.block;

import com.mojang.serialization.MapCodec;

public final class AshpitBlock extends ThermalMultiblockBlock {
    public static final MapCodec<AshpitBlock> CODEC = simpleCodec(AshpitBlock::new);

    public AshpitBlock(Properties properties) {
        super(properties, Kind.ASHPIT);
    }

    @Override
    protected MapCodec<? extends AshpitBlock> codec() {
        return CODEC;
    }
}
