package com.hbm.ntm.item;

import com.hbm.ntm.entity.FollyBeamEntity;
import com.hbm.ntm.entity.FollyNukeProjectileEntity;
import com.hbm.ntm.network.GunEffectPayload;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.weapon.FollyAmmoType;
import com.hbm.ntm.weapon.GunInput;
import com.hbm.ntm.weapon.SecretAmmoTypes;
import com.hbm.ntm.weapon.SednaCrosshair;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class FollyItem extends SednaGunItem {
    public static final int DRAW_TICKS = 40;
    public static final int FIRE_DELAY = 26;
    public static final int RELOAD_TICKS = 160;
    public static final int CAPACITY = 1;
    public static final int SPINUP_TICKS = 100;
    public static final float BASE_DAMAGE = 1_000.0F;

    private static final String INITIALIZED = "hbm_initialized";
    private static final String STATE = "state_0";
    private static final String TIMER = "timer_0";
    private static final String MAG_COUNT = "magcount0";
    private static final String MAG_TYPE = "magtype0";
    private static final String MAG_PREV = "magprev0";
    private static final String AIMING = "aiming";
    private static final String PRIMARY_HELD = "primary0";
    private static final String CANCEL_RELOAD = "cancel";
    private static final String EQUIPPED = "eqipped";
    private static final String LAST_ANIM = "lastanim_0";
    private static final String ANIM_TIMER = "animtimer_0";

    @Override
    protected void handleGunInput(Player player, ItemStack stack, GunInput input) {
        switch (input) {
            case PRIMARY -> pressPrimary(player, stack);
            case PRIMARY_RELEASE -> releasePrimary(stack);
            case RELOAD -> pressReload(player, stack);
            case TOGGLE_AIM -> toggleAim(stack);
            default -> { }
        }
    }

    @Override public boolean gunAiming(ItemStack stack) { return aiming(stack); }
    @Override public SednaCrosshair gunCrosshair() { return SednaCrosshair.NONE; }
    @Override public float gunAimFovMultiplier() { return 0.67F; }
    @Override public int gunRounds(ItemStack stack) { return rounds(stack); }
    @Override public int gunCapacity() { return CAPACITY; }
    @Override public float gunWear(ItemStack stack) { return 0.0F; }
    @Override public float gunDurability() { return 0.0F; }
    @Override public boolean gunShowDurability() { return false; }
    @Override public ItemStack gunAmmoIcon(ItemStack stack) {
        return loadedAmmo(stack).createStack(ModItems.AMMO_SECRET.get(), 1);
    }
    @Override public float recoilVertical() { return 25.0F; }
    @Override public float recoilHorizontalSigma() { return 1.5F; }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(entity instanceof LivingEntity living) || level.isClientSide) return;
        boolean held = selected && living.getMainHandItem() == stack;
        CompoundTag tag = data(stack);
        GunState previous = state(tag);
        if (!held) {
            setState(tag, GunState.DRAWING);
            tag.putInt(TIMER, DRAW_TICKS);
            tag.putInt(LAST_ANIM, GunAnimation.CYCLE.ordinal());
            tag.putBoolean(AIMING, false);
            tag.putBoolean(PRIMARY_HELD, false);
            tag.putBoolean(CANCEL_RELOAD, false);
            tag.putBoolean(EQUIPPED, false);
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
        save(stack, tag);
    }

    private static void decide(LivingEntity living, CompoundTag tag, GunState previous) {
        if (previous == GunState.DRAWING || previous == GunState.COOLDOWN) {
            setState(tag, GunState.IDLE);
            tag.putInt(TIMER, 0);
            return;
        }
        if (previous != GunState.RELOADING || !(living instanceof Player player)) return;
        if (!tag.getBoolean(CANCEL_RELOAD)) reloadOne(player, tag);
        tag.putBoolean(CANCEL_RELOAD, false);
        setState(tag, GunState.IDLE);
        tag.putInt(TIMER, 0);
    }

    private static void pressPrimary(Player player, ItemStack stack) {
        CompoundTag tag = data(stack);
        tag.putBoolean(PRIMARY_HELD, true);
        if (state(tag) == GunState.RELOADING) {
            tag.putBoolean(CANCEL_RELOAD, true);
            save(stack, tag);
            return;
        }
        if (canFire(tag)) fire(player, tag);
        save(stack, tag);
    }

    private static void releasePrimary(ItemStack stack) {
        CompoundTag tag = data(stack);
        tag.putBoolean(PRIMARY_HELD, false);
        save(stack, tag);
    }

    private static boolean canFire(CompoundTag tag) {
        return state(tag) == GunState.IDLE
                && tag.getBoolean(AIMING)
                && animation(tag) == GunAnimation.SPINUP
                && tag.getInt(ANIM_TIMER) >= SPINUP_TICKS
                && rounds(tag) > 0;
    }

    private static void fire(Player player, CompoundTag tag) {
        if (!(player.level() instanceof ServerLevel level)) return;
        FollyAmmoType ammo = FollyAmmoType.fromLegacyMetadata(tag.getInt(MAG_TYPE));
        boolean aiming = tag.getBoolean(AIMING);
        Vec3 origin = projectileOrigin(player, aiming);
        Vec3 heading = player.getLookAngle();
        if (ammo == FollyAmmoType.SILVER_BULLET) {
            level.addFreshEntity(new FollyBeamEntity(level, player, BASE_DAMAGE, origin, heading));
        } else {
            level.addFreshEntity(new FollyNukeProjectileEntity(level, player, BASE_DAMAGE, origin, heading));
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.GUN_FOLLY_FIRE.get(), SoundSource.PLAYERS, 100.0F, 1.0F);
        if (player instanceof ServerPlayer serverPlayer
                && serverPlayer.connection.getConnection().isConnected()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    GunEffectPayload.fired(player.getId(), origin, heading, false));
        }
        tag.putInt(MAG_COUNT, 0);
        setState(tag, GunState.COOLDOWN);
        tag.putInt(TIMER, FIRE_DELAY);
        playAnimation(tag, GunAnimation.CYCLE);
    }

    private static void pressReload(Player player, ItemStack stack) {
        CompoundTag tag = data(stack);
        if (state(tag) != GunState.IDLE) return;
        FollyAmmoType available = findAmmo(player.getInventory(), rounds(tag) > 0 ? loadedAmmo(tag) : null);
        if (rounds(tag) < CAPACITY && available != null) {
            tag.putBoolean(AIMING, false);
            tag.putInt(MAG_PREV, rounds(tag));
            tag.putInt(MAG_TYPE, available.legacyMetadata());
            setState(tag, GunState.RELOADING);
            tag.putInt(TIMER, RELOAD_TICKS);
            playAnimation(tag, GunAnimation.RELOAD);
            save(stack, tag);
        }
    }

    private static void toggleAim(ItemStack stack) {
        CompoundTag tag = data(stack);
        if (state(tag) != GunState.IDLE) return;
        boolean aiming = tag.getBoolean(AIMING);
        tag.putBoolean(AIMING, !aiming);
        if (!aiming) playAnimation(tag, GunAnimation.SPINUP);
        save(stack, tag);
    }

    private static void reloadOne(Player player, CompoundTag tag) {
        if (rounds(tag) >= CAPACITY) return;
        FollyAmmoType required = loadedAmmo(tag);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (!candidate.is(ModItems.AMMO_SECRET.get())
                    || SecretAmmoTypes.fromStack(candidate) != required) continue;
            candidate.shrink(1);
            tag.putInt(MAG_COUNT, 1);
            return;
        }
    }

    private static FollyAmmoType findAmmo(Inventory inventory, FollyAmmoType required) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (candidate.isEmpty() || !candidate.is(ModItems.AMMO_SECRET.get())) continue;
            if (!(SecretAmmoTypes.fromStack(candidate) instanceof FollyAmmoType type)) continue;
            if (required == null || type == required) return type;
        }
        return null;
    }

    private static Vec3 projectileOrigin(Player player, boolean aiming) {
        Vec3 local = new Vec3(aiming ? -0.125D : -0.1875D, -0.0625D, 0.75D);
        return player.getEyePosition().add(local
                .xRot(-player.getXRot() * Mth.DEG_TO_RAD)
                .yRot(-player.getYRot() * Mth.DEG_TO_RAD));
    }

    private static void playOrchestra(Level level, LivingEntity entity, GunAnimation animation,
                                      int timer) {
        if (animation == GunAnimation.RELOAD) {
            if (timer == 20 || timer == 120) play(level, entity, ModSounds.GUN_FOLLY_SCREW.get(), 1.0F);
            if (timer == 80) play(level, entity, ModSounds.GUN_FOLLY_INSERT.get(), 1.0F);
        }
    }

    private static void play(Level level, LivingEntity entity, SoundEvent sound, float volume) {
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound,
                SoundSource.PLAYERS, volume, 1.0F);
    }

    public static int rounds(ItemStack stack) { return rounds(data(stack)); }
    private static int rounds(CompoundTag tag) { return Mth.clamp(tag.getInt(MAG_COUNT), 0, CAPACITY); }
    public static boolean aiming(ItemStack stack) { return data(stack).getBoolean(AIMING); }
    public static GunState state(ItemStack stack) { return state(data(stack)); }
    public static int timer(ItemStack stack) { return data(stack).getInt(TIMER); }
    public static GunAnimation animation(ItemStack stack) { return animation(data(stack)); }
    public static int animationTimer(ItemStack stack) { return data(stack).getInt(ANIM_TIMER); }
    public static int amountBeforeReload(ItemStack stack) { return data(stack).getInt(MAG_PREV); }
    public static FollyAmmoType loadedAmmo(ItemStack stack) { return loadedAmmo(data(stack)); }
    private static FollyAmmoType loadedAmmo(CompoundTag tag) {
        return FollyAmmoType.fromLegacyMetadata(tag.getInt(MAG_TYPE));
    }

    public static void setTestState(ItemStack stack, GunState state, int timer, int rounds,
                                    FollyAmmoType ammo, boolean aiming, GunAnimation animation,
                                    int animationTimer) {
        CompoundTag tag = data(stack);
        setState(tag, state);
        tag.putInt(TIMER, timer);
        tag.putInt(MAG_COUNT, Mth.clamp(rounds, 0, CAPACITY));
        tag.putInt(MAG_TYPE, ammo.legacyMetadata());
        tag.putBoolean(AIMING, aiming);
        tag.putBoolean(EQUIPPED, true);
        tag.putInt(LAST_ANIM, animation.ordinal());
        tag.putInt(ANIM_TIMER, animationTimer);
        save(stack, tag);
    }

    private static GunState state(CompoundTag tag) {
        int ordinal = tag.getByte(STATE);
        return ordinal >= 0 && ordinal < GunState.values().length
                ? GunState.values()[ordinal] : GunState.DRAWING;
    }

    private static void setState(CompoundTag tag, GunState state) { tag.putByte(STATE, (byte) state.ordinal()); }

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
        if (!tag.getBoolean(INITIALIZED)) {
            tag.putBoolean(INITIALIZED, true);
            tag.putInt(MAG_TYPE, FollyAmmoType.SILVER_BULLET.legacyMetadata());
            tag.putInt(MAG_COUNT, 0);
        }
        return tag;
    }

    private static void save(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        FollyAmmoType ammo = loadedAmmo(stack);
        tooltip.add(Component.translatable("gui.weapon.ammo").append(": ")
                .append(Component.translatable("item.hbm.ammo_secret." + ammo.serializedName()))
                .append(" " + rounds(stack) + " / " + CAPACITY).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.baseDamage")
                .append(": " + (int) BASE_DAMAGE).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.quality.secret").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    public enum GunState { DRAWING, IDLE, COOLDOWN, RELOADING }
    public enum GunAnimation { EQUIP, CYCLE, RELOAD, SPINUP }
}
