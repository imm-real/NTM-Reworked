package com.hbm.ntm.explosion;

/** Original {@code IBomb.BombReturnCode} contract used by remote detonators. */
public enum DetonationResult {
    UNDEFINED(false, ""),
    DETONATED(true, "bomb.detonated"),
    TRIGGERED(true, "bomb.triggered"),
    LAUNCHED(true, "bomb.launched"),
    ERROR_MISSING_COMPONENT(false, "bomb.missingComponent"),
    ERROR_INCOMPATIBLE(false, "bomb.incompatible"),
    ERROR_NO_BOMB(false, "bomb.nobomb");

    private final boolean successful;
    private final String translationKey;

    DetonationResult(boolean successful, String translationKey) {
        this.successful = successful;
        this.translationKey = translationKey;
    }

    public boolean wasSuccessful() {
        return successful;
    }

    public String translationKey() {
        return translationKey;
    }
}
