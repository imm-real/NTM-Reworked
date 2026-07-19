package com.hbm.ntm.nuclear;

import com.hbm.ntm.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Client-side Toroidal Convection Simulation Explosion effect. */
public final class MushroomCloudEntity extends Entity {
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(
            MushroomCloudEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> MAX_AGE = SynchedEntityData.defineId(
            MushroomCloudEntity.class, EntityDataSerializers.INT);
    // Source EntityNukeTorex dataWatcher slot 11: 0 = standard, 1 = bale (green-forward), 2 = solinium.
    private static final EntityDataAccessor<Integer> TYPE = SynchedEntityData.defineId(
            MushroomCloudEntity.class, EntityDataSerializers.INT);

    private final List<Cloudlet> cloudlets = new ArrayList<>();
    private final List<Cloudlet> cloudletView = Collections.unmodifiableList(cloudlets);
    private int age;
    private double coreHeight = 3.0D;
    private double convectionHeight = 3.0D;
    private double torusWidth = 3.0D;
    private double rollerSize = 1.0D;
    private double heat = 1.0D;
    private double lastSpawnY = -1.0D;
    private boolean heardSound;
    private boolean shookCamera;

    public MushroomCloudEntity(EntityType<? extends MushroomCloudEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SCALE, 1.0F);
        builder.define(MAX_AGE, 900);
        builder.define(TYPE, 0);
    }

    /** Source EntityNukeTorex.statFacBale: the balefire cloud uses the type-1 green-forward color path. */
    public void configureBale(int radius) {
        entityData.set(TYPE, 1);
        configure(radius);
    }

    public int cloudType() { return entityData.get(TYPE); }

    public void configure(int radius) {
        float value = (float) Math.sqrt(radius * 0.01D + 1.0D / Math.pow(radius * 0.01D + 2.0D, 2.0D))
                - 1.0F / (radius * 0.01F + 2.0F);
        configureScale(Mth.clamp(value * 1.5F, 0.5F, 5.0F));
    }

    public void configureScale(float scale) {
        entityData.set(SCALE, scale);
        entityData.set(MAX_AGE, (int) (900.0F * scale));
    }

    public float cloudScale() { return entityData.get(SCALE); }
    public int maxAge() { return entityData.get(MAX_AGE); }
    public int age() { return age; }
    public double coreHeight() { return coreHeight; }
    public double rollerSize() { return rollerSize; }
    public double shockwaveY() { return lastSpawnY == -1.0D ? getY() : lastSpawnY; }
    public float shockwaveRadius(float partialTick) { return (age + partialTick) * 2.25F; }
    public float shockwaveAlpha(float partialTick) {
        float progress = (age + partialTick) / 105.0F;
        return Mth.clamp((1.0F - progress) * 0.62F, 0.0F, 0.62F);
    }
    public List<Cloudlet> cloudlets() { return cloudletView; }
    public boolean heardSound() { return heardSound; }
    public boolean shookCamera() { return shookCamera; }
    public void markCameraShaken() { shookCamera = true; }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (!level().isClientSide) {
            if (age > maxAge()) discard();
            return;
        }

        if (age < 100) level().setSkyFlashTime(2);
        if (lastSpawnY == -1.0D) lastSpawnY = getY() - 3.0D;
        int target = Math.max(level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(getX()), Mth.floor(getZ())) - 3, 1);
        if (Math.abs(target - lastSpawnY) < 0.5D) lastSpawnY = target;
        else lastSpawnY += 0.5D * Math.signum(target - lastSpawnY);

        spawnStandardCloudlets();
        spawnShockCloudlets();
        spawnRingCloudlets();
        spawnCondensationCloudlets();

        for (Cloudlet cloudlet : cloudlets) cloudlet.update();
        coreHeight += 0.1D;
        torusWidth += 1.0D / 30.0D;
        rollerSize = torusWidth * 0.35D;
        convectionHeight = coreHeight + rollerSize;
        heat = 75.0D - 75.0D * age / maxAge();
        cloudlets.removeIf(Cloudlet::dead);
    }

    private void spawnStandardCloudlets() {
        double range = (torusWidth - rollerSize) * 0.25D;
        double speed = simulationSpeed();
        int count = (int) Math.ceil(10.0D * speed * speed);
        int lifetime = Math.min(age * age + 200, maxAge() - age + 200);
        for (int i = 0; i < count; i++) {
            Cloudlet cloudlet = new Cloudlet(
                    getX() + random.nextGaussian() * range,
                    lastSpawnY,
                    getZ() + random.nextGaussian() * range,
                    random.nextFloat() * Mth.TWO_PI,
                    lifetime,
                    TorexType.STANDARD
            );
            cloudlet.setScale(1.0F + age * 0.0075F, 7.5F);
            cloudlets.add(cloudlet);
        }
    }

    private void spawnShockCloudlets() {
        if (age >= 150) return;
        // age*5 cloudlets can exceed thirty thousand overlapping quads. The soft
        // shader-compatible cloud texture needs far less overdraw to form the same dust front.
        int count = Math.max(2, Mth.ceil(age * 0.75F));
        int lifetime = Math.max(300 - age * 20, 50);
        for (int i = 0; i < count; i++) {
            double distance = (age * 1.5D + random.nextDouble()) * 1.5D;
            float angle = random.nextFloat() * Mth.TWO_PI;
            double x = getX() + Math.cos(angle) * distance;
            double z = getZ() + Math.sin(angle) * distance;
            double y = level().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x + 1, (int) z);
            Cloudlet cloudlet = new Cloudlet(x, y, z, angle, lifetime, TorexType.SHOCK);
            cloudlet.setScale(8.5F, 3.0F).setMotion(age > 15 ? 0.75D : 0.0D);
            cloudlets.add(cloudlet);
        }

        if (!heardSound) {
            double wavefront = (age * 1.5D + 1.0D) * 1.5D;
            if (!level().players().isEmpty() && level().players().getFirst().distanceToSqr(this) < wavefront * wavefront) {
                heardSound = true;
                level().playLocalSound(getX(), getY(), getZ(), ModSounds.NUCLEAR_EXPLOSION.get(),
                        SoundSource.BLOCKS, 100.0F, 1.0F, false);
            }
        }
    }

    private void spawnRingCloudlets() {
        if (age >= 195) return;
        int lifetime = (int) (Math.min(age * age + 200, maxAge() - age + 200) * 1.5D);
        for (int i = 0; i < 2; i++) {
            Cloudlet cloudlet = new Cloudlet(getX(), getY() + coreHeight, getZ(),
                    random.nextFloat() * Mth.TWO_PI, lifetime, TorexType.RING);
            cloudlet.setScale(1.0F + age * 0.005625F, 6.75F);
            cloudlets.add(cloudlet);
        }
    }

    private void spawnCondensationCloudlets() {
        if (age > 195 && age < 900) {
            for (int i = 0; i < 8; i++) for (int j = 0; j < 4; j++) {
                float angle = random.nextFloat() * Mth.TWO_PI;
                double radial = torusWidth + rollerSize * (5.0D + random.nextDouble());
                double tilt = Math.PI / 45.0D * j;
                double horizontal = radial * Math.cos(tilt);
                double vertical = radial * Math.sin(tilt);
                int lifetime = (int) ((20 + age / 10) * (1.0D + random.nextDouble() * 0.1D));
                Cloudlet cloudlet = new Cloudlet(
                        getX() + Math.cos(angle) * horizontal,
                        getY() + coreHeight - 5.0D + j * 1.5D + vertical,
                        getZ() + Math.sin(angle) * horizontal,
                        angle, lifetime, TorexType.CONDENSATION
                );
                cloudlet.setScale(0.24F, 5.2F);
                cloudlets.add(cloudlet);
            }
        }
        if (age > 300 && age < 900) {
            for (int i = 0; i < 8; i++) for (int j = 0; j < 4; j++) {
                float angle = random.nextFloat() * Mth.TWO_PI;
                double radial = torusWidth + rollerSize * (3.0D + random.nextDouble() * 0.5D);
                double tilt = Math.PI / 45.0D * j;
                double horizontal = radial * Math.cos(tilt);
                double vertical = radial * Math.sin(tilt);
                int lifetime = (int) ((20 + age / 10) * (1.0D + random.nextDouble() * 0.1D));
                Cloudlet cloudlet = new Cloudlet(
                        getX() + Math.cos(angle) * horizontal,
                        getY() + coreHeight + 25.0D + j * 1.5D + vertical,
                        getZ() + Math.sin(angle) * horizontal,
                        angle, lifetime, TorexType.CONDENSATION
                );
                cloudlet.setScale(0.24F, 5.2F);
                cloudlets.add(cloudlet);
            }
        }
    }

    private double simulationSpeed() {
        int slow = maxAge() / 4;
        int stop = maxAge() / 2;
        if (age > stop) return 0.0D;
        if (age > slow) return 1.0D - (double) (age - slow) / (stop - slow);
        return 1.0D;
    }

    private double greying() {
        int start = maxAge() * 3 / 4;
        return age > start ? 1.0D + (double) (age - start) / (maxAge() - start) : 1.0D;
    }

    private float globalAlpha() {
        int start = maxAge() * 3 / 4;
        if (age <= start) return 1.0F;
        return 1.0F - (float) (age - start) / (maxAge() - start);
    }

    public enum TorexType { STANDARD, SHOCK, RING, CONDENSATION }

    public final class Cloudlet {
        private double x;
        private double y;
        private double z;
        private double previousX;
        private double previousY;
        private double previousZ;
        private double motionX;
        private double motionY;
        private double motionZ;
        private int cloudAge;
        private final int lifetime;
        private final float angle;
        private final float rangeMod;
        private final float colorMod;
        private Vec3 color;
        private Vec3 previousColor;
        private final TorexType type;
        private boolean dead;
        private float startingScale = 1.0F;
        private float growingScale = 5.0F;
        private double motionMultiplier = 1.0D;

        private Cloudlet(double x, double y, double z, float angle, int lifetime, TorexType type) {
            this.x = this.previousX = x;
            this.y = this.previousY = y;
            this.z = this.previousZ = z;
            this.angle = angle;
            this.lifetime = Math.max(1, lifetime);
            this.rangeMod = 0.3F + random.nextFloat() * 0.7F;
            this.colorMod = 0.8F + random.nextFloat() * 0.2F;
            this.type = type;
            updateColor();
            previousColor = color;
        }

        private void update() {
            if (++cloudAge > lifetime) dead = true;
            previousX = x;
            previousY = y;
            previousZ = z;

            double radialDistance = Math.hypot(getX() - x, getZ() - z);
            double simulatedX = getX() + radialDistance;
            if (type == TorexType.STANDARD) {
                Vec3 convection = convectionMotion(simulatedX);
                Vec3 lift = liftMotion(simulatedX);
                double factor = Mth.clamp((y - getY()) / coreHeight, 0.0D, 1.0D);
                motionX = convection.x * factor + lift.x * (1.0D - factor);
                motionY = convection.y * factor + lift.y * (1.0D - factor);
                motionZ = convection.z * factor + lift.z * (1.0D - factor);
            } else if (type == TorexType.SHOCK) {
                double factor = Mth.clamp((y - getY()) / coreHeight, 0.0D, 1.0D);
                motionX = Math.cos(angle) * factor;
                motionY = 0.0D;
                motionZ = Math.sin(angle) * factor;
            } else if (type == TorexType.RING) {
                Vec3 motion = ringMotion(simulatedX);
                motionX = motion.x;
                motionY = motion.y;
                motionZ = motion.z;
            } else {
                double speed = 0.00002D * age;
                motionX = (x - getX()) * speed;
                motionY = 0.0D;
                motionZ = (z - getZ()) * speed;
            }

            double multiplier = motionMultiplier * simulationSpeed();
            x += motionX * multiplier;
            y += motionY * multiplier;
            z += motionZ * multiplier;
            updateColor();
        }

        private Vec3 convectionMotion(double simulatedX) {
            Vec3 torus = new Vec3(getX() + torusWidth, getY() + coreHeight, getZ());
            Vec3 delta = torus.subtract(simulatedX, y, getZ());
            double roller = rollerSize * rangeMod;
            double distance = delta.length() / roller - 1.0D;
            double function = 1.0D - Math.exp(-distance);
            double rotation = function * Math.PI * 0.5D;
            Vec3 perimeter = rotateZ(delta.scale(-1.0D / safe(distance)), rotation);
            Vec3 motion = torus.add(perimeter).subtract(simulatedX, y, getZ()).normalize();
            return rotateY(motion, angle);
        }

        private Vec3 ringMotion(double simulatedX) {
            if (simulatedX > getX() + torusWidth * 2.0D) return Vec3.ZERO;
            Vec3 torus = new Vec3(getX() + torusWidth, getY() + coreHeight * 0.5D, getZ());
            Vec3 delta = torus.subtract(simulatedX, y, getZ());
            double roller = rollerSize * rangeMod * 0.25D;
            double distance = delta.length() / roller - 1.0D;
            double function = 1.0D - Math.exp(-distance);
            double rotation = function * Math.PI * 0.5D;
            Vec3 perimeter = rotateZ(delta.scale(-1.0D / safe(distance)), rotation);
            Vec3 motion = torus.add(perimeter).subtract(simulatedX, y, getZ()).scale(0.001D).normalize();
            return rotateY(motion, angle);
        }

        private Vec3 liftMotion(double simulatedX) {
            double factor = Mth.clamp(1.0D - (simulatedX - (getX() + torusWidth)), 0.0D, 1.0D);
            return new Vec3(getX() - x, getY() + convectionHeight - y, getZ() - z).normalize().scale(factor);
        }

        private void updateColor() {
            previousColor = color;
            double dx = getX() - x;
            double dy = getY() + coreHeight - y;
            double dz = getZ() - z;
            double distance = Math.sqrt((dx * dx + dy * dy + dz * dz) / Math.max(heat, 0.0001D));
            double brightness = 2.0D / Math.max(distance, 1.0D);
            if (cloudType() == 1) {
                // Source EntityNukeTorex type 1: green-forward balefire tint.
                color = new Vec3(Math.max(brightness * 1.0D, 0.25D),
                        Math.max(brightness * 2.0D, 0.25D),
                        Math.max(brightness * 0.5D, 0.25D));
            } else {
                color = new Vec3(Math.max(brightness * 2.0D, 0.25D),
                        Math.max(brightness * 1.5D, 0.25D),
                        Math.max(brightness * 0.5D, 0.25D));
            }
            if (previousColor == null) previousColor = color;
        }

        private static double safe(double value) {
            return Math.abs(value) < 1.0E-6D ? Math.copySign(1.0E-6D, value == 0.0D ? 1.0D : value) : value;
        }

        private static Vec3 rotateY(Vec3 vector, double angle) {
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            return new Vec3(vector.x * cos + vector.z * sin, vector.y,
                    vector.z * cos - vector.x * sin);
        }

        private static Vec3 rotateZ(Vec3 vector, double angle) {
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            return new Vec3(vector.x * cos - vector.y * sin,
                    vector.y * cos + vector.x * sin, vector.z);
        }

        private Cloudlet setScale(float start, float growth) {
            startingScale = start;
            growingScale = growth;
            return this;
        }

        private Cloudlet setMotion(double multiplier) {
            motionMultiplier = multiplier;
            return this;
        }

        public Vec3 interpolatedPosition(float partialTick) {
            Vec3 base = new Vec3(Mth.lerp(partialTick, previousX, x), Mth.lerp(partialTick, previousY, y),
                    Mth.lerp(partialTick, previousZ, z));
            if (type == TorexType.SHOCK) return base;
            float scale = cloudScale();
            return new Vec3((base.x - getX()) * scale + getX(),
                    (base.y - getY()) * scale + getY(),
                    (base.z - getZ()) * scale + getZ());
        }

        public Vec3 interpolatedColor(float partialTick) {
            if (type == TorexType.CONDENSATION) return new Vec3(1.0D, 1.0D, 1.0D);
            double grey = greying() + (type == TorexType.RING ? 1.0D : 0.0D);
            return new Vec3(Mth.lerp(partialTick, previousColor.x, color.x) * grey,
                    Mth.lerp(partialTick, previousColor.y, color.y) * grey,
                    Mth.lerp(partialTick, previousColor.z, color.z) * grey);
        }

        public float alpha() {
            float alpha = (1.0F - (float) cloudAge / lifetime) * globalAlpha();
            return type == TorexType.CONDENSATION ? alpha * 0.25F : alpha;
        }

        public float renderScale() {
            float value = startingScale + (float) cloudAge / lifetime * growingScale;
            return type == TorexType.SHOCK ? value : value * cloudScale();
        }

        public float renderRotation(float partialTick) {
            return angle + (cloudAge + partialTick) * (type == TorexType.SHOCK ? 0.003F : 0.011F);
        }

        public TorexType type() { return type; }
        public float colorMod() { return colorMod; }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        private boolean dead() { return dead; }
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) { discard(); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return true; }
}
