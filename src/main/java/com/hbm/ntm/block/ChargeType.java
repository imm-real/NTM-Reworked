package com.hbm.ntm.block;

import net.minecraft.util.StringRepresentable;

public enum ChargeType implements StringRepresentable {
    DYNAMITE("charge_dynamite", "charge_dynamite", 4.0F, 16, DropMode.DECAY, true, EffectSize.SMALL),
    MINER("charge_miner", "charge_dynamite", 4.0F, 16, DropMode.ALL, false, EffectSize.SMALL),
    C4("charge_c4", "charge_c4", 15.0F, 32, DropMode.NONE, true, EffectSize.LARGE),
    SEMTEX("charge_semtex", "charge_c4", 10.0F, 32, DropMode.FORTUNE_THREE, false, EffectSize.LARGE);

    private final String id;
    private final String model;
    private final float strength;
    private final int resolution;
    private final DropMode drops;
    private final boolean damagesEntities;
    private final EffectSize effectSize;

    ChargeType(String id, String model, float strength, int resolution, DropMode drops,
               boolean damagesEntities, EffectSize effectSize) {
        this.id = id;
        this.model = model;
        this.strength = strength;
        this.resolution = resolution;
        this.drops = drops;
        this.damagesEntities = damagesEntities;
        this.effectSize = effectSize;
    }

    public static final StringRepresentable.EnumCodec<ChargeType> CODEC = StringRepresentable.fromEnum(ChargeType::values);

    @Override
    public String getSerializedName() { return id; }
    public String id() { return id; }
    public String model() { return model; }
    public float strength() { return strength; }
    public int resolution() { return resolution; }
    public DropMode drops() { return drops; }
    public boolean damagesEntities() { return damagesEntities; }
    public EffectSize effectSize() { return effectSize; }

    public enum DropMode {
        DECAY,
        ALL,
        FORTUNE_THREE,
        NONE
    }

    public enum EffectSize {
        SMALL,
        LARGE
    }
}
