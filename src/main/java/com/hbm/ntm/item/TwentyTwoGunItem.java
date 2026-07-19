package com.hbm.ntm.item;

import com.hbm.ntm.entity.BulletEntity;
import com.hbm.ntm.network.GunEffectPayload;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.weapon.GunInput;
import com.hbm.ntm.weapon.SednaCrosshair;
import com.hbm.ntm.weapon.StandardAmmoTypes;
import com.hbm.ntm.weapon.TwentyTwoAmmoType;
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

/** Source behavior shared by XFactory22lr's American-180 and Star F. */
public final class TwentyTwoGunItem extends SednaGunItem {
    public static final float HIP_SPREAD = 0.025F;
    public static final float MAX_WEAR_SPREAD = 0.125F;

    private static final String INITIALIZED = "hbm_initialized";
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

    private final Variant variant;

    public TwentyTwoGunItem(Variant variant) {
        this.variant = variant;
    }

    public Variant variant() { return variant; }
    public float baseDamage() { return variant.baseDamage; }

    @Override
    protected void handleGunInput(Player player, ItemStack stack, GunInput input) {
        switch (input) {
            case PRIMARY -> pressPrimary(player, stack);
            case PRIMARY_RELEASE -> releasePrimary(stack);
            case RELOAD -> pressReload(player, stack);
            case TOGGLE_AIM -> toggleAim(stack);
        }
    }

    @Override public boolean gunAiming(ItemStack stack) { return aiming(stack); }
    @Override public boolean gunAutomatic() { return variant.automatic; }
    @Override public SednaCrosshair gunCrosshair() { return variant.crosshair; }
    @Override public int gunRounds(ItemStack stack) { return rounds(stack); }
    @Override public int gunCapacity() { return variant.capacity; }
    @Override public float gunWear(ItemStack stack) { return wear(stack); }
    @Override public float gunDurability() { return variant.durability; }
    @Override public ItemStack gunAmmoIcon(ItemStack stack) {
        return loadedAmmo(stack).createStack(ModItems.AMMO_STANDARD.get(), 1);
    }
    @Override public float recoilVertical() { return variant.recoilVertical; }
    @Override public float recoilVerticalSigma() { return variant.recoilVerticalSigma; }
    @Override public float recoilHorizontalSigma() { return variant.recoilHorizontalSigma; }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(entity instanceof LivingEntity living) || level.isClientSide) return;

        boolean held = selected && living.getMainHandItem() == stack;
        CompoundTag tag = data(stack);
        GunState previous = state(tag);

        if (!held) {
            if (previous != GunState.JAMMED) {
                setState(tag, GunState.DRAWING);
                tag.putInt(TIMER, variant.drawTicks);
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
        playOrchestra(level, living, animation(tag), animationTimer, variant);
        tag.putInt(ANIM_TIMER, animationTimer + 1);

        int timer = tag.getInt(TIMER);
        if (timer > 0) tag.putInt(TIMER, timer - 1);
        if (timer <= 1) decide(living, stack, tag, previous);
        save(stack, tag);
    }

    private static void decide(LivingEntity living, ItemStack stack, CompoundTag tag, GunState previous) {
        TwentyTwoGunItem gun = (TwentyTwoGunItem) stack.getItem();
        Variant variant = gun.variant;
        if (previous == GunState.DRAWING || previous == GunState.JAMMED) {
            setState(tag, GunState.IDLE);
            tag.putInt(TIMER, 0);
            return;
        }
        if (previous == GunState.COOLDOWN) {
            if (variant.automatic && tag.getBoolean(PRIMARY_HELD)
                    && living instanceof Player player && rounds(tag, variant) > 0) {
                gun.fire(player, stack, tag);
            } else {
                setState(tag, GunState.IDLE);
                tag.putInt(TIMER, 0);
            }
            return;
        }
        if (previous != GunState.RELOADING || !(living instanceof Player player)) return;

        if (!tag.getBoolean(CANCEL_RELOAD)) reloadAction(player, tag, variant);
        tag.putBoolean(CANCEL_RELOAD, false);
        tag.putInt(MAG_AFTER, rounds(tag, variant));
        if (jamChance(tag.getFloat(WEAR), variant.durability) > living.getRandom().nextFloat()) {
            setState(tag, GunState.JAMMED);
            tag.putInt(TIMER, variant.jamTicks);
            playAnimation(tag, GunAnimation.JAMMED);
        } else {
            setState(tag, GunState.IDLE);
            tag.putInt(TIMER, 0);
        }
    }

    private void pressPrimary(Player player, ItemStack stack) {
        CompoundTag tag = data(stack);
        if (variant.automatic) tag.putBoolean(PRIMARY_HELD, true);
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
        if (rounds(tag, variant) <= 0) {
            playAnimation(tag, GunAnimation.CYCLE_DRY);
            setState(tag, GunState.DRAWING);
            tag.putInt(TIMER, variant.dryTicks);
            save(stack, tag);
            return;
        }
        fire(player, stack, tag);
        save(stack, tag);
    }

    private static void releasePrimary(ItemStack stack) {
        CompoundTag tag = data(stack);
        tag.putBoolean(PRIMARY_HELD, false);
        save(stack, tag);
    }

    private void fire(Player player, ItemStack stack, CompoundTag tag) {
        int loaded = rounds(tag, variant);
        if (loaded <= 0 || !(player.level() instanceof ServerLevel level)) return;

        TwentyTwoAmmoType ammo = TwentyTwoAmmoType.fromLegacyBulletConfig(tag.getInt(MAG_TYPE));
        float currentWear = Mth.clamp(tag.getFloat(WEAR), 0.0F, variant.durability);
        float damage = variant.baseDamage * wearDamageMultiplier(currentWear, variant.durability)
                * ammo.damageMultiplier();
        boolean aiming = tag.getBoolean(AIMING);
        float spread = variant.innateSpread + ammo.spread()
                + (aiming ? 0.0F : HIP_SPREAD) + wearSpread(currentWear, variant.durability);
        Vec3 origin = projectileOrigin(player, aiming);
        Vec3 heading = player.getLookAngle();

        level.addFreshEntity(new BulletEntity(level, player, ammo, damage, spread, origin, heading));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), variant.fireSound.get(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        if (player instanceof ServerPlayer serverPlayer
                && serverPlayer.connection.getConnection().isConnected()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    GunEffectPayload.fired(player.getId(), origin, heading, false));
        }

        tag.putInt(MAG_COUNT, loaded - 1);
        tag.putFloat(WEAR, Math.min(currentWear + ammo.wear(), variant.durability));
        setState(tag, GunState.COOLDOWN);
        tag.putInt(TIMER, variant.fireDelay);
        playAnimation(tag, GunAnimation.CYCLE);
    }

    private void pressReload(Player player, ItemStack stack) {
        CompoundTag tag = data(stack);
        if (state(tag) != GunState.IDLE) return;
        tag.putBoolean(AIMING, false);
        if (canReload(player.getInventory(), tag, variant)) {
            tag.putInt(MAG_PREV, rounds(tag, variant));
            setState(tag, GunState.RELOADING);
            tag.putInt(TIMER, variant.reloadTicks);
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

    private static boolean canReload(Inventory inventory, CompoundTag gun, Variant variant) {
        int count = rounds(gun, variant);
        if (count >= variant.capacity) return false;
        TwentyTwoAmmoType required = count > 0
                ? TwentyTwoAmmoType.fromLegacyBulletConfig(gun.getInt(MAG_TYPE)) : null;
        return findFirstAmmo(inventory, required) != null;
    }

    private static void reloadAction(Player player, CompoundTag gun, Variant variant) {
        Inventory inventory = player.getInventory();
        int loaded = rounds(gun, variant);
        TwentyTwoAmmoType type = loaded > 0
                ? TwentyTwoAmmoType.fromLegacyBulletConfig(gun.getInt(MAG_TYPE))
                : findFirstAmmo(inventory, null);
        if (type == null) return;
        if (loaded == 0) gun.putInt(MAG_TYPE, type.legacyBulletConfig());
        for (int slot = 0; slot < inventory.getContainerSize() && loaded < variant.capacity; slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (!candidate.is(ModItems.AMMO_STANDARD.get())
                    || StandardAmmoTypes.fromStack(candidate) != type) continue;
            int consumed = Math.min(variant.capacity - loaded, candidate.getCount());
            candidate.shrink(consumed);
            loaded += consumed;
        }
        gun.putInt(MAG_COUNT, loaded);
    }

    private static TwentyTwoAmmoType findFirstAmmo(Inventory inventory, TwentyTwoAmmoType required) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (candidate.isEmpty() || !candidate.is(ModItems.AMMO_STANDARD.get())) continue;
            if (!(StandardAmmoTypes.fromStack(candidate) instanceof TwentyTwoAmmoType type)) continue;
            if (required == null || required == type) return type;
        }
        return null;
    }

    private static Vec3 projectileOrigin(Player player, boolean aiming) {
        Vec3 local = new Vec3(aiming ? 0.0D : -0.1875D, -0.09375D, 1.0D);
        Vec3 offset = local.xRot(-player.getXRot() * Mth.DEG_TO_RAD)
                .yRot(-player.getYRot() * Mth.DEG_TO_RAD);
        return player.getEyePosition().add(offset);
    }

    private static void playOrchestra(Level level, LivingEntity entity, GunAnimation animation,
                                      int timer, Variant variant) {
        if (variant == Variant.AM180) {
            if (animation == GunAnimation.CYCLE_DRY) {
                if (timer == 0) play(level, entity, ModSounds.GUN_DRY_FIRE.get(), 1.0F);
                if (timer == 6) play(level, entity, ModSounds.GUN_PISTOL_COCK.get(), 0.9F);
            } else if (animation == GunAnimation.RELOAD) {
                if (timer == 2) play(level, entity, ModSounds.GUN_MAG_REMOVE.get(), 1.0F);
                if (timer == 20) play(level, entity, ModSounds.GUN_IMPACT.get(), 1.0F, 0.25F);
                if (timer == 32) play(level, entity, ModSounds.GUN_MAG_INSERT.get(), 1.0F);
                if (timer == 40) play(level, entity, ModSounds.GUN_PISTOL_COCK.get(), 0.9F);
            } else if (animation == GunAnimation.JAMMED && timer == 15) {
                play(level, entity, ModSounds.GUN_PISTOL_COCK.get(), 0.8F);
            } else if (animation == GunAnimation.INSPECT) {
                if (timer == 2) play(level, entity, ModSounds.GUN_MAG_REMOVE.get(), 1.0F);
                if (timer == 35) play(level, entity, ModSounds.GUN_MAG_INSERT.get(), 1.0F);
            }
            return;
        }

        if (animation == GunAnimation.CYCLE_DRY) {
            if (timer == 0) play(level, entity, ModSounds.GUN_DRY_FIRE.get(), 0.9F);
            if (timer == 5) play(level, entity, ModSounds.GUN_PISTOL_COCK.get(), 1.1F);
        } else if (animation == GunAnimation.RELOAD) {
            if (timer == 5) {
                play(level, entity, ModSounds.GUN_REVOLVER_CLOSE.get(), 1.0F);
                play(level, entity, ModSounds.GUN_MAG_REMOVE.get(), 1.0F);
            }
            if (timer == 22) play(level, entity, ModSounds.GUN_MAG_INSERT.get(), 1.0F);
            if (timer == 30) play(level, entity, ModSounds.GUN_REVOLVER_CLOSE.get(), 1.1F);
        } else if (animation == GunAnimation.JAMMED) {
            if (timer == 15 || timer == 23) play(level, entity, ModSounds.GUN_REVOLVER_CLOSE.get(), 1.0F);
            if (timer == 19 || timer == 27) play(level, entity, ModSounds.GUN_REVOLVER_CLOSE.get(), 1.1F);
        } else if (animation == GunAnimation.INSPECT) {
            if (timer == 7) play(level, entity, ModSounds.GUN_REVOLVER_CLOSE.get(), 1.0F);
            if (timer == 30) play(level, entity, ModSounds.GUN_REVOLVER_CLOSE.get(), 1.1F);
        }
    }

    private static void play(Level level, LivingEntity entity, SoundEvent sound, float pitch) {
        play(level, entity, sound, pitch, 1.0F);
    }

    private static void play(Level level, LivingEntity entity, SoundEvent sound,
                             float pitch, float volume) {
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound,
                SoundSource.PLAYERS, volume, pitch);
    }

    public static float jamChance(float wear, float durability) {
        float percent = wear / durability;
        return percent < 0.66F ? 0.0F : Math.min((percent - 0.66F) * 4.0F, 1.0F);
    }

    public static float wearDamageMultiplier(float wear, float durability) {
        float percent = wear / durability;
        return percent < 0.75F ? 1.0F : 1.0F - (percent - 0.75F) * 2.0F;
    }

    public static float wearSpread(float wear, float durability) {
        float percent = wear / durability;
        return percent < 0.5F ? 0.0F : (percent - 0.5F) * 2.0F * MAX_WEAR_SPREAD;
    }

    public static int rounds(ItemStack stack) {
        TwentyTwoGunItem gun = (TwentyTwoGunItem) stack.getItem();
        return rounds(data(stack), gun.variant);
    }
    private static int rounds(CompoundTag tag, Variant variant) {
        return Mth.clamp(tag.getInt(MAG_COUNT), 0, variant.capacity);
    }
    public static float wear(ItemStack stack) {
        TwentyTwoGunItem gun = (TwentyTwoGunItem) stack.getItem();
        return Mth.clamp(data(stack).getFloat(WEAR), 0.0F, gun.variant.durability);
    }
    public static boolean aiming(ItemStack stack) { return data(stack).getBoolean(AIMING); }
    public static GunState state(ItemStack stack) { return state(data(stack)); }
    public static int timer(ItemStack stack) { return data(stack).getInt(TIMER); }
    public static GunAnimation animation(ItemStack stack) { return animation(data(stack)); }
    public static int animationTimer(ItemStack stack) { return data(stack).getInt(ANIM_TIMER); }
    public static int amountBeforeReload(ItemStack stack) { return data(stack).getInt(MAG_PREV); }
    public static TwentyTwoAmmoType loadedAmmo(ItemStack stack) {
        return TwentyTwoAmmoType.fromLegacyBulletConfig(data(stack).getInt(MAG_TYPE));
    }

    public static void setTestState(ItemStack stack, GunState state, int timer, int rounds,
                                    TwentyTwoAmmoType ammo, float wear, boolean primaryHeld) {
        TwentyTwoGunItem gun = (TwentyTwoGunItem) stack.getItem();
        CompoundTag tag = data(stack);
        setState(tag, state);
        tag.putInt(TIMER, timer);
        tag.putInt(MAG_COUNT, Mth.clamp(rounds, 0, gun.variant.capacity));
        tag.putInt(MAG_TYPE, ammo.legacyBulletConfig());
        tag.putFloat(WEAR, Mth.clamp(wear, 0.0F, gun.variant.durability));
        tag.putBoolean(PRIMARY_HELD, primaryHeld);
        tag.putBoolean(EQUIPPED, true);
        save(stack, tag);
    }

    private static GunState state(CompoundTag tag) {
        int ordinal = tag.getByte(STATE);
        GunState[] values = GunState.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : GunState.DRAWING;
    }

    private static void setState(CompoundTag tag, GunState state) {
        tag.putByte(STATE, (byte) state.ordinal());
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

    private static CompoundTag data(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.getBoolean(INITIALIZED) && stack.getItem() instanceof TwentyTwoGunItem) {
            tag.putBoolean(INITIALIZED, true);
            tag.putInt(MAG_TYPE, TwentyTwoAmmoType.SOFT_POINT.legacyBulletConfig());
            // Source setDefaultAmmo fills the loose-ammo container, not MagazineFullReload.
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
        TwentyTwoAmmoType ammo = loadedAmmo(stack);
        tooltip.add(Component.translatable("gui.weapon.ammo").append(": ")
                .append(Component.translatable("item.hbm.ammo_standard." + ammo.serializedName()))
                .append(" " + rounds(stack) + " / " + variant.capacity).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.baseDamage")
                .append(": " + trimDamage(variant.baseDamage)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.damageWithAmmo")
                .append(": " + trimDamage(variant.baseDamage * ammo.damageMultiplier()))
                .withStyle(ChatFormatting.GRAY));
        int condition = Mth.clamp((int) ((variant.durability - wear(stack)) * 100.0F
                / variant.durability), 0, 100);
        tooltip.add(Component.translatable("gui.weapon.condition").append(": " + condition + "%")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.quality.aside").withStyle(ChatFormatting.YELLOW));
    }

    private static String trimDamage(float damage) {
        if (Math.abs(damage - Math.round(damage)) < 0.0001F) return Integer.toString(Math.round(damage));
        return String.format(Locale.ROOT, "%.3f", damage).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public enum Variant {
        AM180(4_425, 15, 38, 1, 10, 66, 30, 177, 2.0F, 0.01F,
                true, SednaCrosshair.L_CIRCLE, 0.0F, 0.25F, 0.25F,
                ModSounds.GUN_GREASEGUN_FIRE),
        STAR_F(375, 15, 38, 5, 17, 40, 32, 15, 12.5F, 0.01F,
                false, SednaCrosshair.CIRCLE, 2.5F, 0.0F, 0.5F,
                ModSounds.GUN_STAR_F_FIRE);

        private final int durability;
        private final int drawTicks;
        private final int inspectTicks;
        private final int fireDelay;
        private final int dryTicks;
        private final int reloadTicks;
        private final int jamTicks;
        private final int capacity;
        private final float baseDamage;
        private final float innateSpread;
        private final boolean automatic;
        private final SednaCrosshair crosshair;
        private final float recoilVertical;
        private final float recoilVerticalSigma;
        private final float recoilHorizontalSigma;
        private final net.neoforged.neoforge.registries.DeferredHolder<SoundEvent, SoundEvent> fireSound;

        Variant(int durability, int drawTicks, int inspectTicks, int fireDelay, int dryTicks,
                int reloadTicks, int jamTicks, int capacity, float baseDamage, float innateSpread,
                boolean automatic, SednaCrosshair crosshair, float recoilVertical,
                float recoilVerticalSigma, float recoilHorizontalSigma,
                net.neoforged.neoforge.registries.DeferredHolder<SoundEvent, SoundEvent> fireSound) {
            this.durability = durability;
            this.drawTicks = drawTicks;
            this.inspectTicks = inspectTicks;
            this.fireDelay = fireDelay;
            this.dryTicks = dryTicks;
            this.reloadTicks = reloadTicks;
            this.jamTicks = jamTicks;
            this.capacity = capacity;
            this.baseDamage = baseDamage;
            this.innateSpread = innateSpread;
            this.automatic = automatic;
            this.crosshair = crosshair;
            this.recoilVertical = recoilVertical;
            this.recoilVerticalSigma = recoilVerticalSigma;
            this.recoilHorizontalSigma = recoilHorizontalSigma;
            this.fireSound = fireSound;
        }

        public int durability() { return durability; }
        public int drawTicks() { return drawTicks; }
        public int inspectTicks() { return inspectTicks; }
        public int fireDelay() { return fireDelay; }
        public int dryTicks() { return dryTicks; }
        public int reloadTicks() { return reloadTicks; }
        public int jamTicks() { return jamTicks; }
        public int capacity() { return capacity; }
        public float baseDamage() { return baseDamage; }
        public float innateSpread() { return innateSpread; }
        public boolean automatic() { return automatic; }
    }

    public enum GunState { DRAWING, IDLE, COOLDOWN, RELOADING, JAMMED }

    /** Enum order is animation protocol. Do not alphabetize. */
    public enum GunAnimation {
        RELOAD, RELOAD_CYCLE, RELOAD_END, CYCLE, CYCLE_EMPTY, CYCLE_DRY,
        ALT_CYCLE, SPINUP, SPINDOWN, EQUIP, INSPECT, JAMMED
    }
}
