package com.hbm.ntm.item;

import com.hbm.ntm.entity.FireExtinguisherProjectileEntity;
import com.hbm.ntm.network.GunEffectPayload;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.weapon.FireExtinguisherAmmoType;
import com.hbm.ntm.weapon.GunInput;
import com.hbm.ntm.weapon.SednaCrosshair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

/** Source XFactoryTool utility receiver. */
public final class FireExtinguisherItem extends SednaGunItem {
    public static final int DURABILITY = 5_000;
    public static final int CAPACITY = 300;
    public static final int DRAW_TICKS = 10;
    public static final int INSPECT_TICKS = 55;
    public static final int FIRE_DELAY = 1;
    public static final int RELOAD_TICKS = 20;

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
    @Override public boolean gunHideCrosshairWhenAimed() { return false; }
    @Override public int gunRounds(ItemStack stack) { return rounds(stack); }
    @Override public int gunCapacity() { return CAPACITY; }
    @Override public float gunWear(ItemStack stack) { return wear(stack); }
    @Override public float gunDurability() { return DURABILITY; }
    @Override public ItemStack gunAmmoIcon(ItemStack stack) {
        return loadedType(stack).createStack(ModItems.AMMO_FIREEXT.get(), 1);
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
            save(stack, tag);
            return;
        }

        if (!tag.getBoolean(EQUIPPED)) playAnimation(tag, GunAnimation.EQUIP);
        tag.putBoolean(EQUIPPED, true);
        if (animation(tag) == GunAnimation.RELOAD && tag.getInt(ANIM_TIMER) == 0) {
            level.playSound(null, living.getX(), living.getY(), living.getZ(),
                    ModSounds.GUN_PRESSURE_VALVE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        tag.putInt(ANIM_TIMER, tag.getInt(ANIM_TIMER) + 1);

        int timer = tag.getInt(TIMER);
        if (timer > 0) tag.putInt(TIMER, timer - 1);
        if (timer <= 1) decide(living, tag, previous);
        save(stack, tag);
    }

    private static void decide(LivingEntity living, CompoundTag tag, GunState previous) {
        if (previous == GunState.DRAWING || previous == GunState.JAMMED) {
            setState(tag, GunState.IDLE);
            tag.putInt(TIMER, 0);
            return;
        }
        if (previous == GunState.COOLDOWN) {
            if (tag.getBoolean(PRIMARY_HELD) && living instanceof Player player && rounds(tag) > 0) {
                fire(player, tag);
            } else {
                setState(tag, GunState.IDLE);
                tag.putInt(TIMER, 0);
            }
            return;
        }
        if (previous != GunState.RELOADING || !(living instanceof Player player)) return;
        if (!tag.getBoolean(CANCEL_RELOAD)) reloadAction(player, tag);
        tag.putBoolean(CANCEL_RELOAD, false);
        if (jamChance(tag.getFloat(WEAR)) > living.getRandom().nextFloat()) {
            setState(tag, GunState.JAMMED);
            playAnimation(tag, GunAnimation.JAMMED);
        } else {
            setState(tag, GunState.IDLE);
        }
        tag.putInt(TIMER, 0);
    }

    private static void pressPrimary(Player player, ItemStack stack) {
        CompoundTag tag = data(stack);
        tag.putBoolean(PRIMARY_HELD, true);
        GunState current = state(tag);
        if (current == GunState.RELOADING) {
            tag.putBoolean(CANCEL_RELOAD, true);
        } else if (current == GunState.IDLE) {
            if (rounds(tag) <= 0) {
                playAnimation(tag, GunAnimation.CYCLE_DRY);
                setState(tag, GunState.DRAWING);
                tag.putInt(TIMER, 0);
            } else {
                fire(player, tag);
            }
        }
        save(stack, tag);
    }

    private static void releasePrimary(ItemStack stack) {
        CompoundTag tag = data(stack);
        tag.putBoolean(PRIMARY_HELD, false);
        save(stack, tag);
    }

    private static void fire(Player player, CompoundTag tag) {
        int loaded = rounds(tag);
        if (loaded <= 0 || !(player.level() instanceof ServerLevel level)) return;
        FireExtinguisherAmmoType type = FireExtinguisherAmmoType.fromLegacyMetadata(tag.getInt(MAG_TYPE));
        float currentWear = Mth.clamp(tag.getFloat(WEAR), 0.0F, DURABILITY);
        float spread = type.spread() + wearSpread(currentWear);
        Vec3 origin = projectileOrigin(player);
        Vec3 heading = player.getLookAngle();
        level.addFreshEntity(new FireExtinguisherProjectileEntity(level, player, type, spread, origin, heading));
        if (player instanceof ServerPlayer serverPlayer
                && serverPlayer.connection.getConnection().isConnected()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    GunEffectPayload.fired(player.getId(), origin, heading, false));
        }
        tag.putInt(MAG_COUNT, loaded - 1);
        tag.putFloat(WEAR, Math.min(currentWear + 1.0F, DURABILITY));
        setState(tag, GunState.COOLDOWN);
        tag.putInt(TIMER, FIRE_DELAY);
        playAnimation(tag, GunAnimation.CYCLE);
    }

    private static void pressReload(Player player, ItemStack stack) {
        CompoundTag tag = data(stack);
        if (state(tag) != GunState.IDLE) return;
        tag.putBoolean(AIMING, false);
        if (canReload(player.getInventory(), tag)) {
            tag.putInt(MAG_PREV, rounds(tag));
            if (rounds(tag) == 0) {
                AmmoSlot ammo = findAmmo(player.getInventory(), null);
                if (ammo != null) tag.putInt(MAG_TYPE, ammo.type.legacyMetadata());
            }
            setState(tag, GunState.RELOADING);
            tag.putInt(TIMER, RELOAD_TICKS);
            playAnimation(tag, GunAnimation.RELOAD);
        } else {
            playAnimation(tag, GunAnimation.INSPECT);
        }
        save(stack, tag);
    }

    private static boolean canReload(Inventory inventory, CompoundTag gun) {
        int loaded = rounds(gun);
        if (loaded >= CAPACITY) return false;
        FireExtinguisherAmmoType required = loaded > 0
                ? FireExtinguisherAmmoType.fromLegacyMetadata(gun.getInt(MAG_TYPE)) : null;
        return findAmmo(inventory, required) != null;
    }

    private static void reloadAction(Player player, CompoundTag gun) {
        int loaded = rounds(gun);
        FireExtinguisherAmmoType required = loaded > 0
                ? FireExtinguisherAmmoType.fromLegacyMetadata(gun.getInt(MAG_TYPE)) : null;
        AmmoSlot ammo = findAmmo(player.getInventory(), required);
        if (ammo == null) return;
        if (loaded == 0) gun.putInt(MAG_TYPE, ammo.type.legacyMetadata());
        if (!player.getAbilities().instabuild) ammo.stack.shrink(1);
        gun.putInt(MAG_COUNT, Math.min(CAPACITY, loaded + FireExtinguisherAmmoType.RELOAD_AMOUNT));
    }

    private static AmmoSlot findAmmo(Inventory inventory, FireExtinguisherAmmoType required) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.is(ModItems.AMMO_FIREEXT.get())) continue;
            FireExtinguisherAmmoType type = FireExtinguisherAmmoType.fromStack(stack);
            if (required == null || required == type) return new AmmoSlot(stack, type);
        }
        return null;
    }

    private static Vec3 projectileOrigin(Player player) {
        Vec3 offset = new Vec3(-0.25D, -0.15625D, 1.0D)
                .xRot(-player.getXRot() * Mth.DEG_TO_RAD)
                .yRot(-player.getYRot() * Mth.DEG_TO_RAD);
        return player.getEyePosition().add(offset);
    }

    public static float wearSpread(float wear) {
        float percent = Mth.clamp(wear, 0.0F, DURABILITY) / DURABILITY;
        return percent < 0.5F ? 0.0F : (percent - 0.5F) * 2.0F * 0.125F;
    }

    public static float jamChance(float wear) {
        float percent = Mth.clamp(wear, 0.0F, DURABILITY) / DURABILITY;
        return percent < 0.66F ? 0.0F : Math.min((percent - 0.66F) * 4.0F, 1.0F);
    }

    private static void toggleAim(ItemStack stack) {
        CompoundTag tag = data(stack);
        tag.putBoolean(AIMING, !tag.getBoolean(AIMING));
        save(stack, tag);
    }

    public static int rounds(ItemStack stack) { return rounds(data(stack)); }
    private static int rounds(CompoundTag tag) { return Mth.clamp(tag.getInt(MAG_COUNT), 0, CAPACITY); }
    public static float wear(ItemStack stack) { return Mth.clamp(data(stack).getFloat(WEAR), 0.0F, DURABILITY); }
    public static boolean aiming(ItemStack stack) { return data(stack).getBoolean(AIMING); }
    public static GunState state(ItemStack stack) { return state(data(stack)); }
    public static int timer(ItemStack stack) { return data(stack).getInt(TIMER); }
    public static GunAnimation animation(ItemStack stack) { return animation(data(stack)); }
    public static int animationTimer(ItemStack stack) { return data(stack).getInt(ANIM_TIMER); }
    public static FireExtinguisherAmmoType loadedType(ItemStack stack) {
        return FireExtinguisherAmmoType.fromLegacyMetadata(data(stack).getInt(MAG_TYPE));
    }

    public static void setTestState(ItemStack stack, GunState state, int timer, int rounds,
                                    FireExtinguisherAmmoType type, float wear, boolean primaryHeld) {
        CompoundTag tag = data(stack);
        setState(tag, state);
        tag.putInt(TIMER, timer);
        tag.putInt(MAG_COUNT, Mth.clamp(rounds, 0, CAPACITY));
        tag.putInt(MAG_TYPE, type.legacyMetadata());
        tag.putFloat(WEAR, Mth.clamp(wear, 0.0F, DURABILITY));
        tag.putBoolean(PRIMARY_HELD, primaryHeld);
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
            tag.putInt(MAG_TYPE, FireExtinguisherAmmoType.WATER.legacyMetadata());
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
        FireExtinguisherAmmoType type = loadedType(stack);
        tooltip.add(Component.translatable("gui.weapon.ammo").append(": ")
                .append(Component.translatable("item.hbm.ammo_fireext." + type.serializedName()))
                .append(" " + rounds(stack) + " / " + CAPACITY).withStyle(ChatFormatting.GRAY));
        int condition = Mth.clamp((int) ((DURABILITY - wear(stack)) * 100.0F / DURABILITY), 0, 100);
        tooltip.add(Component.translatable("gui.weapon.condition").append(": " + condition + "%")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.quality.utility").withStyle(ChatFormatting.YELLOW));
    }

    public enum GunState { DRAWING, IDLE, COOLDOWN, RELOADING, JAMMED }
    public enum GunAnimation {
        RELOAD, RELOAD_CYCLE, RELOAD_END, CYCLE, CYCLE_EMPTY, CYCLE_DRY,
        ALT_CYCLE, SPINUP, SPINDOWN, EQUIP, INSPECT, JAMMED
    }
    private record AmmoSlot(ItemStack stack, FireExtinguisherAmmoType type) { }
}
