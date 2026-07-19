package com.hbm.ntm.item;

import com.hbm.ntm.entity.FlameProjectileEntity;
import com.hbm.ntm.network.GunEffectPayload;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.weapon.FlamerFuelType;
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

import java.util.List;
import java.util.Locale;

/** Source XFactoryFlamer receivers: Flamethrower, Mister Topaz, and Daybreaker. */
public final class FlamerGunItem extends SednaGunItem {
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

    private final Variant variant;

    public FlamerGunItem(Variant variant) {
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
            default -> { }
        }
    }

    @Override public boolean gunAiming(ItemStack stack) { return aiming(stack); }
    @Override public boolean gunAutomatic() { return true; }
    @Override public SednaCrosshair gunCrosshair() { return SednaCrosshair.L_CIRCLE; }
    @Override public int gunRounds(ItemStack stack) { return rounds(stack); }
    @Override public int gunCapacity() { return variant.capacity; }
    @Override public float gunWear(ItemStack stack) { return wear(stack); }
    @Override public float gunDurability() { return variant.durability; }
    @Override public boolean gunShowAmmoCounter() { return false; }
    @Override public ItemStack gunAmmoIcon(ItemStack stack) {
        return loadedFuel(stack).createStack(ModItems.AMMO_STANDARD.get(), 1);
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
        playOrchestra(level, living, animation(tag), animationTimer);
        tag.putInt(ANIM_TIMER, animationTimer + 1);

        int timer = tag.getInt(TIMER);
        if (timer > 0) tag.putInt(TIMER, timer - 1);
        if (timer <= 1) decide(living, stack, tag, previous);
        save(stack, tag);
    }

    private static void decide(LivingEntity living, ItemStack stack, CompoundTag tag, GunState previous) {
        FlamerGunItem gun = (FlamerGunItem) stack.getItem();
        Variant variant = gun.variant;
        if (previous == GunState.DRAWING || previous == GunState.JAMMED) {
            setState(tag, GunState.IDLE);
            tag.putInt(TIMER, 0);
            return;
        }
        if (previous == GunState.COOLDOWN) {
            if (tag.getBoolean(PRIMARY_HELD) && living instanceof Player player && rounds(tag, variant) > 0) {
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
        if (rounds(tag, variant) <= 0) {
            playAnimation(tag, GunAnimation.CYCLE_DRY);
            setState(tag, GunState.DRAWING);
            tag.putInt(TIMER, variant.drawTicks);
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

        FlamerFuelType fuel = FlamerFuelType.fromLegacyBulletConfig(tag.getInt(MAG_TYPE));
        float currentWear = Mth.clamp(tag.getFloat(WEAR), 0.0F, variant.durability);
        float damage = variant.baseDamage * wearDamageMultiplier(currentWear, variant.durability);
        float spread = variant == Variant.TOPAZ ? 0.05F : fuel.spread();
        Vec3 origin = projectileOrigin(player);
        Vec3 heading = player.getLookAngle();
        for (int i = 0; i < variant.projectiles; i++) {
            level.addFreshEntity(new FlameProjectileEntity(level, player, fuel, variant,
                    damage, spread, origin, heading));
        }
        if (variant == Variant.DAYBREAKER) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.GUN_POWDER_FIRE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        if (player instanceof ServerPlayer serverPlayer
                && serverPlayer.connection.getConnection().isConnected()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    GunEffectPayload.fired(player.getId(), origin, heading, false));
        }

        tag.putInt(MAG_COUNT, loaded - 1);
        tag.putFloat(WEAR, Math.min(currentWear + 1.0F, variant.durability));
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
        int loaded = rounds(gun, variant);
        if (loaded >= variant.capacity) return false;
        FlamerFuelType required = loaded > 0
                ? FlamerFuelType.fromLegacyBulletConfig(gun.getInt(MAG_TYPE)) : null;
        return findFuel(inventory, required) != null;
    }

    private static void reloadAction(Player player, CompoundTag gun, Variant variant) {
        int loaded = rounds(gun, variant);
        FlamerFuelType required = loaded > 0
                ? FlamerFuelType.fromLegacyBulletConfig(gun.getInt(MAG_TYPE)) : null;
        FuelSlot fuelSlot = findFuel(player.getInventory(), required);
        if (fuelSlot == null) return;
        if (loaded == 0) gun.putInt(MAG_TYPE, fuelSlot.type.legacyBulletConfig());
        fuelSlot.stack.shrink(1);
        gun.putInt(MAG_COUNT, Math.min(variant.capacity, loaded + FlamerFuelType.RELOAD_AMOUNT));
    }

    private static FuelSlot findFuel(Inventory inventory, FlamerFuelType required) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.is(ModItems.AMMO_STANDARD.get())) continue;
            if (!(StandardAmmoTypes.fromStack(stack) instanceof FlamerFuelType type)) continue;
            if (required == null || required == type) return new FuelSlot(stack, type);
        }
        return null;
    }

    private static Vec3 projectileOrigin(Player player) {
        // Receiver offset is forward/height/side; Vec3 is side/height/forward.
        Vec3 offset = new Vec3(-0.25D, -0.0625D, 0.75D)
                .xRot(-player.getXRot() * Mth.DEG_TO_RAD)
                .yRot(-player.getYRot() * Mth.DEG_TO_RAD);
        return player.getEyePosition().add(offset);
    }

    private void playOrchestra(Level level, LivingEntity entity, GunAnimation animation, int timer) {
        if (animation != GunAnimation.RELOAD) return;
        if (timer == 15) play(level, entity, ModSounds.GUN_LATCH_OPEN.get(), 1.0F, 1.0F);
        if (timer == 35) play(level, entity, ModSounds.GUN_IMPACT.get(), 1.0F, 0.5F);
        if (timer == 60) play(level, entity, ModSounds.GUN_REVOLVER_CLOSE.get(), 0.75F, 1.0F);
        if (timer == 70) play(level, entity, ModSounds.GUN_CANISTER_INSERT.get(), 1.0F, 1.0F);
        if (timer == 85) play(level, entity, ModSounds.GUN_PRESSURE_VALVE.get(), 1.0F, 1.0F);
    }

    private static void play(Level level, LivingEntity entity, SoundEvent sound, float pitch, float volume) {
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

    public static int rounds(ItemStack stack) {
        FlamerGunItem gun = (FlamerGunItem) stack.getItem();
        return rounds(data(stack), gun.variant);
    }

    private static int rounds(CompoundTag tag, Variant variant) {
        return Mth.clamp(tag.getInt(MAG_COUNT), 0, variant.capacity);
    }

    public static float wear(ItemStack stack) {
        FlamerGunItem gun = (FlamerGunItem) stack.getItem();
        return Mth.clamp(data(stack).getFloat(WEAR), 0.0F, gun.variant.durability);
    }

    public static boolean aiming(ItemStack stack) { return data(stack).getBoolean(AIMING); }
    public static GunState state(ItemStack stack) { return state(data(stack)); }
    public static int timer(ItemStack stack) { return data(stack).getInt(TIMER); }
    public static GunAnimation animation(ItemStack stack) { return animation(data(stack)); }
    public static int animationTimer(ItemStack stack) { return data(stack).getInt(ANIM_TIMER); }
    public static int amountBeforeReload(ItemStack stack) { return data(stack).getInt(MAG_PREV); }
    public static FlamerFuelType loadedFuel(ItemStack stack) {
        return FlamerFuelType.fromLegacyBulletConfig(data(stack).getInt(MAG_TYPE));
    }

    public static void setTestState(ItemStack stack, GunState state, int timer, int rounds,
                                    FlamerFuelType fuel, float wear, boolean primaryHeld) {
        FlamerGunItem gun = (FlamerGunItem) stack.getItem();
        CompoundTag tag = data(stack);
        setState(tag, state);
        tag.putInt(TIMER, timer);
        tag.putInt(MAG_COUNT, Mth.clamp(rounds, 0, gun.variant.capacity));
        tag.putInt(MAG_TYPE, fuel.legacyBulletConfig());
        tag.putFloat(WEAR, Mth.clamp(wear, 0.0F, gun.variant.durability));
        tag.putBoolean(PRIMARY_HELD, primaryHeld);
        tag.putBoolean(EQUIPPED, true);
        save(stack, tag);
    }

    private static GunState state(CompoundTag tag) {
        int ordinal = tag.getByte(STATE);
        return ordinal >= 0 && ordinal < GunState.values().length ? GunState.values()[ordinal] : GunState.DRAWING;
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
        if (!tag.getBoolean(INITIALIZED) && stack.getItem() instanceof FlamerGunItem) {
            tag.putBoolean(INITIALIZED, true);
            tag.putInt(MAG_TYPE, FlamerFuelType.DIESEL.legacyBulletConfig());
            tag.putInt(MAG_COUNT, 0); // MagazineFullReload only gets the identity, not a free tank.
        }
        return tag;
    }

    private static void save(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        FlamerFuelType fuel = loadedFuel(stack);
        tooltip.add(Component.translatable("gui.weapon.ammo").append(": ")
                .append(Component.translatable("item.hbm.ammo_standard." + fuel.serializedName()))
                .append(" " + rounds(stack) + " / " + variant.capacity).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.baseDamage")
                .append(": " + trimDamage(variant.baseDamage)).withStyle(ChatFormatting.GRAY));
        int condition = Mth.clamp((int) ((variant.durability - wear(stack)) * 100.0F
                / variant.durability), 0, 100);
        tooltip.add(Component.translatable("gui.weapon.condition").append(": " + condition + "%")
                .withStyle(ChatFormatting.GRAY));
        String quality = variant == Variant.FLAMETHROWER ? "gui.weapon.quality.aside"
                : variant == Variant.TOPAZ ? "gui.weapon.quality.bside" : "gui.weapon.quality.legendary";
        tooltip.add(Component.translatable(quality).withStyle(ChatFormatting.YELLOW));
    }

    private static String trimDamage(float damage) {
        if (Math.abs(damage - Math.round(damage)) < 0.0001F) return Integer.toString(Math.round(damage));
        return String.format(Locale.ROOT, "%.3f", damage).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public enum Variant {
        FLAMETHROWER(20_000, 10, 17, 1, 90, 17, 300, 1.0F, 1),
        TOPAZ(20_000, 10, 17, 1, 90, 17, 500, 1.5F, 2),
        DAYBREAKER(20_000, 10, 17, 10, 90, 17, 50, 25.0F, 1);

        private final int durability;
        private final int drawTicks;
        private final int inspectTicks;
        private final int fireDelay;
        private final int reloadTicks;
        private final int jamTicks;
        private final int capacity;
        private final float baseDamage;
        private final int projectiles;

        Variant(int durability, int drawTicks, int inspectTicks, int fireDelay,
                int reloadTicks, int jamTicks, int capacity, float baseDamage, int projectiles) {
            this.durability = durability;
            this.drawTicks = drawTicks;
            this.inspectTicks = inspectTicks;
            this.fireDelay = fireDelay;
            this.reloadTicks = reloadTicks;
            this.jamTicks = jamTicks;
            this.capacity = capacity;
            this.baseDamage = baseDamage;
            this.projectiles = projectiles;
        }

        public int durability() { return durability; }
        public int drawTicks() { return drawTicks; }
        public int inspectTicks() { return inspectTicks; }
        public int fireDelay() { return fireDelay; }
        public int reloadTicks() { return reloadTicks; }
        public int jamTicks() { return jamTicks; }
        public int capacity() { return capacity; }
        public float baseDamage() { return baseDamage; }
        public int projectiles() { return projectiles; }
    }

    public enum GunState { DRAWING, IDLE, COOLDOWN, RELOADING, JAMMED }

    /** Enum order is animation protocol. Do not alphabetize. */
    public enum GunAnimation {
        RELOAD, RELOAD_CYCLE, RELOAD_END, CYCLE, CYCLE_EMPTY, CYCLE_DRY,
        ALT_CYCLE, SPINUP, SPINDOWN, EQUIP, INSPECT, JAMMED
    }

    private record FuelSlot(ItemStack stack, FlamerFuelType type) { }
}
