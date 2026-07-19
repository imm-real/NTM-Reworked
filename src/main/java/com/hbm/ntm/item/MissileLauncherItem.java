package com.hbm.ntm.item;

import com.hbm.ntm.entity.RocketProjectileEntity;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.weapon.RocketAmmoType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Source Missile Launcher receiver with its immediate aimed target acquisition. */
public final class MissileLauncherItem extends RocketLauncherItem {
    public static final int DURABILITY = 500;
    public static final int DRAW_TICKS = 20;
    public static final int INSPECT_TICKS = 40;
    public static final int FIRE_DELAY = 5;
    public static final int RELOAD_TICKS = 48;
    public static final int JAM_TICKS = 33;
    public static final int CAPACITY = 1;
    public static final float BASE_DAMAGE = 50.0F;
    public static final double LOCK_DISTANCE = 150.0D;
    public static final double LOCK_ANGLE = 20.0D;

    @Override protected int receiverDurability() { return DURABILITY; }
    @Override protected int receiverDrawTicks() { return DRAW_TICKS; }
    @Override protected int receiverFireDelay() { return FIRE_DELAY; }
    @Override protected int receiverReloadTicks() { return RELOAD_TICKS; }
    @Override protected int receiverJamTicks() { return JAM_TICKS; }
    @Override protected float receiverBaseDamage() { return BASE_DAMAGE; }
    @Override protected RocketAmmoType receiverDefaultAmmo() { return RocketAmmoType.SHAPED_CHARGE; }
    @Override protected RocketProjectileEntity.FlightMode receiverFlightMode() {
        return RocketProjectileEntity.FlightMode.MISSILE_LAUNCHER;
    }
    @Override protected Entity acquireShotTarget(Player player, boolean aiming) {
        return aiming ? StingerLauncherItem.findLockTarget(player, LOCK_DISTANCE, LOCK_ANGLE) : null;
    }

    @Override public boolean gunHideCrosshairWhenAimed() { return false; }
    @Override public float gunAimFovMultiplier() { return 1.0F; }

    @Override
    protected void playOrchestra(Level level, LivingEntity entity,
                                 GunAnimation animation, int timer) {
        if (animation == GunAnimation.RELOAD) {
            if (timer == 0) play(level, entity, ModSounds.GUN_BOLT_OPEN.get(), 0.9F);
            if (timer == 30) play(level, entity, ModSounds.GUN_CANISTER_INSERT.get(), 1.0F);
            if (timer == 42) play(level, entity, ModSounds.GUN_BOLT_CLOSE.get(), 0.9F);
        } else if (animation == GunAnimation.JAMMED || animation == GunAnimation.INSPECT) {
            if (timer == 0) play(level, entity, ModSounds.GUN_BOLT_OPEN.get(), 0.9F);
            if (timer == 27) play(level, entity, ModSounds.GUN_BOLT_CLOSE.get(), 0.9F);
        }
    }

    private static void play(Level level, LivingEntity entity,
                             net.minecraft.sounds.SoundEvent sound, float pitch) {
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, pitch);
    }

    public static int rounds(ItemStack stack) { return RocketLauncherItem.rounds(stack); }
    public static float wear(ItemStack stack) { return RocketLauncherItem.wear(stack); }
    public static boolean aiming(ItemStack stack) { return RocketLauncherItem.aiming(stack); }
    public static GunState state(ItemStack stack) { return RocketLauncherItem.state(stack); }
    public static int timer(ItemStack stack) { return RocketLauncherItem.timer(stack); }
    public static GunAnimation animation(ItemStack stack) { return RocketLauncherItem.animation(stack); }
    public static int animationTimer(ItemStack stack) {
        return RocketLauncherItem.animationTimer(stack);
    }
    public static RocketAmmoType loadedAmmo(ItemStack stack) {
        return RocketLauncherItem.loadedAmmo(stack);
    }
}
