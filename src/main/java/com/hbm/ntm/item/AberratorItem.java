package com.hbm.ntm.item;

import com.hbm.ntm.entity.AberratorBeamEntity;
import com.hbm.ntm.network.GunEffectPayload;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.weapon.AberratorAmmoType;
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

public final class AberratorItem extends SednaGunItem {
    public static final int RECEIVER_COUNT = 1;
    public static final int DURABILITY = 2_000;
    public static final int CAPACITY = 5;
    public static final int DRAW_TICKS = 10;
    public static final int INSPECT_TICKS = 26;
    public static final int FIRE_DELAY = 13;
    public static final int DRY_TICKS = 21;
    public static final int RELOAD_TICKS = 51;
    public static final float BASE_DAMAGE = 100.0F;

    private static final String INITIALIZED = "hbm_initialized";
    private static final String STATE = "state_";
    private static final String TIMER = "timer_";
    private static final String MAG_COUNT = "magcount";
    private static final String MAG_TYPE = "magtype";
    private static final String MAG_PREV = "magprev";
    private static final String MAG_AFTER = "magafter";
    private static final String CANCEL_RELOAD = "cancel";
    private static final String EQUIPPED = "eqipped";
    private static final String LAST_ANIM = "lastanim_";
    private static final String ANIM_TIMER = "animtimer_";

    @Override
    protected void handleGunInput(Player player, ItemStack stack, GunInput input) {
        switch (input) {
            case PRIMARY -> pressTrigger(player, stack);
            case RELOAD -> pressReload(player, stack);
            case PRIMARY_RELEASE, SECONDARY, SECONDARY_RELEASE, TOGGLE_AIM -> { }
        }
    }

    @Override public boolean gunAiming(ItemStack stack) { return false; }
    @Override public SednaCrosshair gunCrosshair() { return SednaCrosshair.CIRCLE; }
    @Override public int gunRounds(ItemStack stack) { return rounds(stack, 0); }
    @Override public int gunCapacity() { return CAPACITY; }
    @Override public float gunWear(ItemStack stack) { return 0.0F; }
    @Override public float gunDurability() { return DURABILITY; }
    @Override public boolean gunShowDurability() { return false; }
    @Override public ItemStack gunAmmoIcon(ItemStack stack) { return ammoIcon(stack, 0); }
    @Override public float recoilVertical() { return 10.0F; }
    @Override public float recoilHorizontalSigma() { return 1.5F; }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(entity instanceof LivingEntity living) || level.isClientSide) return;
        boolean held = selected && living.getMainHandItem() == stack;
        CompoundTag tag = data(stack);

        if (!held) {
            setState(tag, 0, GunState.DRAWING);
            tag.putInt(timerKey(0), DRAW_TICKS);
            tag.putInt(lastAnimationKey(0), GunAnimation.CYCLE.ordinal());
            tag.putBoolean(CANCEL_RELOAD, false);
            tag.putBoolean(EQUIPPED, false);
            save(stack, tag);
            return;
        }

        if (!tag.getBoolean(EQUIPPED)) {
            playAnimation(tag, 0, GunAnimation.EQUIP);
        }
        tag.putBoolean(EQUIPPED, true);

        GunState previous = state(tag, 0);
        int animationTimer = tag.getInt(animationTimerKey(0));
        playOrchestra(level, living, animation(tag, 0), animationTimer);
        tag.putInt(animationTimerKey(0), animationTimer + 1);
        int timer = tag.getInt(timerKey(0));
        if (timer > 0) tag.putInt(timerKey(0), timer - 1);
        if (timer <= 1) decide(living, tag, previous);
        if (state(tag, 0) != GunState.RELOADING) {
            tag.putBoolean(CANCEL_RELOAD, false);
        }
        save(stack, tag);
    }

    private static void decide(LivingEntity living, CompoundTag tag, GunState previous) {
        if (previous == GunState.DRAWING || previous == GunState.COOLDOWN) {
            setState(tag, 0, GunState.IDLE);
            tag.putInt(timerKey(0), 0);
            return;
        }
        if (previous != GunState.RELOADING || !(living instanceof Player player)) return;
        if (!tag.getBoolean(CANCEL_RELOAD)) reloadAction(player, tag);
        tag.putInt(magAfterKey(0), rounds(tag, 0));
        setState(tag, 0, GunState.IDLE);
        tag.putInt(timerKey(0), 0);
    }

    private void pressTrigger(Player player, ItemStack stack) {
        CompoundTag tag = data(stack);
        GunState current = state(tag, 0);
        if (current == GunState.RELOADING) {
            tag.putBoolean(CANCEL_RELOAD, true);
            save(stack, tag);
            return;
        }
        if (current != GunState.IDLE) return;
        if (rounds(tag, 0) <= 0) {
            playAnimation(tag, 0, GunAnimation.CYCLE_DRY);
            setState(tag, 0, GunState.DRAWING);
            tag.putInt(timerKey(0), DRY_TICKS);
            save(stack, tag);
            return;
        }
        fire(player, tag);
        save(stack, tag);
    }

    private static void fire(Player player, CompoundTag tag) {
        if (!(player.level() instanceof ServerLevel level)) return;
        AberratorAmmoType ammo = loadedAmmo(tag, 0);
        Vec3 origin = projectileOrigin(player);
        Vec3 heading = player.getLookAngle();
        AberratorBeamEntity beam = new AberratorBeamEntity(level, player, ammo,
                BASE_DAMAGE, origin, heading);
        beam.performHitscan();
        level.addFreshEntity(beam);
        play(level, player, ModSounds.GUN_ABERRATOR_FIRE.get(), 1.0F);
        if (player instanceof ServerPlayer serverPlayer
                && serverPlayer.connection.getConnection().isConnected()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    GunEffectPayload.fired(player.getId(), origin, heading, false, 0));
        }

        tag.putInt(magCountKey(0), rounds(tag, 0) - 1);
        setState(tag, 0, GunState.COOLDOWN);
        tag.putInt(timerKey(0), FIRE_DELAY);
        playAnimation(tag, 0, GunAnimation.CYCLE);
    }

    private static void pressReload(Player player, ItemStack stack) {
        CompoundTag tag = data(stack);
        if (state(tag, 0) != GunState.IDLE) return;
        if (canReload(player.getInventory(), tag)) {
            tag.putInt(magPrevKey(0), rounds(tag, 0));
            setState(tag, 0, GunState.RELOADING);
            tag.putInt(timerKey(0), RELOAD_TICKS);
            playAnimation(tag, 0, GunAnimation.RELOAD);
        } else {
            setState(tag, 0, GunState.DRAWING);
            tag.putInt(timerKey(0), INSPECT_TICKS);
            playAnimation(tag, 0, GunAnimation.INSPECT);
        }
        save(stack, tag);
    }

    private static boolean canReload(Inventory inventory, CompoundTag gun) {
        int count = rounds(gun, 0);
        if (count >= CAPACITY) return false;
        AberratorAmmoType required = count > 0 ? loadedAmmo(gun, 0) : null;
        return findFirstAmmo(inventory, required) != null;
    }

    private static void reloadAction(Player player, CompoundTag gun) {
        Inventory inventory = player.getInventory();
        int loaded = rounds(gun, 0);
        AberratorAmmoType type = loaded > 0 ? loadedAmmo(gun, 0) : findFirstAmmo(inventory, null);
        if (type == null) return;
        if (loaded == 0) gun.putInt(magTypeKey(0), type.legacyMetadata());
        for (int slot = 0; slot < inventory.getContainerSize() && loaded < CAPACITY; slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (!candidate.is(ModItems.AMMO_SECRET.get())
                    || SecretAmmoTypes.fromStack(candidate) != type) continue;
            int consumed = Math.min(CAPACITY - loaded, candidate.getCount());
            candidate.shrink(consumed);
            loaded += consumed;
        }
        gun.putInt(magCountKey(0), loaded);
    }

    private static AberratorAmmoType findFirstAmmo(Inventory inventory,
                                                  AberratorAmmoType required) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (candidate.isEmpty() || !candidate.is(ModItems.AMMO_SECRET.get())) continue;
            if (!(SecretAmmoTypes.fromStack(candidate) instanceof AberratorAmmoType type)) continue;
            if (required == null || required == type) return type;
        }
        return null;
    }

    private static Vec3 projectileOrigin(Player player) {
        Vec3 local = new Vec3(0.0D, -0.09375D, 0.75D);
        Vec3 offset = local.xRot(-player.getXRot() * Mth.DEG_TO_RAD)
                .yRot(-player.getYRot() * Mth.DEG_TO_RAD);
        return player.getEyePosition().add(offset);
    }

    private static void playOrchestra(Level level, LivingEntity entity,
                                      GunAnimation animation, int timer) {
        if (animation == GunAnimation.RELOAD) {
            if (timer == 5) play(level, entity, ModSounds.GUN_MAG_SMALL_REMOVE.get(), 0.75F);
            if (timer == 32) play(level, entity, ModSounds.GUN_MAG_SMALL_INSERT.get(), 0.75F);
            if (timer == 42) play(level, entity, ModSounds.GUN_PISTOL_COCK.get(), 0.75F);
        } else if (animation == GunAnimation.CYCLE_DRY) {
            if (timer == 1) play(level, entity, ModSounds.GUN_DRY_FIRE.get(), 0.75F);
            if (timer == 9) play(level, entity, ModSounds.GUN_PISTOL_COCK.get(), 0.75F);
        }
    }

    private static void play(Level level, LivingEntity entity, SoundEvent sound, float pitch) {
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound,
                SoundSource.PLAYERS, 1.0F, pitch);
    }

    public static int rounds(ItemStack stack, int index) { return rounds(data(stack), index); }
    private static int rounds(CompoundTag tag, int index) {
        checkIndex(index);
        return Mth.clamp(tag.getInt(magCountKey(index)), 0, CAPACITY);
    }
    public static GunState state(ItemStack stack, int index) { return state(data(stack), index); }
    public static int timer(ItemStack stack, int index) { return data(stack).getInt(timerKey(index)); }
    public static GunAnimation animation(ItemStack stack, int index) {
        return animation(data(stack), index);
    }
    public static int animationTimer(ItemStack stack, int index) {
        return data(stack).getInt(animationTimerKey(index));
    }
    public static int amountBeforeReload(ItemStack stack, int index) {
        return data(stack).getInt(magPrevKey(index));
    }
    public static AberratorAmmoType loadedAmmo(ItemStack stack, int index) {
        return loadedAmmo(data(stack), index);
    }
    private static AberratorAmmoType loadedAmmo(CompoundTag tag, int index) {
        checkIndex(index);
        return AberratorAmmoType.fromLegacyMetadata(tag.getInt(magTypeKey(index)));
    }

    public static void setTestState(ItemStack stack, int index, GunState state, int timer,
                                    int rounds, AberratorAmmoType ammo) {
        checkIndex(index);
        CompoundTag tag = data(stack);
        setState(tag, index, state);
        tag.putInt(timerKey(index), timer);
        tag.putInt(magCountKey(index), Mth.clamp(rounds, 0, CAPACITY));
        tag.putInt(magTypeKey(index), ammo.legacyMetadata());
        tag.putBoolean(EQUIPPED, true);
        save(stack, tag);
    }

    private static GunState state(CompoundTag tag, int index) {
        checkIndex(index);
        int ordinal = tag.getByte(stateKey(index));
        GunState[] values = GunState.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : GunState.DRAWING;
    }
    private static void setState(CompoundTag tag, int index, GunState state) {
        tag.putByte(stateKey(index), (byte) state.ordinal());
    }
    private static GunAnimation animation(CompoundTag tag, int index) {
        checkIndex(index);
        int ordinal = tag.getInt(lastAnimationKey(index));
        GunAnimation[] values = GunAnimation.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : GunAnimation.CYCLE;
    }
    private static void playAnimation(CompoundTag tag, int index, GunAnimation animation) {
        tag.putInt(lastAnimationKey(index), animation.ordinal());
        tag.putInt(animationTimerKey(index), 0);
    }

    private static CompoundTag data(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.getBoolean(INITIALIZED)) {
            tag.putBoolean(INITIALIZED, true);
            for (int index = 0; index < RECEIVER_COUNT; index++) {
                tag.putInt(magTypeKey(index), AberratorAmmoType.V9.legacyMetadata());
            }
        }
        return tag;
    }
    private static void save(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    private static ItemStack ammoIcon(ItemStack stack, int index) {
        return loadedAmmo(stack, index).createStack(ModItems.AMMO_SECRET.get(), 1);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        for (int index = 0; index < RECEIVER_COUNT; index++) {
            AberratorAmmoType ammo = loadedAmmo(stack, index);
            tooltip.add(Component.translatable("gui.weapon.ammo").append(": ")
                    .append(Component.translatable("item.hbm.ammo_secret." + ammo.serializedName())
                    ).append(" " + rounds(stack, index) + " / " + CAPACITY)
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("gui.weapon.damageWithAmmo")
                    .append(": " + (int) BASE_DAMAGE).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("gui.weapon.quality.secret")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static void checkIndex(int index) {
        if (index < 0 || index >= RECEIVER_COUNT) {
            throw new IllegalArgumentException("Aberrator receiver index must be 0");
        }
    }
    private static String stateKey(int index) { return STATE + index; }
    private static String timerKey(int index) { return TIMER + index; }
    private static String magCountKey(int index) { return MAG_COUNT + index; }
    private static String magTypeKey(int index) { return MAG_TYPE + index; }
    private static String magPrevKey(int index) { return MAG_PREV + index; }
    private static String magAfterKey(int index) { return MAG_AFTER + index; }
    private static String lastAnimationKey(int index) { return LAST_ANIM + index; }
    private static String animationTimerKey(int index) { return ANIM_TIMER + index; }

    public enum GunState { DRAWING, IDLE, COOLDOWN, RELOADING }
    public enum GunAnimation { CYCLE, CYCLE_DRY, RELOAD, EQUIP, INSPECT }
}
