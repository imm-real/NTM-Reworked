package com.hbm.ntm.item;

import com.hbm.ntm.entity.ShredderBeamEntity;
import com.hbm.ntm.network.GunEffectPayload;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.weapon.GunInput;
import com.hbm.ntm.weapon.SednaCrosshair;
import com.hbm.ntm.weapon.Shotgun12GaugeAmmoType;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

/** Shredder. Feeds shells from inventory into a beam that manufactures plasma problems. */
public final class ShredderItem extends SednaGunItem {
    public static final int DURABILITY = 2_000;
    public static final int DRAW_TICKS = 10;
    public static final int INSPECT_TICKS = 33;
    public static final int FIRE_DELAY = 10;
    public static final int DRY_DELAY = 10;
    public static final int JAM_TICKS = 19;
    public static final int DEFAULT_AMMO_IDENTITY = 20; // setDefaultAmmo(G12, 20): loose-container identity only.
    public static final float BASE_DAMAGE = 50.0F;
    public static final float SPREAD_INNATE = 0.0F;
    public static final float HIP_SPREAD = 0.025F;
    public static final float MAX_WEAR_SPREAD = 0.125F;

    private static final String STATE = "state_0";
    private static final String TIMER = "timer_0";
    private static final String WEAR = "wear_0";
    private static final String MAG_TYPE = "magtype0"; // Source MagazineBelt NBT cache key ("magtype").
    private static final String MAG_COUNT = "magcount0"; // Cached belt inventory count for the HUD only.
    private static final String AIMING = "aiming";
    private static final String PRIMARY_HELD = "primary0";
    private static final String EQUIPPED = "eqipped";
    private static final String LAST_ANIM = "lastanim_0";
    private static final String ANIM_TIMER = "animtimer_0";

    public ShredderItem() { }

    public float baseDamage() { return BASE_DAMAGE; }

    @Override
    protected void handleGunInput(Player player, ItemStack stack, GunInput input) {
        switch (input) {
            case PRIMARY -> pressPrimary(player, stack);
            case PRIMARY_RELEASE -> releasePrimary(stack);
            case RELOAD -> pressReload(stack);
            case TOGGLE_AIM -> toggleAim(stack);
        }
    }

    @Override public boolean gunAiming(ItemStack stack) { return aiming(stack); }
    @Override public boolean gunAutomatic() { return true; }
    @Override public boolean gunBeltFed() { return true; }
    @Override public SednaCrosshair gunCrosshair() { return SednaCrosshair.L_CIRCLE; }
    @Override public int gunRounds(ItemStack stack) { return beltCount(stack); }
    @Override public int gunCapacity() { return 0; } // MagazineBelt.getCapacity() == 0.
    @Override public float gunWear(ItemStack stack) { return wear(stack); }
    @Override public float gunDurability() { return DURABILITY; }
    @Override public ItemStack gunAmmoIcon(ItemStack stack) {
        return loadedAmmo(stack).createStack(ModItems.AMMO_STANDARD.get(), 1);
    }
    // LAMBDA_RECOIL_AUTOSHOTGUN: setupRecoil(gaussian*1.5 + 1.5, gaussian*0.5).
    @Override public float recoilVertical() { return 1.5F; }
    @Override public float recoilVerticalSigma() { return 1.5F; }
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
            tag.putBoolean(EQUIPPED, false);
            save(stack, tag);
            return;
        }

        // Refresh the belt HUD cache (fed type + inventory count) from the holder's inventory each tick.
        if (living instanceof Player player) refreshBelt(player, tag);

        if (!tag.getBoolean(EQUIPPED)) playAnimation(tag, GunAnimation.EQUIP);
        tag.putBoolean(EQUIPPED, true);

        int animationTimer = tag.getInt(ANIM_TIMER);
        playOrchestra(level, living, animation(tag), animationTimer);
        tag.putInt(ANIM_TIMER, animationTimer + 1);

        int timer = tag.getInt(TIMER);
        if (timer > 0) tag.putInt(TIMER, timer - 1);
        if (timer <= 1) decide(living, stack, tag);
        save(stack, tag);
    }

    private static void decide(LivingEntity living, ItemStack stack, CompoundTag tag) {
        ShredderItem gun = (ShredderItem) stack.getItem();
        GunState previous = state(tag);
        if (previous == GunState.DRAWING || previous == GunState.JAMMED) {
            tag.putByte(STATE, (byte) GunState.IDLE.ordinal());
            tag.putInt(TIMER, 0);
            return;
        }
        if (previous == GunState.COOLDOWN) {
            // deciderAutoRefire: refireOnHold && primaryHeld && mode == 0
            if (tag.getBoolean(PRIMARY_HELD) && living instanceof Player player) {
                if (beltAmount(player) > 0) {
                    gun.fire(player, stack, tag);
                } else {
                    // doesDryFireAfterAuto && refireAfterDry -> keep dry-cycling in COOLDOWN.
                    playAnimation(tag, GunAnimation.CYCLE_DRY);
                    tag.putByte(STATE, (byte) GunState.COOLDOWN.ordinal());
                    tag.putInt(TIMER, DRY_DELAY);
                }
            } else {
                tag.putByte(STATE, (byte) GunState.IDLE.ordinal());
                tag.putInt(TIMER, 0);
            }
        }
    }

    private void pressPrimary(Player player, ItemStack stack) {
        CompoundTag tag = data(stack);
        tag.putBoolean(PRIMARY_HELD, true);
        if (state(tag) != GunState.IDLE) {
            save(stack, tag);
            return;
        }
        if (beltAmount(player) > 0) {
            fire(player, stack, tag);
        } else {
            // clickReceiver empty path: doesDryFire && refireAfterDry -> COOLDOWN dry loop.
            playAnimation(tag, GunAnimation.CYCLE_DRY);
            tag.putByte(STATE, (byte) GunState.COOLDOWN.ordinal());
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
        Shotgun12GaugeAmmoType ammo = firstAcceptedType(player, tag);
        if (beltAmount(player, ammo) <= 0 || !(player.level() instanceof ServerLevel level)) return;

        float currentWear = Mth.clamp(tag.getFloat(WEAR), 0.0F, DURABILITY);
        boolean aiming = tag.getBoolean(AIMING);
        // Beam damage = receiver.baseDamage * wearMult, times the shredder config damageMult (mult * projectiles).
        float beamDamage = BASE_DAMAGE * wearDamageMultiplier(currentWear)
                * ammo.damageMultiplier() * ammo.projectiles();
        // calcSpread: innate 0 + config.spread(0)*ammoSpread + hipfire + wear; the beam config spread is 0.
        float spread = SPREAD_INNATE + (aiming ? 0.0F : HIP_SPREAD) + wearSpread(currentWear);
        Vec3 offset = new Vec3(aiming ? 0.0D : -0.25D, -0.125D, 0.75D);

        ShredderBeamEntity beam = new ShredderBeamEntity(level, player, ammo, beamDamage, spread, offset);
        beam.performHitscan();
        level.addFreshEntity(beam);

        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.GUN_SHREDDER_FIRE.get(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection.getConnection().isConnected()) {
            // All six accepted loads are non-black-powder, so the muzzle puff uses the standard smoke count.
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    GunEffectPayload.fired(player.getId(), beam.position(), player.getLookAngle(), false));
        }

        consumeBelt(player, ammo);
        tag.putInt(MAG_TYPE, ammo.legacyBulletConfig());
        tag.putInt(MAG_COUNT, beltAmount(player, ammo));
        tag.putFloat(WEAR, Math.min(currentWear + ammo.wear(), DURABILITY));
        tag.putByte(STATE, (byte) GunState.COOLDOWN.ordinal());
        tag.putInt(TIMER, FIRE_DELAY);
        playAnimation(tag, GunAnimation.CYCLE);
    }

    private static void pressReload(ItemStack stack) {
        CompoundTag tag = data(stack);
        if (state(tag) != GunState.IDLE) return;
        tag.putBoolean(AIMING, false);
        // MagazineBelt.canReload() is always false -> the reload key only plays a cancelable INSPECT.
        playAnimation(tag, GunAnimation.INSPECT);
        save(stack, tag);
    }

    private static void toggleAim(ItemStack stack) {
        CompoundTag tag = data(stack);
        tag.putBoolean(AIMING, !tag.getBoolean(AIMING));
        save(stack, tag);
    }

    // ----- MagazineBelt: inventory-fed ammo selection -----

    /** MagazineBelt.getFirstConfig: first accepted shell in inventory order, else the cached type, else buckshot. */
    private static Shotgun12GaugeAmmoType firstAcceptedType(Player player, CompoundTag tag) {
        Shotgun12GaugeAmmoType found = scanFirstAccepted(player.getInventory());
        if (found != null) return found;
        Shotgun12GaugeAmmoType cached = Shotgun12GaugeAmmoType.fromLegacyBulletConfig(tag.getInt(MAG_TYPE));
        return cached.blackPowder() ? Shotgun12GaugeAmmoType.BUCKSHOT : cached;
    }

    private static Shotgun12GaugeAmmoType scanFirstAccepted(Inventory inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (candidate.isEmpty() || !candidate.is(ModItems.AMMO_STANDARD.get())) continue;
            if (StandardAmmoTypes.fromStack(candidate) instanceof Shotgun12GaugeAmmoType type
                    && !type.blackPowder()) {
                return type;
            }
        }
        return null;
    }

    private static void refreshBelt(Player player, CompoundTag tag) {
        Shotgun12GaugeAmmoType type = firstAcceptedType(player, tag);
        tag.putInt(MAG_TYPE, type.legacyBulletConfig());
        tag.putInt(MAG_COUNT, beltAmount(player, type));
    }

    private static int beltAmount(Player player) {
        Shotgun12GaugeAmmoType type = scanFirstAccepted(player.getInventory());
        return type == null ? 0 : beltAmount(player, type);
    }

    private static int beltAmount(Player player, Shotgun12GaugeAmmoType type) {
        int total = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (candidate.is(ModItems.AMMO_STANDARD.get())
                    && StandardAmmoTypes.fromStack(candidate) == type) {
                total += candidate.getCount();
            }
        }
        return total;
    }

    private static void consumeBelt(Player player, Shotgun12GaugeAmmoType type) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (candidate.is(ModItems.AMMO_STANDARD.get())
                    && StandardAmmoTypes.fromStack(candidate) == type) {
                candidate.shrink(1);
                return;
            }
        }
    }

    /** ORCHESTRA_SHREDDER: server-side, anim timer counting up from 0. */
    private static void playOrchestra(Level level, LivingEntity entity, GunAnimation animation, int timer) {
        switch (animation) {
            case CYCLE -> {
                // Timer zero used to send a muzzle flash; the fire payload already did the paperwork.
                if (timer == 2) play(level, entity, ModSounds.GUN_SHREDDER_CYCLE.get(), 0.25F, 1.5F);
            }
            case CYCLE_DRY -> {
                if (timer == 0) play(level, entity, ModSounds.GUN_DRY_FIRE.get(), 1.0F, 1.0F);
                if (timer == 2) play(level, entity, ModSounds.GUN_SHREDDER_CYCLE.get(), 0.25F, 1.5F);
            }
            case INSPECT -> {
                if (timer == 2) play(level, entity, ModSounds.GUN_MAG_REMOVE.get(), 1.0F, 1.0F);
                if (timer == 28) play(level, entity, ModSounds.GUN_MAG_INSERT.get(), 1.0F, 1.0F);
            }
            default -> { }
        }
    }

    private static void play(Level level, LivingEntity entity, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound,
                SoundSource.PLAYERS, volume, pitch);
    }

    public static float wearDamageMultiplier(float wear) {
        float percent = wear / DURABILITY;
        return percent < 0.75F ? 1.0F : 1.0F - (percent - 0.75F) * 2.0F;
    }

    public static float wearSpread(float wear) {
        float percent = wear / DURABILITY;
        return percent < 0.5F ? 0.0F : (percent - 0.5F) * 2.0F * MAX_WEAR_SPREAD;
    }

    /** Belt count is cached from the last held server tick (or a test's setTestState). */
    public static int beltCount(ItemStack stack) { return Math.max(0, data(stack).getInt(MAG_COUNT)); }
    public static float wear(ItemStack stack) { return Mth.clamp(data(stack).getFloat(WEAR), 0.0F, DURABILITY); }
    public static boolean aiming(ItemStack stack) { return data(stack).getBoolean(AIMING); }
    public static boolean primaryHeld(ItemStack stack) { return data(stack).getBoolean(PRIMARY_HELD); }
    public static GunState state(ItemStack stack) { return state(data(stack)); }
    public static int timer(ItemStack stack) { return data(stack).getInt(TIMER); }
    public static GunAnimation animation(ItemStack stack) { return animation(data(stack)); }
    public static int animationTimer(ItemStack stack) { return data(stack).getInt(ANIM_TIMER); }
    public static Shotgun12GaugeAmmoType loadedAmmo(ItemStack stack) {
        return Shotgun12GaugeAmmoType.fromLegacyBulletConfig(data(stack).getInt(MAG_TYPE));
    }

    /** Beam damage the currently fed shell would fire (50 x mult x projectiles), used by the tooltip/tests. */
    public static float beamDamage(Shotgun12GaugeAmmoType ammo) {
        return BASE_DAMAGE * ammo.damageMultiplier() * ammo.projectiles();
    }

    public static void setTestState(ItemStack stack, GunState state, int timer,
                                    Shotgun12GaugeAmmoType ammo, float wear, boolean primaryHeld) {
        CompoundTag tag = data(stack);
        tag.putByte(STATE, (byte) state.ordinal());
        tag.putInt(TIMER, timer);
        tag.putInt(MAG_TYPE, ammo.legacyBulletConfig());
        tag.putFloat(WEAR, Mth.clamp(wear, 0.0F, DURABILITY));
        tag.putBoolean(PRIMARY_HELD, primaryHeld);
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
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : GunAnimation.CYCLE;
    }

    private static void playAnimation(CompoundTag tag, GunAnimation animation) {
        tag.putInt(LAST_ANIM, animation.ordinal());
        tag.putInt(ANIM_TIMER, 0);
    }

    // A fresh Shredder carries only the loose-container G12/20 identity; the belt stores no rounds.
    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void save(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Shotgun12GaugeAmmoType ammo = loadedAmmo(stack);
        tooltip.add(Component.translatable("gui.weapon.ammo").append(": ")
                .append(Component.translatable("item.hbm.ammo_standard." + ammo.serializedName()))
                .append(" x" + beltCount(stack)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.baseDamage").append(": " + trimDamage(BASE_DAMAGE))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.damageWithAmmo").append(": " + trimDamage(beamDamage(ammo)))
                .withStyle(ChatFormatting.GRAY));
        int condition = Mth.clamp((int) ((DURABILITY - wear(stack)) * 100.0F / DURABILITY), 0, 100);
        tooltip.add(Component.translatable("gui.weapon.condition").append(": " + condition + "%")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.quality.bside").withStyle(ChatFormatting.GOLD));
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
