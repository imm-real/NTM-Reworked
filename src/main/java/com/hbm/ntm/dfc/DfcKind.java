package com.hbm.ntm.dfc;

public enum DfcKind {
    CORE("container.dfcCore", 3),
    EMITTER("container.dfcEmitter", 0),
    INJECTOR("container.dfcInjector", 4),
    RECEIVER("container.dfcReceiver", 0),
    STABILIZER("container.dfcStabilizer", 1);

    private final String translationKey;
    private final int slots;

    DfcKind(String translationKey, int slots) {
        this.translationKey = translationKey;
        this.slots = slots;
    }

    public String translationKey() { return translationKey; }
    public int slots() { return slots; }
}
