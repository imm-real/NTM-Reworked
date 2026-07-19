package com.hbm.ntm.radiation;

import com.hbm.ntm.config.HbmConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.ArrayList;
import java.util.List;

/** Persistent living-entity state used by radiation and inhalation hazards. */
public final class RadiationData implements INBTSerializable<CompoundTag> {
    public static final float MAX_RADIATION = 2500.0F;
    public static final float MAX_DIGAMMA = 10.0F;
    public static final int MAX_ASBESTOS = 60 * 60 * 20;
    public static final int MAX_BLACK_LUNG = 2 * 60 * 60 * 20;

    private float radiation;
    private float digamma;
    private int asbestos;
    private int blackLung;
    private int contagion;
    private float radEnv;
    private float radBuf;
    private int radAwayTicks;
    private int radXTicks;
    private final List<ContaminationEffect> contamination = new ArrayList<>();

    public float radiation() {
        return radiation;
    }

    public void setRadiation(float radiation) {
        this.radiation = clamp(radiation, 0, MAX_RADIATION);
    }

    public void addRadiation(float amount) {
        setRadiation(radiation + amount);
    }

    public float digamma() {
        return digamma;
    }

    public void setDigamma(float digamma) {
        this.digamma = clamp(digamma, 0, MAX_DIGAMMA);
    }

    public void addDigamma(float amount) {
        setDigamma(digamma + amount);
    }

    public int asbestos() {
        return asbestos;
    }

    public void setAsbestos(int asbestos) {
        this.asbestos = Math.max(0, asbestos);
    }

    public int blackLung() {
        return blackLung;
    }

    public void setBlackLung(int blackLung) {
        this.blackLung = Math.max(0, blackLung);
    }

    public int contagion() {
        return HbmConfig.ENABLE_MKU.get() ? contagion : 0;
    }

    public void setContagion(int contagion) {
        this.contagion = Math.max(0, contagion);
    }

    public float radEnv() {
        return radEnv;
    }

    public void addEnvironmentalRadiation(float amount) {
        if (Float.isFinite(amount) && amount > 0) {
            radEnv += amount;
        }
    }

    public float radBuf() {
        return radBuf;
    }

    public void finishExposureInterval() {
        radBuf = radEnv;
        radEnv = 0;
    }

    public int radAwayTicks() {
        return radAwayTicks;
    }

    public void addRadAwayTicks(int ticks) {
        radAwayTicks = Math.max(0, radAwayTicks + ticks);
    }

    public int radXTicks() {
        return radXTicks;
    }

    public void refreshRadXTicks(int ticks) {
        radXTicks = Math.max(radXTicks, Math.max(0, ticks));
    }

    public void tickMedicine() {
        if (radAwayTicks > 0) {
            addRadiation(-1.0F);
            radAwayTicks--;
        }
        if (radXTicks > 0) {
            radXTicks--;
        }
    }

    public float medicineResistance() {
        return radXTicks > 0 ? 0.2F : 0.0F;
    }

    public List<ContaminationEffect> contamination() {
        return contamination;
    }

    public void addContamination(float maxRadiation, int duration, boolean bypassResistance) {
        if (Float.isFinite(maxRadiation) && maxRadiation > 0 && duration > 0) {
            contamination.add(new ContaminationEffect(maxRadiation, duration, duration, bypassResistance));
        }
    }

    public void applySyncedState(
            float radiation,
            float digamma,
            int asbestos,
            int blackLung,
            int contagion,
            int radAwayTicks,
            int radXTicks,
            List<ContaminationEffect> contamination
    ) {
        this.radiation = clamp(radiation, 0, MAX_RADIATION);
        this.digamma = clamp(digamma, 0, MAX_DIGAMMA);
        this.asbestos = Math.max(0, asbestos);
        this.blackLung = Math.max(0, blackLung);
        this.contagion = HbmConfig.ENABLE_MKU.get() ? Math.max(0, contagion) : 0;
        this.radAwayTicks = Math.max(0, radAwayTicks);
        this.radXTicks = Math.max(0, radXTicks);
        this.contamination.clear();
        this.contamination.addAll(contamination);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("hfr_radiation", radiation);
        tag.putFloat("hfr_digamma", digamma);
        tag.putInt("hfr_asbestos", asbestos);
        tag.putInt("hfr_blacklung", blackLung);
        if (HbmConfig.ENABLE_MKU.get()) tag.putInt("hfr_contagion", contagion);
        tag.putInt("hfr_radaway", radAwayTicks);
        tag.putInt("hfr_radx", radXTicks);

        ListTag contaminationTag = new ListTag();
        for (ContaminationEffect effect : contamination) {
            contaminationTag.add(effect.save());
        }
        tag.put("hfr_contamination", contaminationTag);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        radiation = clamp(tag.getFloat("hfr_radiation"), 0, MAX_RADIATION);
        digamma = clamp(tag.getFloat("hfr_digamma"), 0, MAX_DIGAMMA);
        asbestos = Math.max(0, tag.getInt("hfr_asbestos"));
        blackLung = Math.max(0, tag.getInt("hfr_blacklung"));
        contagion = HbmConfig.ENABLE_MKU.get() ? Math.max(0, tag.getInt("hfr_contagion")) : 0;
        radAwayTicks = Math.max(0, tag.getInt("hfr_radaway"));
        radXTicks = Math.max(0, tag.getInt("hfr_radx"));
        contamination.clear();
        ListTag contaminationTag = tag.getList("hfr_contamination", Tag.TAG_COMPOUND);
        for (int i = 0; i < contaminationTag.size(); i++) {
            ContaminationEffect effect = ContaminationEffect.load(contaminationTag.getCompound(i));
            if (effect != null) {
                contamination.add(effect);
            }
        }
    }

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    public static final class ContaminationEffect {
        private final float maxRadiation;
        private final int maxTime;
        private int time;
        private final boolean bypassResistance;

        public ContaminationEffect(float maxRadiation, int maxTime, int time, boolean bypassResistance) {
            this.maxRadiation = maxRadiation;
            this.maxTime = maxTime;
            this.time = time;
            this.bypassResistance = bypassResistance;
        }

        public float maxRadiation() {
            return maxRadiation;
        }

        public int maxTime() {
            return maxTime;
        }

        public int time() {
            return time;
        }

        public boolean bypassResistance() {
            return bypassResistance;
        }

        public float emission() {
            if (maxTime <= 0 || time <= 0) {
                return 0;
            }
            return maxRadiation * time / (float) maxTime;
        }

        public boolean tickDown() {
            time--;
            return time <= 0;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putFloat("maxRad", maxRadiation);
            tag.putInt("maxTime", maxTime);
            tag.putInt("time", time);
            tag.putBoolean("ignoreArmor", bypassResistance);
            return tag;
        }

        private static ContaminationEffect load(CompoundTag tag) {
            float maxRadiation = tag.getFloat("maxRad");
            int maxTime = tag.getInt("maxTime");
            int time = tag.getInt("time");
            if (!Float.isFinite(maxRadiation) || maxRadiation <= 0 || maxTime <= 0 || time <= 0) {
                return null;
            }
            return new ContaminationEffect(maxRadiation, maxTime, time, tag.getBoolean("ignoreArmor"));
        }
    }
}
