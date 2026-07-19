package com.hbm.ntm.item;

import com.hbm.ntm.entity.FortyMillimeterProjectileEntity;
import com.hbm.ntm.network.GunEffectPayload;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.weapon.FortyMillimeterAmmoType;
import com.hbm.ntm.weapon.GunInput;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.List;
import java.util.Locale;

/** Flare Gun, Congo Lake and MK108 sharing the XFactory40mm cupboard. */
public final class FortyMillimeterGunItem extends SednaGunItem {
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

    public FortyMillimeterGunItem(Variant variant) {
        this.variant = variant;
    }

    public Variant variant() { return variant; }

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
    @Override public boolean gunAutomatic() { return variant.automatic; }
    @Override public SednaCrosshair gunCrosshair() { return SednaCrosshair.L_CIRCUMFLEX; }
    @Override public boolean gunHideCrosshairWhenAimed() { return variant != Variant.MK108; }
    @Override public int gunRounds(ItemStack stack) { return rounds(stack); }
    @Override public int gunCapacity() { return variant.capacity; }
    @Override public float gunWear(ItemStack stack) { return wear(stack); }
    @Override public float gunDurability() { return variant.durability; }
    @Override public ItemStack gunAmmoIcon(ItemStack stack) {
        return loadedAmmo(stack).createStack(ModItems.AMMO_STANDARD.get(), 1);
    }
    @Override public float recoilVertical() { return variant == Variant.MK108 ? 1.0F : 10.0F; }
    @Override public float recoilVerticalSigma() { return variant == Variant.MK108 ? 1.0F : 0.0F; }
    @Override public float recoilHorizontalSigma() { return variant == Variant.MK108 ? 1.0F : 1.5F; }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(entity instanceof LivingEntity living) || level.isClientSide) return;
        CompoundTag tag = data(stack);
        GunState previous = state(tag);
        boolean held = selected && living.getMainHandItem() == stack;
        if (!held) {
            if (previous != GunState.JAMMED) {
                tag.putByte(STATE, (byte) GunState.DRAWING.ordinal());
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
        playOrchestra(level, living, animation(tag), animationTimer, variant, rounds(tag, variant.capacity));
        tag.putInt(ANIM_TIMER, animationTimer + 1);

        int timer = tag.getInt(TIMER);
        if (timer > 0) tag.putInt(TIMER, timer - 1);
        if (timer <= 1) decide(living, stack, tag, previous);
        save(stack, tag);
    }

    private static void decide(LivingEntity living, ItemStack stack, CompoundTag tag, GunState previous) {
        FortyMillimeterGunItem gun = (FortyMillimeterGunItem) stack.getItem();
        Variant variant = gun.variant;
        if (previous == GunState.DRAWING || previous == GunState.JAMMED) {
            tag.putByte(STATE, (byte) GunState.IDLE.ordinal());
            tag.putInt(TIMER, 0);
            return;
        }
        if (previous == GunState.COOLDOWN) {
            if (variant.automatic && tag.getBoolean(PRIMARY_HELD) && living instanceof Player player) {
                if (rounds(tag, variant.capacity) > 0) gun.fire(player, tag);
                else {
                    playAnimation(tag, GunAnimation.CYCLE_DRY);
                    tag.putByte(STATE, (byte) GunState.DRAWING.ordinal());
                    tag.putInt(TIMER, variant.fireDelay);
                }
            } else {
                tag.putByte(STATE, (byte) GunState.IDLE.ordinal());
                tag.putInt(TIMER, 0);
            }
            return;
        }
        if (previous != GunState.RELOADING || !(living instanceof Player player)) return;

        if (variant.sequentialReload) {
            reloadOne(player, tag, variant);
            tag.putInt(MAG_AFTER, rounds(tag, variant.capacity));
            boolean keepLoading = !tag.getBoolean(CANCEL_RELOAD) && canReload(player.getInventory(), tag, variant);
            if (keepLoading) {
                tag.putByte(STATE, (byte) GunState.RELOADING.ordinal());
                tag.putInt(TIMER, variant.reloadCycleTicks);
                playAnimation(tag, GunAnimation.RELOAD_CYCLE);
            } else {
                tag.putByte(STATE, (byte) GunState.DRAWING.ordinal());
                tag.putInt(TIMER, variant.reloadEndTicks);
                playAnimation(tag, GunAnimation.RELOAD_END);
                tag.putBoolean(CANCEL_RELOAD, false);
            }
            return;
        }

        reloadFull(player, tag, variant);
        tag.putInt(MAG_AFTER, rounds(tag, variant.capacity));
        if (jamChance(tag.getFloat(WEAR), variant.durability) > living.getRandom().nextFloat()) {
            tag.putByte(STATE, (byte) GunState.JAMMED.ordinal());
            tag.putInt(TIMER, variant.jamTicks);
            playAnimation(tag, GunAnimation.JAMMED);
        } else {
            tag.putByte(STATE, (byte) GunState.DRAWING.ordinal());
            tag.putInt(TIMER, 0);
            playAnimation(tag, GunAnimation.RELOAD_END);
        }
        tag.putBoolean(CANCEL_RELOAD, false);
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
        if (rounds(tag, variant.capacity) <= 0) {
            playAnimation(tag, GunAnimation.CYCLE_DRY);
            tag.putByte(STATE, (byte) GunState.DRAWING.ordinal());
            tag.putInt(TIMER, variant.fireDelay);
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

    private void fire(Player player, CompoundTag tag) {
        int loaded = rounds(tag, variant.capacity);
        if (loaded <= 0 || !(player.level() instanceof ServerLevel level)) return;
        FortyMillimeterAmmoType ammo = FortyMillimeterAmmoType.fromLegacyMetadata(tag.getInt(MAG_TYPE));
        if (!variant.accepts(ammo)) ammo = variant.defaultAmmo;
        float currentWear = Mth.clamp(tag.getFloat(WEAR), 0.0F, variant.durability);
        float damage = variant.baseDamage * wearDamageMultiplier(currentWear, variant.durability)
                * ammo.damageMultiplier();
        float spread = wearSpread(currentWear, variant.durability);
        Vec3 origin = projectileOrigin(player, tag.getBoolean(AIMING));
        Vec3 heading = player.getLookAngle();
        level.addFreshEntity(new FortyMillimeterProjectileEntity(level, player, ammo, damage, spread, origin, heading));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), variant.fireSound.get(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection.getConnection().isConnected()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    GunEffectPayload.fired(player.getId(), origin, heading, false));
        }
        tag.putInt(MAG_COUNT, loaded - 1);
        tag.putFloat(WEAR, Math.min(currentWear + ammo.wear(), variant.durability));
        tag.putByte(STATE, (byte) GunState.COOLDOWN.ordinal());
        tag.putInt(TIMER, variant.fireDelay);
        playAnimation(tag, GunAnimation.CYCLE);
    }

    private void pressReload(Player player, ItemStack stack) {
        CompoundTag tag = data(stack);
        if (state(tag) != GunState.IDLE) return;
        tag.putBoolean(AIMING, false);
        if (canReload(player.getInventory(), tag, variant)) {
            int loaded = rounds(tag, variant.capacity);
            tag.putInt(MAG_PREV, loaded);
            tag.putByte(STATE, (byte) GunState.RELOADING.ordinal());
            tag.putInt(TIMER, variant.sequentialReload ? variant.reloadBeginTicks : variant.reloadTicks);
            playAnimation(tag, GunAnimation.RELOAD);
            if (variant.sequentialReload && loaded == 0) {
                FortyMillimeterAmmoType first = findFirstAmmo(player.getInventory(), null, variant);
                if (first != null) tag.putInt(MAG_TYPE, first.legacyMetadata());
            }
        } else playAnimation(tag, GunAnimation.INSPECT);
        save(stack, tag);
    }

    private static void toggleAim(ItemStack stack) {
        CompoundTag tag = data(stack);
        tag.putBoolean(AIMING, !tag.getBoolean(AIMING));
        save(stack, tag);
    }

    private static boolean canReload(Inventory inventory, CompoundTag gun, Variant variant) {
        int count = rounds(gun, variant.capacity);
        if (count >= variant.capacity) return false;
        FortyMillimeterAmmoType required = count > 0
                ? FortyMillimeterAmmoType.fromLegacyMetadata(gun.getInt(MAG_TYPE)) : null;
        return findFirstAmmo(inventory, required, variant) != null;
    }

    private static void reloadOne(Player player, CompoundTag gun, Variant variant) {
        int loaded = rounds(gun, variant.capacity);
        FortyMillimeterAmmoType required = loaded > 0
                ? FortyMillimeterAmmoType.fromLegacyMetadata(gun.getInt(MAG_TYPE)) : null;
        FortyMillimeterAmmoType type = findFirstAmmo(player.getInventory(), required, variant);
        if (type == null) return;
        if (loaded == 0) gun.putInt(MAG_TYPE, type.legacyMetadata());
        consumeAmmo(player.getInventory(), type, 1);
        gun.putInt(MAG_COUNT, loaded + 1);
    }

    private static void reloadFull(Player player, CompoundTag gun, Variant variant) {
        int loaded = rounds(gun, variant.capacity);
        FortyMillimeterAmmoType required = loaded > 0
                ? FortyMillimeterAmmoType.fromLegacyMetadata(gun.getInt(MAG_TYPE)) : null;
        FortyMillimeterAmmoType type = findFirstAmmo(player.getInventory(), required, variant);
        if (type == null) return;
        if (loaded == 0) gun.putInt(MAG_TYPE, type.legacyMetadata());
        int consumed = consumeAmmo(player.getInventory(), type, variant.capacity - loaded);
        gun.putInt(MAG_COUNT, loaded + consumed);
    }

    private static int consumeAmmo(Inventory inventory, FortyMillimeterAmmoType type, int wanted) {
        int consumed = 0;
        for (int slot = 0; slot < inventory.getContainerSize() && consumed < wanted; slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (!candidate.is(ModItems.AMMO_STANDARD.get()) || StandardAmmoTypes.fromStack(candidate) != type) continue;
            int amount = Math.min(wanted - consumed, candidate.getCount());
            candidate.shrink(amount);
            consumed += amount;
        }
        return consumed;
    }

    private static FortyMillimeterAmmoType findFirstAmmo(Inventory inventory,
                                                          FortyMillimeterAmmoType required, Variant variant) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (!candidate.is(ModItems.AMMO_STANDARD.get()) || candidate.isEmpty()) continue;
            if (!(StandardAmmoTypes.fromStack(candidate) instanceof FortyMillimeterAmmoType type)) continue;
            if (variant.accepts(type) && (required == null || required == type)) return type;
        }
        return null;
    }

    private static Vec3 projectileOrigin(Player player, boolean aiming) {
        Vec3 local = new Vec3(aiming ? 0.0D : -0.1875D, -0.0625D, 0.75D);
        Vec3 offset = local.xRot(-player.getXRot() * Mth.DEG_TO_RAD).yRot(-player.getYRot() * Mth.DEG_TO_RAD);
        return player.getEyePosition().add(offset);
    }

    private static void playOrchestra(Level level, LivingEntity entity, GunAnimation animation,
                                      int timer, Variant variant, int rounds) {
        if (animation == GunAnimation.CYCLE_DRY && timer == 0) {
            play(level, entity, ModSounds.GUN_DRY_FIRE.get(), variant == Variant.MK108 ? 0.75F : 1.0F);
        }
        if (variant == Variant.FLARE_GUN) {
            if (animation == GunAnimation.CYCLE && timer == 12) play(level, entity, ModSounds.GUN_REVOLVER_COCK.get(), 1.0F);
            if (animation == GunAnimation.CYCLE_DRY && timer == 12) play(level, entity, ModSounds.GUN_REVOLVER_COCK.get(), 1.0F);
            if ((animation == GunAnimation.RELOAD || animation == GunAnimation.JAMMED) && timer == 10)
                play(level, entity, ModSounds.GUN_MAG_SMALL_REMOVE.get(), 0.8F);
            if (animation == GunAnimation.RELOAD && timer == 16) play(level, entity, ModSounds.GUN_SHOTGUN_LOAD.get(), 1.0F);
            if ((animation == GunAnimation.RELOAD && timer == 24) || (animation == GunAnimation.JAMMED && timer == 29))
                play(level, entity, ModSounds.GUN_MAG_SMALL_INSERT.get(), 1.0F);
        } else if (variant == Variant.CONGO_LAKE) {
            if ((animation == GunAnimation.RELOAD || animation == GunAnimation.RELOAD_CYCLE) && timer == 0)
                play(level, entity, ModSounds.GUN_GRENADE_RELOAD.get(), 1.0F);
            if (animation == GunAnimation.INSPECT && timer == 9) play(level, entity, ModSounds.GUN_SHOTGUN_OPEN.get(), 1.0F);
            if (animation == GunAnimation.INSPECT && timer == 27) play(level, entity, ModSounds.GUN_SHOTGUN_CLOSE.get(), 1.0F);
        } else if (variant == Variant.MK108) {
            if (animation == GunAnimation.RELOAD) {
                if (timer == 0 || timer == 125) play(level, entity, ModSounds.GUN_REVOLVER_CLOSE.get(), 0.65F);
                if (timer == 10) play(level, entity, ModSounds.GUN_MAG_SMALL_REMOVE.get(), 0.75F);
                if (timer == 40) play(level, entity, ModSounds.GUN_MAG_REMOVE.get(), 0.75F);
                if (timer == 60) play(level, entity, ModSounds.GUN_IMPACT.get(), 0.5F);
                if (timer == 90) play(level, entity, ModSounds.GUN_MAG_INSERT.get(), 0.75F);
                if (timer == 100) play(level, entity, ModSounds.GUN_MAG_SMALL_INSERT.get(), 0.75F);
            }
            if (animation == GunAnimation.INSPECT && (timer == 9 || timer == 14 || timer == 19))
                play(level, entity, ModSounds.GUN_IMPACT.get(), 1.5F);
        }
    }

    private static void play(Level level, LivingEntity entity, SoundEvent sound, float pitch) {
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound,
                SoundSource.PLAYERS, 1.0F, pitch);
    }

    public static float jamChance(float wear, float durability) {
        if (durability <= 0.0F) return 0.0F;
        float percent = wear / durability;
        return percent < 0.66F ? 0.0F : Math.min((percent - 0.66F) * 4.0F, 1.0F);
    }
    public static float wearDamageMultiplier(float wear, float durability) {
        float percent = wear / durability;
        return percent < 0.75F ? 1.0F : 1.0F - (percent - 0.75F) * 2.0F;
    }
    public static float wearSpread(float wear, float durability) {
        float percent = wear / durability;
        return percent < 0.5F ? 0.0F : (percent - 0.5F) * 0.25F;
    }

    public static int rounds(ItemStack stack) {
        FortyMillimeterGunItem gun = (FortyMillimeterGunItem) stack.getItem();
        return rounds(data(stack), gun.variant.capacity);
    }
    private static int rounds(CompoundTag tag, int capacity) { return Mth.clamp(tag.getInt(MAG_COUNT), 0, capacity); }
    public static float wear(ItemStack stack) {
        FortyMillimeterGunItem gun = (FortyMillimeterGunItem) stack.getItem();
        return Mth.clamp(data(stack).getFloat(WEAR), 0.0F, gun.variant.durability);
    }
    public static boolean aiming(ItemStack stack) { return data(stack).getBoolean(AIMING); }
    public static GunState state(ItemStack stack) { return state(data(stack)); }
    public static GunAnimation animation(ItemStack stack) { return animation(data(stack)); }
    public static int animationTimer(ItemStack stack) { return data(stack).getInt(ANIM_TIMER); }
    public static int timer(ItemStack stack) { return data(stack).getInt(TIMER); }
    public static boolean primaryHeld(ItemStack stack) { return data(stack).getBoolean(PRIMARY_HELD); }
    public static int amountBeforeReload(ItemStack stack) { return data(stack).getInt(MAG_PREV); }
    public static int amountAfterReload(ItemStack stack) { return data(stack).getInt(MAG_AFTER); }
    public static FortyMillimeterAmmoType loadedAmmo(ItemStack stack) {
        FortyMillimeterGunItem gun = (FortyMillimeterGunItem) stack.getItem();
        FortyMillimeterAmmoType type = FortyMillimeterAmmoType.fromLegacyMetadata(data(stack).getInt(MAG_TYPE));
        return gun.variant.accepts(type) ? type : gun.variant.defaultAmmo;
    }

    public static void setTestState(ItemStack stack, GunState state, int timer, int rounds,
                                    FortyMillimeterAmmoType ammo, float wear, boolean primaryHeld) {
        FortyMillimeterGunItem gun = (FortyMillimeterGunItem) stack.getItem();
        CompoundTag tag = data(stack);
        tag.putByte(STATE, (byte) state.ordinal());
        tag.putInt(TIMER, timer);
        tag.putInt(MAG_COUNT, Mth.clamp(rounds, 0, gun.variant.capacity));
        tag.putInt(MAG_TYPE, ammo.legacyMetadata());
        tag.putFloat(WEAR, Mth.clamp(wear, 0.0F, gun.variant.durability));
        tag.putBoolean(PRIMARY_HELD, primaryHeld);
        save(stack, tag);
    }

    private static GunState state(CompoundTag tag) {
        int ordinal = tag.getByte(STATE);
        return ordinal >= 0 && ordinal < GunState.values().length ? GunState.values()[ordinal] : GunState.DRAWING;
    }
    private static GunAnimation animation(CompoundTag tag) {
        int ordinal = tag.getInt(LAST_ANIM);
        return ordinal >= 0 && ordinal < GunAnimation.values().length ? GunAnimation.values()[ordinal] : GunAnimation.RELOAD;
    }
    private static void playAnimation(CompoundTag tag, GunAnimation animation) {
        tag.putInt(LAST_ANIM, animation.ordinal());
        tag.putInt(ANIM_TIMER, 0);
    }
    private static CompoundTag data(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.getBoolean(INITIALIZED) && stack.getItem() instanceof FortyMillimeterGunItem gun) {
            tag.putBoolean(INITIALIZED, true);
            // setDefaultAmmo supplies the identity and a separate starter-ammo stack; it does not preload.
            tag.putInt(MAG_COUNT, 0);
            tag.putInt(MAG_TYPE, gun.variant.defaultAmmo.legacyMetadata());
        }
        return tag;
    }
    private static void save(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        FortyMillimeterAmmoType ammo = loadedAmmo(stack);
        float damage = variant.baseDamage * ammo.damageMultiplier();
        tooltip.add(Component.translatable("gui.weapon.ammo").append(": ")
                .append(Component.translatable("item.hbm.ammo_standard." + ammo.serializedName()))
                .append(" " + rounds(stack) + " / " + variant.capacity).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.baseDamage").append(": " + trimDamage(variant.baseDamage))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.damageWithAmmo").append(": " + trimDamage(damage))
                .withStyle(ChatFormatting.GRAY));
        int condition = Mth.clamp((int) ((variant.durability - wear(stack)) * 100.0F / variant.durability), 0, 100);
        tooltip.add(Component.translatable("gui.weapon.condition").append(": " + condition + "%")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.quality.aside").withStyle(ChatFormatting.YELLOW));
    }

    private static String trimDamage(float damage) {
        if (Math.abs(damage - Math.round(damage)) < 0.0001F) return Integer.toString(Math.round(damage));
        return String.format(Locale.ROOT, "%.3f", damage).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public enum Variant {
        FLARE_GUN(100, 1, 15.0F, 7, 20, 28, 33, false, false,
                0, 0, FortyMillimeterAmmoType.SIGNAL_FLARE, ModSounds.GUN_UNDERBARREL_FIRE),
        CONGO_LAKE(400, 4, 20.0F, 7, 24, 0, 0, true, false,
                16, 16, FortyMillimeterAmmoType.HIGH_EXPLOSIVE, ModSounds.GUN_CONGO_FIRE),
        MK108(5_000, 30, 25.0F, 20, 10, 135, 25, false, true,
                0, 0, FortyMillimeterAmmoType.HIGH_EXPLOSIVE, ModSounds.GUN_MK108_FIRE);

        private final float durability;
        private final int capacity;
        private final float baseDamage;
        private final int drawTicks;
        private final int fireDelay;
        private final int reloadTicks;
        private final int jamTicks;
        private final boolean sequentialReload;
        private final int reloadBeginTicks;
        private final int reloadCycleTicks;
        private final int reloadEndTicks;
        private final boolean automatic;
        private final FortyMillimeterAmmoType defaultAmmo;
        private final DeferredHolder<SoundEvent, SoundEvent> fireSound;

        Variant(float durability, int capacity, float baseDamage, int drawTicks, int fireDelay,
                int reloadTicks, int jamTicks, boolean sequentialReload, boolean automatic,
                int reloadBeginTicks, int reloadCycleTicks, FortyMillimeterAmmoType defaultAmmo,
                DeferredHolder<SoundEvent, SoundEvent> fireSound) {
            this.durability = durability;
            this.capacity = capacity;
            this.baseDamage = baseDamage;
            this.drawTicks = drawTicks;
            this.fireDelay = fireDelay;
            this.reloadTicks = reloadTicks;
            this.jamTicks = jamTicks;
            this.sequentialReload = sequentialReload;
            this.reloadBeginTicks = reloadBeginTicks;
            this.reloadCycleTicks = reloadCycleTicks;
            this.reloadEndTicks = sequentialReload ? 16 : 0;
            this.automatic = automatic;
            this.defaultAmmo = defaultAmmo;
            this.fireSound = fireSound;
        }

        public boolean accepts(FortyMillimeterAmmoType ammo) {
            return this == FLARE_GUN ? ammo.isFlare() : ammo.isGrenade();
        }
        public int capacity() { return capacity; }
        public float baseDamage() { return baseDamage; }
        public float durability() { return durability; }
        public int drawTicks() { return drawTicks; }
        public int fireDelay() { return fireDelay; }
        public int reloadTicks() { return reloadTicks; }
        public int jamTicks() { return jamTicks; }
        public boolean sequentialReload() { return sequentialReload; }
        public int reloadBeginTicks() { return reloadBeginTicks; }
        public int reloadCycleTicks() { return reloadCycleTicks; }
        public int reloadEndTicks() { return reloadEndTicks; }
    }

    public enum GunState { DRAWING, IDLE, COOLDOWN, RELOADING, JAMMED }
    public enum GunAnimation {
        RELOAD, RELOAD_CYCLE, RELOAD_END, CYCLE, CYCLE_EMPTY, CYCLE_DRY,
        ALT_CYCLE, SPINUP, SPINDOWN, EQUIP, INSPECT, JAMMED
    }
}
