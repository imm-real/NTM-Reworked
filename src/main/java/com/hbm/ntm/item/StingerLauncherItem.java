package com.hbm.ntm.item;

import com.hbm.ntm.entity.RocketProjectileEntity;
import com.hbm.ntm.network.GunEffectPayload;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.weapon.GunInput;
import com.hbm.ntm.weapon.RocketAmmoType;
import com.hbm.ntm.weapon.SednaCrosshair;
import com.hbm.ntm.weapon.StandardAmmoTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Source FIM-92 Stinger: the Panzerschreck receiver with its target-lock requirement. */
public final class StingerLauncherItem extends SednaGunItem {
    private static final String INITIALIZED = "hbm_initialized";
    private static final String STATE = "state_0";
    private static final String TIMER = "timer_0";
    private static final String WEAR = "wear_0";
    private static final String MAG_COUNT = "magcount0";
    private static final String MAG_TYPE = "magtype0";
    private static final String MAG_PREV = "magprev0";
    private static final String AIMING = "aiming";
    private static final String PRIMARY_HELD = "primary0";
    private static final String CANCEL_RELOAD = "cancel";
    private static final String EQUIPPED = "eqipped";
    private static final String LAST_ANIM = "lastanim_0";
    private static final String ANIM_TIMER = "animtimer_0";
    private static final String LOCKING_ON = "lockingon";
    private static final String LOCK_PROGRESS = "lockonprogress";
    private static final String LOCK_TARGET = "lockontarget";
    private static final String LOCKED_ON = "lockedon";

    public static final int DURABILITY = 300;
    public static final int DRAW_TICKS = 7;
    public static final int INSPECT_TICKS = 40;
    public static final int FIRE_DELAY = 5;
    public static final int RELOAD_TICKS = 50;
    public static final int JAM_TICKS = 40;
    public static final int CAPACITY = 1;
    public static final float BASE_DAMAGE = 35.0F;
    public static final int LOCK_TICKS = 60;
    public static final double LOCK_DISTANCE = 150.0D;
    public static final double LOCK_ANGLE = 10.0D;

    @Override
    protected void handleGunInput(Player player, ItemStack stack, GunInput input) {
        switch (input) {
            case PRIMARY -> pressPrimary(player, stack);
            case PRIMARY_RELEASE -> releasePrimary(stack);
            case SECONDARY -> setLockingOn(stack, true);
            case SECONDARY_RELEASE -> setLockingOn(stack, false);
            case RELOAD -> pressReload(player, stack);
            case TOGGLE_AIM -> toggleAim(stack);
        }
    }

    @Override public boolean gunAiming(ItemStack stack) { return aiming(stack); }
    @Override public boolean gunSecondaryAutomatic() { return true; }
    @Override public SednaCrosshair gunCrosshair() { return SednaCrosshair.L_BOX_OUTLINE; }
    @Override public boolean gunHideCrosshairWhenAimed() { return false; }
    @Override public boolean gunCrosshairOnlyWhenAimed() { return true; }
    @Override public float gunAimFovMultiplier() { return 0.5F; }
    @Override public int gunRounds(ItemStack stack) { return rounds(stack); }
    @Override public int gunCapacity() { return CAPACITY; }
    @Override public float gunWear(ItemStack stack) { return wear(stack); }
    @Override public float gunDurability() { return DURABILITY; }
    @Override public ItemStack gunAmmoIcon(ItemStack stack) {
        return loadedAmmo(stack).createStack(ModItems.AMMO_STANDARD.get(), 1);
    }
    @Override public float recoilVertical() { return 0.0F; }
    @Override public float recoilHorizontalSigma() { return 0.0F; }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(entity instanceof LivingEntity living) || level.isClientSide) return;
        boolean held = selected && living.getMainHandItem() == stack;
        CompoundTag tag = data(stack);
        GunState previous = state(tag);

        if (!held) {
            if (previous != GunState.JAMMED) {
                setState(tag, GunState.DRAWING);
                tag.putInt(TIMER, DRAW_TICKS);
            }
            tag.putInt(LAST_ANIM, GunAnimation.CYCLE.ordinal());
            tag.putBoolean(AIMING, false);
            tag.putBoolean(PRIMARY_HELD, false);
            tag.putBoolean(CANCEL_RELOAD, false);
            tag.putBoolean(EQUIPPED, false);
            tag.putBoolean(LOCKING_ON, false);
            resetLock(tag);
            save(stack, tag);
            return;
        }

        if (!tag.getBoolean(EQUIPPED)) playAnimation(tag, GunAnimation.EQUIP);
        tag.putBoolean(EQUIPPED, true);
        int animationTimer = tag.getInt(ANIM_TIMER);
        playOrchestra(level, living, animation(tag), animationTimer);
        tag.putInt(ANIM_TIMER, animationTimer + 1);

        int timer = tag.getInt(TIMER);
        if (timer > 0) tag.putInt(TIMER, timer - 1);
        if (timer <= 1) decide(living, tag, previous);
        if (living instanceof Player player) updateLock(player, tag);
        save(stack, tag);
    }

    private static void decide(LivingEntity living, CompoundTag tag, GunState previous) {
        if (previous == GunState.DRAWING || previous == GunState.JAMMED
                || previous == GunState.COOLDOWN) {
            setState(tag, GunState.IDLE);
            tag.putInt(TIMER, 0);
            return;
        }
        if (previous != GunState.RELOADING || !(living instanceof Player player)) return;

        if (!tag.getBoolean(CANCEL_RELOAD)) reloadOne(player, tag);
        tag.putBoolean(CANCEL_RELOAD, false);
        if (RocketLauncherItem.jamChance(tag.getFloat(WEAR), DURABILITY)
                > living.getRandom().nextFloat()) {
            setState(tag, GunState.JAMMED);
            tag.putInt(TIMER, JAM_TICKS);
            playAnimation(tag, GunAnimation.JAMMED);
        } else {
            setState(tag, GunState.IDLE);
            tag.putInt(TIMER, 0);
        }
    }

    private static void updateLock(Player player, CompoundTag tag) {
        if (!tag.getBoolean(LOCKING_ON) || !tag.getBoolean(AIMING) || rounds(tag) <= 0) {
            resetLock(tag);
            return;
        }

        Entity target = findLockTarget(player, LOCK_DISTANCE, LOCK_ANGLE);
        boolean locked = tag.getBoolean(LOCKED_ON);
        if (target == null) {
            if (!locked) resetLock(tag);
            return;
        }

        int previousTarget = tag.getInt(LOCK_TARGET);
        if (!locked && target.getId() != previousTarget) {
            resetLock(tag);
            tag.putInt(LOCK_TARGET, target.getId());
        }
        int progress = tag.getInt(LOCK_PROGRESS) + 1;
        tag.putInt(LOCK_PROGRESS, progress);
        if (progress >= LOCK_TICKS && !locked) {
            play(player.level(), player, ModSounds.TECH_BLEEP.get());
            tag.putBoolean(LOCKED_ON, true);
        }
    }

    /** Source chooses the collidable entity with the smallest angular error, not the nearest entity. */
    public static Entity findLockTarget(Player player, double distance, double angleThreshold) {
        if (player == null) return null;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        double coneRadius = Math.sin(angleThreshold * Mth.DEG_TO_RAD) * distance;
        AABB search = new AABB(eye, eye.add(look.scale(distance)))
                .inflate(coneRadius, 10.0D, coneRadius);
        Entity closest = null;
        double closestAngle = 360.0D;

        for (Entity candidate : player.level().getEntities(player, search, entity ->
                entity.isAlive() && !entity.isSpectator() && entity.isPickable()
                        && entity.getBbHeight() >= 0.5F)) {
            Vec3 delta = new Vec3(candidate.getX() - eye.x,
                    candidate.getY() + candidate.getBbHeight() * 0.5D - eye.y,
                    candidate.getZ() - eye.z);
            double length = delta.length();
            if (length <= 1.0E-8D || length > distance) continue;
            double cosine = Mth.clamp(delta.dot(look) / length, -1.0D, 1.0D);
            double angle = Math.abs(Math.acos(cosine) * Mth.RAD_TO_DEG);
            if (angle < closestAngle && angle < angleThreshold) {
                closestAngle = angle;
                closest = candidate;
            }
        }
        return closest;
    }

    private void pressPrimary(Player player, ItemStack stack) {
        CompoundTag tag = data(stack);
        tag.putBoolean(PRIMARY_HELD, true);
        GunState current = state(tag);
        if (current == GunState.RELOADING) {
            tag.putBoolean(CANCEL_RELOAD, true);
            save(stack, tag);
            return;
        }
        if (current != GunState.IDLE) {
            save(stack, tag);
            return;
        }
        if (rounds(tag) <= 0 || !tag.getBoolean(LOCKED_ON)) {
            playAnimation(tag, GunAnimation.CYCLE_DRY);
            setState(tag, GunState.COOLDOWN);
            tag.putInt(TIMER, FIRE_DELAY);
            save(stack, tag);
            return;
        }
        fire(player, tag);
        save(stack, tag);
    }

    private static void releasePrimary(ItemStack stack) {
        CompoundTag tag = data(stack);
        tag.putBoolean(PRIMARY_HELD, false);
        save(stack, tag);
    }

    private static void setLockingOn(ItemStack stack, boolean value) {
        CompoundTag tag = data(stack);
        tag.putBoolean(LOCKING_ON, value);
        save(stack, tag);
    }

    private void fire(Player player, CompoundTag tag) {
        int loaded = rounds(tag);
        if (loaded <= 0 || !(player.level() instanceof ServerLevel level)) return;

        RocketAmmoType ammo = RocketAmmoType.fromLegacyMetadata(tag.getInt(MAG_TYPE));
        float currentWear = Mth.clamp(tag.getFloat(WEAR), 0.0F, DURABILITY);
        float damage = BASE_DAMAGE
                * RocketLauncherItem.wearDamageMultiplier(currentWear, DURABILITY)
                * ammo.damageMultiplier();
        float spread = (tag.getBoolean(AIMING) ? 0.0F : 0.025F)
                + RocketLauncherItem.wearSpread(currentWear, DURABILITY) * 0.125F;
        Vec3 origin = projectileOrigin(player, tag.getBoolean(AIMING));
        Vec3 heading = player.getLookAngle();
        Entity target = level.getEntity(tag.getInt(LOCK_TARGET));
        level.addFreshEntity(new RocketProjectileEntity(level, player, ammo, damage, spread,
                origin, heading, target));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.GUN_ROCKET_FIRE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (player instanceof ServerPlayer serverPlayer
                && serverPlayer.connection.getConnection().isConnected()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    GunEffectPayload.fired(player.getId(), origin, heading, false));
        }

        tag.putInt(MAG_COUNT, loaded - 1);
        tag.putFloat(WEAR, Math.min(currentWear + ammo.wear(), DURABILITY));
        setState(tag, GunState.COOLDOWN);
        tag.putInt(TIMER, FIRE_DELAY);
        playAnimation(tag, GunAnimation.CYCLE);
    }

    private void pressReload(Player player, ItemStack stack) {
        CompoundTag tag = data(stack);
        if (state(tag) != GunState.IDLE) return;
        tag.putBoolean(AIMING, false);
        resetLock(tag);
        RocketAmmoType available = findAmmo(player.getInventory());
        if (rounds(tag) < CAPACITY && available != null) {
            tag.putInt(MAG_PREV, rounds(tag));
            tag.putInt(MAG_TYPE, available.legacyMetadata());
            setState(tag, GunState.RELOADING);
            tag.putInt(TIMER, RELOAD_TICKS);
            playAnimation(tag, GunAnimation.RELOAD);
        } else {
            playAnimation(tag, GunAnimation.INSPECT);
        }
        save(stack, tag);
    }

    private static void toggleAim(ItemStack stack) {
        CompoundTag tag = data(stack);
        tag.putBoolean(AIMING, !tag.getBoolean(AIMING));
        save(stack, tag);
    }

    private static void reloadOne(Player player, CompoundTag tag) {
        if (rounds(tag) >= CAPACITY) return;
        RocketAmmoType required = RocketAmmoType.fromLegacyMetadata(tag.getInt(MAG_TYPE));
        if (!consumeAmmo(player.getInventory(), required)) return;
        tag.putInt(MAG_COUNT, 1);
    }

    private static RocketAmmoType findAmmo(Inventory inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (candidate.isEmpty() || !candidate.is(ModItems.AMMO_STANDARD.get())) continue;
            if (StandardAmmoTypes.fromStack(candidate) instanceof RocketAmmoType type) return type;
        }
        return null;
    }

    private static boolean consumeAmmo(Inventory inventory, RocketAmmoType required) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (candidate.isEmpty() || !candidate.is(ModItems.AMMO_STANDARD.get())) continue;
            if (StandardAmmoTypes.fromStack(candidate) != required) continue;
            candidate.shrink(1);
            return true;
        }
        return false;
    }

    private static Vec3 projectileOrigin(Player player, boolean aiming) {
        Vec3 local = new Vec3(aiming ? 0.0D : -0.1875D, -0.09375D, 1.0D);
        return player.getEyePosition().add(local
                .xRot(-player.getXRot() * Mth.DEG_TO_RAD)
                .yRot(-player.getYRot() * Mth.DEG_TO_RAD));
    }

    private static void playOrchestra(Level level, LivingEntity entity,
                                      GunAnimation animation, int timer) {
        if (animation == GunAnimation.RELOAD && timer == 30) {
            play(level, entity, ModSounds.GUN_CANISTER_INSERT.get());
        }
    }

    private static void play(Level level, LivingEntity entity, SoundEvent sound) {
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound,
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void resetLock(CompoundTag tag) {
        tag.putInt(LOCK_PROGRESS, 0);
        tag.putBoolean(LOCKED_ON, false);
    }

    public static int rounds(ItemStack stack) { return rounds(data(stack)); }
    private static int rounds(CompoundTag tag) {
        return Mth.clamp(tag.getInt(MAG_COUNT), 0, CAPACITY);
    }
    public static float wear(ItemStack stack) {
        return Mth.clamp(data(stack).getFloat(WEAR), 0.0F, DURABILITY);
    }
    public static boolean aiming(ItemStack stack) { return data(stack).getBoolean(AIMING); }
    public static boolean lockingOn(ItemStack stack) { return data(stack).getBoolean(LOCKING_ON); }
    public static boolean lockedOn(ItemStack stack) { return data(stack).getBoolean(LOCKED_ON); }
    public static int lockProgress(ItemStack stack) { return data(stack).getInt(LOCK_PROGRESS); }
    public static int lockTarget(ItemStack stack) { return data(stack).getInt(LOCK_TARGET); }
    public static GunState state(ItemStack stack) { return state(data(stack)); }
    public static int timer(ItemStack stack) { return data(stack).getInt(TIMER); }
    public static GunAnimation animation(ItemStack stack) { return animation(data(stack)); }
    public static int animationTimer(ItemStack stack) { return data(stack).getInt(ANIM_TIMER); }
    public static int amountBeforeReload(ItemStack stack) { return data(stack).getInt(MAG_PREV); }
    public static RocketAmmoType loadedAmmo(ItemStack stack) {
        return RocketAmmoType.fromLegacyMetadata(data(stack).getInt(MAG_TYPE));
    }

    public static void setTestState(ItemStack stack, GunState state, int timer, int rounds,
                                    RocketAmmoType ammo, float wear, boolean aiming,
                                    boolean locking, int progress, boolean locked, int target) {
        CompoundTag tag = data(stack);
        setState(tag, state);
        tag.putInt(TIMER, timer);
        tag.putInt(MAG_COUNT, Mth.clamp(rounds, 0, CAPACITY));
        tag.putInt(MAG_TYPE, ammo.legacyMetadata());
        tag.putFloat(WEAR, Mth.clamp(wear, 0.0F, DURABILITY));
        tag.putBoolean(AIMING, aiming);
        tag.putBoolean(LOCKING_ON, locking);
        tag.putInt(LOCK_PROGRESS, progress);
        tag.putBoolean(LOCKED_ON, locked);
        tag.putInt(LOCK_TARGET, target);
        tag.putBoolean(EQUIPPED, true);
        save(stack, tag);
    }

    private static GunState state(CompoundTag tag) {
        int ordinal = tag.getByte(STATE);
        return ordinal >= 0 && ordinal < GunState.values().length
                ? GunState.values()[ordinal] : GunState.DRAWING;
    }

    private static void setState(CompoundTag tag, GunState state) {
        tag.putByte(STATE, (byte) state.ordinal());
    }

    private static GunAnimation animation(CompoundTag tag) {
        int ordinal = tag.getInt(LAST_ANIM);
        return ordinal >= 0 && ordinal < GunAnimation.values().length
                ? GunAnimation.values()[ordinal] : GunAnimation.CYCLE;
    }

    private static void playAnimation(CompoundTag tag, GunAnimation animation) {
        tag.putInt(LAST_ANIM, animation.ordinal());
        tag.putInt(ANIM_TIMER, 0);
    }

    private static CompoundTag data(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.getBoolean(INITIALIZED) && stack.getItem() instanceof StingerLauncherItem) {
            tag.putBoolean(INITIALIZED, true);
            tag.putInt(MAG_TYPE, RocketAmmoType.SHAPED_CHARGE.legacyMetadata());
            tag.putInt(MAG_COUNT, 0);
            tag.putInt(LOCK_TARGET, -1);
        }
        return tag;
    }

    private static void save(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        RocketAmmoType ammo = loadedAmmo(stack);
        tooltip.add(Component.translatable("gui.weapon.ammo").append(": ")
                .append(Component.translatable("item.hbm.ammo_standard." + ammo.serializedName()))
                .append(" " + rounds(stack) + " / " + CAPACITY).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.baseDamage")
                .append(": 35").withStyle(ChatFormatting.GRAY));
        int condition = Mth.clamp((int) ((DURABILITY - wear(stack)) * 100.0F / DURABILITY), 0, 100);
        tooltip.add(Component.translatable("gui.weapon.condition").append(": " + condition + "%")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.quality.special").withStyle(ChatFormatting.AQUA));
    }

    public enum GunState { DRAWING, IDLE, COOLDOWN, RELOADING, JAMMED }
    public enum GunAnimation { EQUIP, CYCLE, CYCLE_DRY, RELOAD, JAMMED, INSPECT }
}
