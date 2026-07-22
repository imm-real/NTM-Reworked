package com.hbm.ntm.item;

import com.hbm.ntm.entity.ChargeThrowerProjectileEntity;
import com.hbm.ntm.network.GunEffectPayload;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.weapon.ChargeThrowerAmmoType;
import com.hbm.ntm.weapon.GunInput;
import com.hbm.ntm.weapon.SednaCrosshair;
import com.hbm.ntm.weapon.StandardAmmoTypes;
import com.hbm.ntm.weapon.WeaponModManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

public final class ChargeThrowerItem extends SednaGunItem {
    public static final int DURABILITY = 3_000;
    public static final int CAPACITY = 1;
    public static final int DRAW_TICKS = 10;
    public static final int INSPECT_TICKS = 55;
    public static final int FIRE_DELAY = 4;
    public static final int DRY_DELAY = 10;
    public static final int RELOAD_TICKS = 60;
    private static final ResourceLocation SCOPE = ResourceLocation.fromNamespaceAndPath(
            "hbm", "textures/misc/scope_tool.png");

    private static final String INITIALIZED = "hbm_initialized";
    private static final String STATE = "state_0";
    private static final String TIMER = "timer_0";
    private static final String WEAR = "wear_0";
    private static final String MAG_COUNT = "magcount0";
    private static final String MAG_TYPE = "magtype0";
    private static final String AIMING = "aiming";
    private static final String PRIMARY_HELD = "primary0";
    private static final String SECONDARY_HELD = "secondary0";
    private static final String CANCEL_RELOAD = "cancel";
    private static final String EQUIPPED = "eqipped";
    private static final String LAST_ANIM = "lastanim_0";
    private static final String ANIM_TIMER = "animtimer_0";
    private static final String LAST_HOOK = "lasthook";

    @Override
    protected void handleGunInput(Player player, ItemStack stack, GunInput input) {
        switch (input) {
            case PRIMARY -> pressPrimary(player, stack);
            case PRIMARY_RELEASE -> setHeld(stack, PRIMARY_HELD, false);
            case SECONDARY -> setHeld(stack, SECONDARY_HELD, true);
            case SECONDARY_RELEASE -> setHeld(stack, SECONDARY_HELD, false);
            case RELOAD -> pressReload(player, stack);
            case TOGGLE_AIM -> toggleAim(stack);
        }
    }

    @Override public boolean gunAiming(ItemStack stack) { return aiming(stack); }
    @Override public boolean gunAutomatic() { return true; }
    @Override public boolean gunSecondaryAutomatic() { return true; }
    @Override public SednaCrosshair gunCrosshair() { return SednaCrosshair.L_CIRCUMFLEX; }
    @Override public boolean gunHideCrosshairWhenAimed() { return false; }
    @Override public boolean gunHideCrosshairWhenAimed(ItemStack stack) { return isScoped(stack); }
    @Override public float gunAimFovMultiplier(ItemStack stack) { return isScoped(stack) ? 0.34F : 0.67F; }
    @Override public ResourceLocation gunScopeTexture(ItemStack stack) { return isScoped(stack) ? SCOPE : null; }
    @Override public int gunRounds(ItemStack stack) { return rounds(stack); }
    @Override public int gunCapacity() { return CAPACITY; }
    @Override public float gunWear(ItemStack stack) { return wear(stack); }
    @Override public float gunDurability() { return DURABILITY; }
    @Override public ItemStack gunAmmoIcon(ItemStack stack) {
        return loadedAmmo(stack).createStack(ModItems.AMMO_STANDARD.get(), 1);
    }
    @Override public float recoilVertical() { return 10.0F; }
    @Override public float recoilHorizontalSigma() { return 1.5F; }

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
            tag.putBoolean(AIMING, false);
            tag.putBoolean(PRIMARY_HELD, false);
            tag.putBoolean(SECONDARY_HELD, false);
            tag.putBoolean(CANCEL_RELOAD, false);
            tag.putBoolean(EQUIPPED, false);
            tag.putInt(LAST_HOOK, -1);
            save(stack, tag);
            return;
        }

        if (!tag.getBoolean(EQUIPPED)) playAnimation(tag, GunAnimation.EQUIP);
        tag.putBoolean(EQUIPPED, true);
        int animationTimer = tag.getInt(ANIM_TIMER);
        if (animation(tag) == GunAnimation.RELOAD) {
            if (animationTimer == 30) play(level, living, ModSounds.GUN_ROCKET_INSERT.get(), 1.0F);
            if (animationTimer == 40) play(level, living, ModSounds.GUN_BOLT_CLOSE.get(), 1.0F);
        }
        tag.putInt(ANIM_TIMER, animationTimer + 1);
        updateHook(living, tag);

        int timer = tag.getInt(TIMER);
        if (timer > 0) tag.putInt(TIMER, timer - 1);
        if (timer <= 1) decide(living, tag, previous);
        save(stack, tag);
    }

    private static void updateHook(LivingEntity living, CompoundTag tag) {
        int id = tag.getInt(LAST_HOOK);
        if (id < 0) return;
        Entity entity = living.level().getEntity(id);
        if (!(entity instanceof ChargeThrowerProjectileEntity hook)
                || hook.ammoType().kind() != ChargeThrowerAmmoType.Kind.HOOK || !hook.isAlive()) {
            tag.putInt(LAST_HOOK, -1);
            return;
        }
        if (!hook.stuck()) return;
        Vec3 eye = living.getEyePosition();
        Vec3 line = hook.position().subtract(eye);
        double length = line.length();
        if (length < 1.0E-6D) return;
        if (tag.getBoolean(PRIMARY_HELD)) {
            Vec3 pull = line.scale(0.1D / length);
            living.setDeltaMovement(living.getDeltaMovement().add(pull.x, pull.y + 0.04D, pull.z));
            living.hurtMarked = true;
            if (length < 2.0D) hook.discard();
        } else if (tag.getBoolean(SECONDARY_HELD)) {
            living.setDeltaMovement(living.getDeltaMovement().scale(0.5D));
            living.hurtMarked = true;
        } else {
            Vec3 velocity = living.getDeltaMovement();
            Vec3 nextEye = eye.add(velocity);
            Vec3 nextLine = hook.position().subtract(nextEye);
            if (nextLine.length() > length) {
                Vec3 correctedEye = hook.position().subtract(nextLine.normalize().scale(length));
                Vec3 corrected = correctedEye.subtract(eye);
                if (corrected.length() < 3.0D) {
                    living.setDeltaMovement(corrected);
                    living.hurtMarked = true;
                }
            }
        }
        if (living.getDeltaMovement().y > -0.1D) living.fallDistance = 0.0F;
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
        setState(tag, jamChance(tag.getFloat(WEAR)) > living.getRandom().nextFloat()
                ? GunState.JAMMED : GunState.IDLE);
        tag.putInt(TIMER, 0);
    }

    private static void pressPrimary(Player player, ItemStack stack) {
        CompoundTag tag = data(stack);
        tag.putBoolean(PRIMARY_HELD, true);
        if (state(tag) == GunState.RELOADING) {
            tag.putBoolean(CANCEL_RELOAD, true);
        } else if (state(tag) == GunState.IDLE) {
            if (rounds(tag) > 0) fire(player, tag);
            else {
                playAnimation(tag, GunAnimation.CYCLE_DRY);
                setState(tag, GunState.COOLDOWN);
                tag.putInt(TIMER, DRY_DELAY);
                Entity hook = player.level().getEntity(tag.getInt(LAST_HOOK));
                if (hook == null) play(player.level(), player, ModSounds.GUN_DRY_FIRE.get(), 0.75F);
            }
        }
        save(stack, tag);
    }

    private static void fire(Player player, CompoundTag tag) {
        if (!(player.level() instanceof ServerLevel level) || rounds(tag) <= 0) return;
        ChargeThrowerAmmoType ammo = ChargeThrowerAmmoType.fromLegacyMetadata(tag.getInt(MAG_TYPE));
        Vec3 origin = projectileOrigin(player);
        Vec3 heading = player.getLookAngle();
        ChargeThrowerProjectileEntity projectile = new ChargeThrowerProjectileEntity(
                level, player, ammo, origin, heading);
        level.addFreshEntity(projectile);
        if (ammo.kind() == ChargeThrowerAmmoType.Kind.HOOK) tag.putInt(LAST_HOOK, projectile.getId());
        play(level, player, ModSounds.GUN_CHARGE_FIRE.get(), 1.0F);
        if (player instanceof ServerPlayer serverPlayer
                && serverPlayer.connection.getConnection().isConnected()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    GunEffectPayload.fired(player.getId(), origin, heading, false));
        }
        tag.putInt(MAG_COUNT, 0);
        tag.putFloat(WEAR, Math.min(tag.getFloat(WEAR) + 1.0F, DURABILITY));
        setState(tag, GunState.COOLDOWN);
        tag.putInt(TIMER, FIRE_DELAY);
        playAnimation(tag, GunAnimation.CYCLE);
    }

    private static void pressReload(Player player, ItemStack stack) {
        CompoundTag tag = data(stack);
        if (state(tag) != GunState.IDLE) return;
        tag.putBoolean(AIMING, false);
        ChargeThrowerAmmoType ammo = findAmmo(player.getInventory());
        if (rounds(tag) < CAPACITY && ammo != null) {
            tag.putInt(MAG_TYPE, ammo.legacyMetadata());
            tag.putInt(LAST_HOOK, -1);
            setState(tag, GunState.RELOADING);
            tag.putInt(TIMER, RELOAD_TICKS);
            playAnimation(tag, GunAnimation.RELOAD);
        } else {
            playAnimation(tag, GunAnimation.INSPECT);
        }
        save(stack, tag);
    }

    private static void reloadOne(Player player, CompoundTag tag) {
        ChargeThrowerAmmoType required = ChargeThrowerAmmoType.fromLegacyMetadata(tag.getInt(MAG_TYPE));
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack ammo = player.getInventory().getItem(slot);
            if (!ammo.is(ModItems.AMMO_STANDARD.get()) || StandardAmmoTypes.fromStack(ammo) != required) continue;
            if (!player.getAbilities().instabuild) ammo.shrink(1);
            tag.putInt(MAG_COUNT, 1);
            return;
        }
    }

    private static ChargeThrowerAmmoType findAmmo(Inventory inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.AMMO_STANDARD.get())
                    && StandardAmmoTypes.fromStack(stack) instanceof ChargeThrowerAmmoType type) return type;
        }
        return null;
    }

    private static Vec3 projectileOrigin(Player player) {
        Vec3 offset = new Vec3(-0.25D, -0.15625D, 1.0D)
                .xRot(-player.getXRot() * Mth.DEG_TO_RAD)
                .yRot(-player.getYRot() * Mth.DEG_TO_RAD);
        return player.getEyePosition().add(offset);
    }

    private static void play(Level level, LivingEntity entity, net.minecraft.sounds.SoundEvent sound,
                             float pitch) {
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound,
                SoundSource.PLAYERS, 1.0F, pitch);
    }

    private static float jamChance(float wear) {
        float percent = Mth.clamp(wear, 0.0F, DURABILITY) / DURABILITY;
        return percent < 0.66F ? 0.0F : Math.min((percent - 0.66F) * 4.0F, 1.0F);
    }

    private static void setHeld(ItemStack stack, String key, boolean held) {
        CompoundTag tag = data(stack);
        tag.putBoolean(key, held);
        save(stack, tag);
    }

    private static void toggleAim(ItemStack stack) {
        CompoundTag tag = data(stack);
        tag.putBoolean(AIMING, !tag.getBoolean(AIMING));
        save(stack, tag);
    }

    public static int rounds(ItemStack stack) { return Mth.clamp(data(stack).getInt(MAG_COUNT), 0, CAPACITY); }
    private static int rounds(CompoundTag tag) { return Mth.clamp(tag.getInt(MAG_COUNT), 0, CAPACITY); }
    public static float wear(ItemStack stack) { return Mth.clamp(data(stack).getFloat(WEAR), 0.0F, DURABILITY); }
    public static boolean aiming(ItemStack stack) { return data(stack).getBoolean(AIMING); }
    public static GunState state(ItemStack stack) { return state(data(stack)); }
    public static GunAnimation animation(ItemStack stack) { return animation(data(stack)); }
    public static int animationTimer(ItemStack stack) { return data(stack).getInt(ANIM_TIMER); }
    public static int lastHook(ItemStack stack) { return data(stack).getInt(LAST_HOOK); }
    public static ChargeThrowerAmmoType loadedAmmo(ItemStack stack) {
        return ChargeThrowerAmmoType.fromLegacyMetadata(data(stack).getInt(MAG_TYPE));
    }

    public static void setTestState(ItemStack stack, GunState state, int timer, int rounds,
                                    ChargeThrowerAmmoType ammo, float wear) {
        CompoundTag tag = data(stack);
        setState(tag, state);
        tag.putInt(TIMER, timer);
        tag.putInt(MAG_COUNT, Mth.clamp(rounds, 0, CAPACITY));
        tag.putInt(MAG_TYPE, ammo.legacyMetadata());
        tag.putFloat(WEAR, Mth.clamp(wear, 0.0F, DURABILITY));
        tag.putBoolean(EQUIPPED, true);
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
            tag.putInt(MAG_TYPE, ChargeThrowerAmmoType.MORTAR.legacyMetadata());
            tag.putInt(MAG_COUNT, 0);
            tag.putInt(LAST_HOOK, -1);
        }
        return tag;
    }
    private static void save(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        ChargeThrowerAmmoType ammo = loadedAmmo(stack);
        tooltip.add(Component.translatable("gui.weapon.ammo").append(": ")
                .append(Component.translatable("item.hbm.ammo_standard." + ammo.serializedName()))
                .append(" " + rounds(stack) + " / " + CAPACITY).withStyle(ChatFormatting.GRAY));
        int condition = Mth.clamp((int) ((DURABILITY - wear(stack)) * 100.0F / DURABILITY), 0, 100);
        tooltip.add(Component.translatable("gui.weapon.condition").append(": " + condition + "%")
                .withStyle(ChatFormatting.GRAY));
        for (ItemStack mod : WeaponModManager.installedMods(stack, 0)) {
            tooltip.add(mod.getHoverName().copy().withStyle(ChatFormatting.YELLOW));
        }
        tooltip.add(Component.translatable("gui.weapon.quality.utility").withStyle(ChatFormatting.YELLOW));
    }

    public static boolean isScoped(ItemStack stack) {
        return WeaponModManager.hasMod(stack, 0, WeaponModManager.SCOPE);
    }

    public enum GunState { DRAWING, IDLE, COOLDOWN, RELOADING, JAMMED }
    public enum GunAnimation { EQUIP, CYCLE, CYCLE_DRY, RELOAD, JAMMED, INSPECT }
}
