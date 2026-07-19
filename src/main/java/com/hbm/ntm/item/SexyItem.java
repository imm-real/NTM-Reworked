package com.hbm.ntm.item;

import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.network.GunEffectPayload;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.weapon.GunInput;
import com.hbm.ntm.weapon.Shotgun12GaugeAmmoType;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import java.util.Locale;

/** Legendary Sexy. One hundred shells and a whiskey-based inspection system. */
public final class SexyItem extends SednaGunItem {
    public static final int DURABILITY = 5_000;
    public static final int DRAW_TICKS = 20;
    public static final int INSPECT_TICKS = 65;
    public static final int FIRE_DELAY = 4;      // delay(4): delayAfterFire
    public static final int DRY_DELAY = 4;       // delay(4): delayAfterDryFire
    public static final int RELOAD_TICKS = 110;  // reload(110) -> reloadBegin 110
    public static final int RELOAD_END_TICKS = 0; // reload(110) -> reloadEnd 0
    public static final int RELOAD_FILL_TICK = 55; // orchestra performs the real magazine fill here
    public static final int INSPECT_POTION_TICK = 60; // orchestra applies the whiskey buff here
    public static final int JAM_TICKS = 19;
    public static final int CAPACITY = 100;
    public static final float BASE_DAMAGE = 64.0F;
    public static final float SPREAD_INNATE = 0.0F;
    public static final float HIP_SPREAD = 0.025F;
    public static final float MAX_WEAR_SPREAD = 0.125F;
    /** Magnum label, still no free shells. */
    public static final Shotgun12GaugeAmmoType DEFAULT_AMMO = Shotgun12GaugeAmmoType.MAGNUM;

    // TODO Equestrian shell, cases, smoke ribbons, weapon mods and Red Room acquisition

    private static final String STATE = "state_0";
    private static final String TIMER = "timer_0";
    private static final String WEAR = "wear_0";
    private static final String MAG_COUNT = "magcount0";
    private static final String MAG_TYPE = "magtype0";
    private static final String MAG_PREV = "magprev0";
    private static final String MAG_AFTER = "magafter0";
    private static final String AIMING = "aiming";
    private static final String PRIMARY_HELD = "primary0";
    private static final String CANCEL_RELOAD = "cancel";
    private static final String EQUIPPED = "eqipped";
    private static final String LAST_ANIM = "lastanim_0";
    private static final String ANIM_TIMER = "animtimer_0";

    public SexyItem() { }

    public float baseDamage() { return BASE_DAMAGE; }

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
    @Override public boolean gunAutomatic() { return true; }
    // hideCrosshair(false): the L_CIRCLE reticle stays visible even while aiming (unusual).
    @Override public boolean gunHideCrosshairWhenAimed() { return false; }
    @Override public SednaCrosshair gunCrosshair() { return SednaCrosshair.L_CIRCLE; }
    @Override public int gunRounds(ItemStack stack) { return rounds(stack); }
    @Override public int gunCapacity() { return CAPACITY; }
    @Override public float gunWear(ItemStack stack) { return wear(stack); }
    @Override public float gunDurability() { return DURABILITY; }
    @Override public ItemStack gunAmmoIcon(ItemStack stack) {
        return loadedAmmo(stack).createStack(ModItems.AMMO_STANDARD.get(), 1);
    }
    // LAMBDA_RECOIL_SEXY: setupRecoil(gaussian*0.5, gaussian*0.5). Zero-mean gaussian on BOTH axes,
    // so the fixed vertical component is 0 and only the vertical sigma is non-zero (can kick down).
    @Override public float recoilVertical() { return 0.0F; }
    @Override public float recoilVerticalSigma() { return 0.5F; }
    @Override public float recoilHorizontalSigma() { return 0.5F; }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(entity instanceof LivingEntity living) || level.isClientSide) return;

        boolean held = selected && living.getMainHandItem() == stack;
        CompoundTag tag = data(stack);
        GunState previous = state(tag);

        if (!held) {
            if (previous != GunState.JAMMED) {
                tag.putByte(STATE, (byte) GunState.DRAWING.ordinal());
                tag.putInt(TIMER, DRAW_TICKS);
            }
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
        playOrchestra(level, living, tag, animation(tag), animationTimer);
        tag.putInt(ANIM_TIMER, animationTimer + 1);

        int timer = tag.getInt(TIMER);
        if (timer > 0) tag.putInt(TIMER, timer - 1);
        if (timer <= 1) decide(living, stack, tag, previous);
        save(stack, tag);
    }

    private static void decide(LivingEntity living, ItemStack stack, CompoundTag tag, GunState previous) {
        SexyItem gun = (SexyItem) stack.getItem();
        if (previous == GunState.DRAWING || previous == GunState.JAMMED) {
            tag.putByte(STATE, (byte) GunState.IDLE.ordinal());
            tag.putInt(TIMER, 0);
            return;
        }
        if (previous == GunState.COOLDOWN) {
            // deciderAutoRefire: refireOnHold && primaryHeld && mode 0.
            if (tag.getBoolean(PRIMARY_HELD) && living instanceof Player player) {
                if (rounds(tag) > 0) {
                    gun.fire(player, stack, tag);
                } else {
                    // doesDryFireAfterAuto=true, refireAfterDry=false -> ONE CYCLE_DRY then DRAWING (not a loop).
                    playAnimation(tag, GunAnimation.CYCLE_DRY);
                    tag.putByte(STATE, (byte) GunState.DRAWING.ordinal());
                    tag.putInt(TIMER, DRY_DELAY);
                }
            } else {
                // reloadOnEmpty=false, so no auto-reload; simply go idle.
                tag.putByte(STATE, (byte) GunState.IDLE.ordinal());
                tag.putInt(TIMER, 0);
            }
            return;
        }
        if (previous != GunState.RELOADING || !(living instanceof Player player)) return;

        // MagazineFullReload: the real fill happened at orchestra tick 55; this is an idempotent top-up.
        // Afterwards canReload is false, so there is never a RELOAD_CYCLE and the cancel flag is a no-op.
        reloadAction(player, tag);
        if (jamChance(tag.getFloat(WEAR)) > living.getRandom().nextFloat()) {
            tag.putByte(STATE, (byte) GunState.JAMMED.ordinal());
            tag.putInt(TIMER, JAM_TICKS);
            playAnimation(tag, GunAnimation.JAMMED);
        } else {
            tag.putByte(STATE, (byte) GunState.DRAWING.ordinal());
            tag.putInt(TIMER, RELOAD_END_TICKS);
            playAnimation(tag, GunAnimation.RELOAD_END);
        }
        tag.putBoolean(CANCEL_RELOAD, false);
        tag.putInt(MAG_AFTER, tag.getInt(MAG_COUNT));
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

        if (rounds(tag) > 0) {
            fire(player, stack, tag);
        } else {
            // clickReceiver empty path: doesDryFire=true, refireAfterDry=false -> DRAWING (not COOLDOWN).
            playAnimation(tag, GunAnimation.CYCLE_DRY);
            tag.putByte(STATE, (byte) GunState.DRAWING.ordinal());
            tag.putInt(TIMER, DRY_DELAY);
        }
        save(stack, tag);
    }

    private static void releasePrimary(ItemStack stack) {
        CompoundTag tag = data(stack);
        tag.putBoolean(PRIMARY_HELD, false);
        save(stack, tag);
    }

    private void fire(Player player, ItemStack stack, CompoundTag tag) {
        int loaded = rounds(tag);
        if (loaded <= 0 || !(player.level() instanceof ServerLevel level)) return;

        Shotgun12GaugeAmmoType ammo = typeOf(tag);
        float currentWear = Mth.clamp(tag.getFloat(WEAR), 0.0F, DURABILITY);
        float damage = BASE_DAMAGE * wearDamageMultiplier(currentWear) * ammo.damageMultiplier();
        float spread = SPREAD_INNATE + ammo.spread()
                + (tag.getBoolean(AIMING) ? 0.0F : HIP_SPREAD) + wearSpread(currentWear);
        Vec3 origin = projectileOrigin(player, tag.getBoolean(AIMING));
        Vec3 heading = player.getLookAngle();

        for (int projectile = 0; projectile < ammo.projectiles(); projectile++) {
            level.addFreshEntity(new BulletEntity(level, player, ammo, damage, spread, origin, heading));
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.GUN_SHREDDER_FIRE.get(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection.getConnection().isConnected()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    GunEffectPayload.fired(player.getId(), origin, heading, ammo.blackPowder()));
        }

        tag.putInt(MAG_COUNT, loaded - 1);
        tag.putFloat(WEAR, Math.min(currentWear + ammo.wear(), DURABILITY));
        tag.putByte(STATE, (byte) GunState.COOLDOWN.ordinal());
        tag.putInt(TIMER, FIRE_DELAY);
        playAnimation(tag, GunAnimation.CYCLE);
    }

    private static void pressReload(Player player, ItemStack stack) {
        CompoundTag tag = data(stack);
        if (state(tag) != GunState.IDLE) return;

        tag.putBoolean(AIMING, false);
        if (canReload(player.getInventory(), tag)) {
            tag.putInt(MAG_PREV, rounds(tag));
            tag.putByte(STATE, (byte) GunState.RELOADING.ordinal());
            tag.putInt(TIMER, RELOAD_TICKS);
            playAnimation(tag, GunAnimation.RELOAD);
        } else {
            // inspectCancel(false): the INSPECT is a locked 65-tick drinking animation (the only whiskey path).
            playAnimation(tag, GunAnimation.INSPECT);
            tag.putByte(STATE, (byte) GunState.DRAWING.ordinal());
            tag.putInt(TIMER, INSPECT_TICKS);
        }
        save(stack, tag);
    }

    private static void toggleAim(ItemStack stack) {
        CompoundTag tag = data(stack);
        tag.putBoolean(AIMING, !tag.getBoolean(AIMING));
        save(stack, tag);
    }

    private static boolean canReload(Inventory inventory, CompoundTag gun) {
        int count = rounds(gun);
        if (count >= CAPACITY) return false;
        Shotgun12GaugeAmmoType required = count > 0
                ? Shotgun12GaugeAmmoType.fromLegacyBulletConfig(gun.getInt(MAG_TYPE)) : null;
        return findFirstAmmo(inventory, required) != null;
    }

    /** MagazineFullReload.standardReload: loads as many rounds as fit from inventory in one action. */
    private static void reloadAction(Player player, CompoundTag gun) {
        Inventory inventory = player.getInventory();
        int loaded = rounds(gun);
        Shotgun12GaugeAmmoType type = loaded > 0
                ? Shotgun12GaugeAmmoType.fromLegacyBulletConfig(gun.getInt(MAG_TYPE))
                : findFirstAmmo(inventory, null);
        if (type == null) return;
        if (loaded == 0) gun.putInt(MAG_TYPE, type.legacyBulletConfig());

        for (int slot = 0; slot < inventory.getContainerSize() && loaded < CAPACITY; slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (!candidate.is(ModItems.AMMO_STANDARD.get())
                    || StandardAmmoTypes.fromStack(candidate) != type) continue;
            int consumed = Math.min(CAPACITY - loaded, candidate.getCount());
            candidate.shrink(consumed);
            loaded += consumed;
        }
        gun.putInt(MAG_COUNT, loaded);
    }

    private static Shotgun12GaugeAmmoType findFirstAmmo(Inventory inventory, Shotgun12GaugeAmmoType required) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (!candidate.is(ModItems.AMMO_STANDARD.get()) || candidate.isEmpty()) continue;
            if (!(StandardAmmoTypes.fromStack(candidate) instanceof Shotgun12GaugeAmmoType type)) continue;
            if (required == null || required == type) return type;
        }
        return null;
    }

    private static Vec3 projectileOrigin(Player player, boolean aiming) {
        // offset(0.75, -0.125, -0.25); the scoped side is 0 (offset zeroes the sideways component when aimed).
        Vec3 local = new Vec3(aiming ? 0.0D : -0.25D, -0.125D, 0.75D);
        Vec3 offset = local.xRot(-player.getXRot() * Mth.DEG_TO_RAD).yRot(-player.getYRot() * Mth.DEG_TO_RAD);
        return player.getEyePosition().add(offset);
    }

    /** CYCLE has no server foley; firing already sends its muzzle-flash payload. */
    private static void playOrchestra(Level level, LivingEntity entity, CompoundTag tag,
                                      GunAnimation animation, int timer) {
        switch (animation) {
            case CYCLE_DRY -> {
                if (timer == 0) play(level, entity, ModSounds.GUN_DRY_FIRE.get(), 1.0F, 1.0F);
            }
            case RELOAD -> {
                if (timer == 0) play(level, entity, ModSounds.GUN_REVOLVER_COCK.get(), 1.0F, 1.0F);
                if (timer == 4) play(level, entity, ModSounds.GUN_REVOLVER_CLOSE.get(), 1.0F, 0.75F);
                if (timer == 16) play(level, entity, ModSounds.GUN_MAG_SMALL_REMOVE.get(), 1.0F, 1.0F);
                if (timer == 30) play(level, entity, ModSounds.GUN_MAG_REMOVE.get(), 1.0F, 1.0F);
                if (timer == RELOAD_FILL_TICK) {
                    play(level, entity, ModSounds.GUN_IMPACT.get(), 0.5F, 1.0F);
                    // The real magazine fill happens mid-animation, one second before the reload timer ends.
                    if (entity instanceof Player player) reloadAction(player, tag);
                }
                if (timer == 65) play(level, entity, ModSounds.GUN_MAG_INSERT.get(), 1.0F, 1.0F);
                if (timer == 74) play(level, entity, ModSounds.GUN_MAG_SMALL_INSERT.get(), 1.0F, 1.0F);
                if (timer == 88) play(level, entity, ModSounds.GUN_REVOLVER_CLOSE.get(), 1.0F, 0.75F);
                if (timer == 100) play(level, entity, ModSounds.GUN_REVOLVER_COCK.get(), 1.0F, 1.0F);
            }
            case INSPECT -> {
                if (timer == 20 || timer == 25 || timer == 30 || timer == 35) {
                    play(level, entity, ModSounds.PLAYER_GULP.get(), 1.0F, 1.0F);
                }
                if (timer == 50) play(level, entity, ModSounds.PLAYER_GROAN.get(), 1.0F, 1.0F);
                if (timer == INSPECT_POTION_TICK) {
                    // Strength III (600t), Resistance III (600t), Nausea I (200t): amplifier 2 == level III.
                    entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, 2));
                    entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 2));
                    entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
                }
            }
            default -> { }
        }
    }

    private static void play(Level level, LivingEntity entity, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound,
                SoundSource.PLAYERS, volume, pitch);
    }

    public static float jamChance(float wear) {
        float percent = wear / DURABILITY;
        return percent < 0.66F ? 0.0F : Math.min((percent - 0.66F) * 4.0F, 1.0F);
    }

    public static float wearDamageMultiplier(float wear) {
        float percent = wear / DURABILITY;
        return percent < 0.75F ? 1.0F : 1.0F - (percent - 0.75F) * 2.0F;
    }

    public static float wearSpread(float wear) {
        float percent = wear / DURABILITY;
        return percent < 0.5F ? 0.0F : (percent - 0.5F) * 2.0F * MAX_WEAR_SPREAD;
    }

    public static int rounds(ItemStack stack) { return rounds(data(stack)); }
    private static int rounds(CompoundTag tag) { return Mth.clamp(tag.getInt(MAG_COUNT), 0, CAPACITY); }
    public static float wear(ItemStack stack) { return Mth.clamp(data(stack).getFloat(WEAR), 0.0F, DURABILITY); }
    public static boolean aiming(ItemStack stack) { return data(stack).getBoolean(AIMING); }
    public static boolean primaryHeld(ItemStack stack) { return data(stack).getBoolean(PRIMARY_HELD); }
    public static GunState state(ItemStack stack) { return state(data(stack)); }
    public static int timer(ItemStack stack) { return data(stack).getInt(TIMER); }
    public static GunAnimation animation(ItemStack stack) { return animation(data(stack)); }
    public static int animationTimer(ItemStack stack) { return data(stack).getInt(ANIM_TIMER); }
    public static int amountBeforeReload(ItemStack stack) { return data(stack).getInt(MAG_PREV); }
    public static int amountAfterReload(ItemStack stack) { return data(stack).getInt(MAG_AFTER); }
    public static Shotgun12GaugeAmmoType loadedAmmo(ItemStack stack) { return typeOf(data(stack)); }

    /** Fresh magazines have no MAG_TYPE; their default/fallback identity is the setDefaultAmmo MAGNUM. */
    private static Shotgun12GaugeAmmoType typeOf(CompoundTag tag) {
        if (!tag.contains(MAG_TYPE)) return DEFAULT_AMMO;
        return Shotgun12GaugeAmmoType.fromLegacyBulletConfig(tag.getInt(MAG_TYPE));
    }

    public static void setTestState(ItemStack stack, GunState state, int timer, int rounds,
                                    Shotgun12GaugeAmmoType ammo, float wear, boolean primaryHeld) {
        CompoundTag tag = data(stack);
        tag.putByte(STATE, (byte) state.ordinal());
        tag.putInt(TIMER, timer);
        tag.putInt(MAG_COUNT, Mth.clamp(rounds, 0, CAPACITY));
        tag.putInt(MAG_TYPE, ammo.legacyBulletConfig());
        tag.putFloat(WEAR, Mth.clamp(wear, 0.0F, DURABILITY));
        tag.putBoolean(PRIMARY_HELD, primaryHeld);
        // A held test gun is already drawn, so the first held tick must not replay the EQUIP animation.
        tag.putBoolean(EQUIPPED, true);
        save(stack, tag);
    }

    private static GunState state(CompoundTag tag) {
        int ordinal = tag.getByte(STATE);
        GunState[] values = GunState.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : GunState.DRAWING;
    }

    private static GunAnimation animation(CompoundTag tag) {
        int ordinal = tag.getInt(LAST_ANIM);
        GunAnimation[] values = GunAnimation.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : GunAnimation.RELOAD;
    }

    private static void playAnimation(CompoundTag tag, GunAnimation animation) {
        tag.putInt(LAST_ANIM, animation.ordinal());
        tag.putInt(ANIM_TIMER, 0);
    }

    // A fresh magazine stays EMPTY. setDefaultAmmo only carried the loose-container G12_MAGNUM/50 identity;
    // ItemGunBaseNT never preloaded the mag, so there is no preload here (Maresleg/AutoShotgun precedent).
    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void save(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Shotgun12GaugeAmmoType ammo = loadedAmmo(stack);
        float pelletDamage = BASE_DAMAGE * ammo.damageMultiplier();
        float totalDamage = pelletDamage * ammo.projectiles();
        tooltip.add(Component.translatable("gui.weapon.ammo").append(": ")
                .append(Component.translatable("item.hbm.ammo_standard." + ammo.serializedName()))
                .append(" " + rounds(stack) + " / " + CAPACITY).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.baseDamage").append(": " + trimDamage(BASE_DAMAGE))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.damageWithAmmo").append(": " + trimDamage(totalDamage)
                        + " (" + ammo.projectiles() + " x " + trimDamage(pelletDamage) + ")")
                .withStyle(ChatFormatting.GRAY));
        int condition = Mth.clamp((int) ((DURABILITY - wear(stack)) * 100.0F / DURABILITY), 0, 100);
        tooltip.add(Component.translatable("gui.weapon.condition").append(": " + condition + "%")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.quality.legendary").withStyle(ChatFormatting.RED));
    }

    private static String trimDamage(float damage) {
        if (Math.abs(damage - Math.round(damage)) < 0.0001F) return Integer.toString(Math.round(damage));
        return String.format(Locale.ROOT, "%.3f", damage).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public enum GunState { DRAWING, IDLE, COOLDOWN, RELOADING, JAMMED }

    /** Enum order is animation protocol. Do not alphabetize. */
    public enum GunAnimation {
        RELOAD, RELOAD_CYCLE, RELOAD_END, CYCLE, CYCLE_EMPTY, CYCLE_DRY,
        ALT_CYCLE, SPINUP, SPINDOWN, EQUIP, INSPECT, JAMMED
    }
}
