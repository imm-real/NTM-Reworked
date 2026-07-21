package com.hbm.ntm.item;

import com.hbm.ntm.entity.NI4NIBeamEntity;
import com.hbm.ntm.entity.NI4NICoinEntity;
import com.hbm.ntm.network.GunEffectPayload;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.weapon.GunInput;
import com.hbm.ntm.weapon.SednaCrosshair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class NI4NIItem extends SednaGunItem {
    public static final int DRAW_TICKS = 5;
    public static final int INSPECT_TICKS = 39;
    public static final int FIRE_DELAY = 10;
    public static final int MAX_COINS = 4;
    public static final int COIN_RECHARGE_TICKS = 80;
    public static final float BASE_DAMAGE = 35.0F;

    private static final String INITIALIZED = "hbm_initialized";
    private static final String STATE = "state_0";
    private static final String TIMER = "timer_0";
    private static final String AIMING = "aiming";
    private static final String EQUIPPED = "eqipped";
    private static final String LAST_ANIM = "lastanim_0";
    private static final String ANIM_TIMER = "animtimer_0";
    private static final String COIN_COUNT = "coincount";
    private static final String COIN_CHARGE = "coincharge";

    @Override
    protected void handleGunInput(Player player, ItemStack stack, GunInput input) {
        switch (input) {
            case PRIMARY -> fire(player, stack);
            case SECONDARY -> throwCoin(player, stack);
            case RELOAD -> inspect(stack);
            case TOGGLE_AIM -> toggleAim(stack);
            default -> { }
        }
    }

    @Override public boolean gunAiming(ItemStack stack) { return aiming(stack); }
    @Override public SednaCrosshair gunCrosshair() { return SednaCrosshair.CIRCLE; }
    @Override public int gunRounds(ItemStack stack) { return 0; }
    @Override public int gunCapacity() { return 0; }
    @Override public float gunWear(ItemStack stack) { return 0.0F; }
    @Override public float gunDurability() { return 0.0F; }
    @Override public boolean gunShowAmmoCounter() { return false; }
    @Override public boolean gunShowDurability() { return false; }
    @Override public ItemStack gunAmmoIcon(ItemStack stack) { return ItemStack.EMPTY; }
    @Override public float recoilVertical() { return 0.0F; }
    @Override public float recoilHorizontalSigma() { return 0.0F; }

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
            tag.putBoolean(EQUIPPED, false);
        } else {
            if (!tag.getBoolean(EQUIPPED)) playAnimation(tag, GunAnimation.EQUIP);
            tag.putBoolean(EQUIPPED, true);
            tag.putInt(ANIM_TIMER, tag.getInt(ANIM_TIMER) + 1);

            int timer = tag.getInt(TIMER);
            if (timer > 0) tag.putInt(TIMER, timer - 1);
            if (timer <= 1 && (previous == GunState.DRAWING || previous == GunState.COOLDOWN)) {
                setState(tag, GunState.IDLE);
                tag.putInt(TIMER, 0);
            }
        }

        rechargeCoin(level, living, held, tag);
        save(stack, tag);
    }

    private static void fire(Player player, ItemStack stack) {
        CompoundTag tag = data(stack);
        if (state(tag) != GunState.IDLE || !(player.level() instanceof ServerLevel level)) return;

        NI4NIBeamEntity beam = new NI4NIBeamEntity(level, player, BASE_DAMAGE,
                new Vec3(-0.1875D, -0.0625D, 0.75D));
        beam.performHitscan();
        level.addFreshEntity(beam);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.GUN_COIL_FIRE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (player instanceof ServerPlayer serverPlayer
                && serverPlayer.connection.getConnection().isConnected()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    GunEffectPayload.fired(player.getId(), beam.position(), beam.beamDirection(), false));
        }

        setState(tag, GunState.COOLDOWN);
        tag.putInt(TIMER, FIRE_DELAY);
        playAnimation(tag, GunAnimation.CYCLE);
        save(stack, tag);
    }

    private static void throwCoin(Player player, ItemStack stack) {
        CompoundTag tag = data(stack);
        int count = coinCount(tag);
        if (count <= 0 || !(player.level() instanceof ServerLevel level)) return;

        Vec3 look = player.getLookAngle().scale(0.8D);
        Vec3 origin = player.getEyePosition().add(0.0D, -0.625D, 0.0D);
        NI4NICoinEntity coin = new NI4NICoinEntity(level, player, origin,
                look.add(0.0D, 0.5D, 0.0D));
        level.addFreshEntity(coin);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F,
                1.0F + player.getRandom().nextFloat() * 0.25F);
        tag.putInt(COIN_COUNT, count - 1);
        save(stack, tag);
    }

    private static void rechargeCoin(Level level, LivingEntity living, boolean held, CompoundTag tag) {
        if (coinCount(tag) >= MAX_COINS) return;
        int charge = tag.getInt(COIN_CHARGE) + 1;
        if (charge < COIN_RECHARGE_TICKS) {
            tag.putInt(COIN_CHARGE, charge);
            return;
        }

        int count = coinCount(tag) + 1;
        tag.putInt(COIN_CHARGE, 0);
        tag.putInt(COIN_COUNT, count);
        if (held) {
            level.playSound(null, living.getX(), living.getY(), living.getZ(), ModSounds.TECH_BOOP.get(),
                    SoundSource.PLAYERS, 1.0F, 1.0F + (float) count / MAX_COINS);
        }
    }

    private static void inspect(ItemStack stack) {
        CompoundTag tag = data(stack);
        if (state(tag) == GunState.IDLE) {
            tag.putBoolean(AIMING, false);
            playAnimation(tag, GunAnimation.INSPECT);
        }
        save(stack, tag);
    }

    private static void toggleAim(ItemStack stack) {
        CompoundTag tag = data(stack);
        tag.putBoolean(AIMING, !tag.getBoolean(AIMING));
        save(stack, tag);
    }

    public static int coinCount(ItemStack stack) { return coinCount(data(stack)); }
    public static int coinCharge(ItemStack stack) { return Math.max(0, data(stack).getInt(COIN_CHARGE)); }
    public static boolean aiming(ItemStack stack) { return data(stack).getBoolean(AIMING); }
    public static GunState state(ItemStack stack) { return state(data(stack)); }
    public static int timer(ItemStack stack) { return data(stack).getInt(TIMER); }
    public static GunAnimation animation(ItemStack stack) { return animation(data(stack)); }
    public static int animationTimer(ItemStack stack) { return data(stack).getInt(ANIM_TIMER); }

    public static void setTestState(ItemStack stack, GunState state, int timer, int coins, int charge,
                                    GunAnimation animation, int animationTimer) {
        CompoundTag tag = data(stack);
        setState(tag, state);
        tag.putInt(TIMER, timer);
        tag.putInt(COIN_COUNT, Mth.clamp(coins, 0, MAX_COINS));
        tag.putInt(COIN_CHARGE, Math.max(0, charge));
        tag.putBoolean(EQUIPPED, true);
        tag.putInt(LAST_ANIM, animation.ordinal());
        tag.putInt(ANIM_TIMER, animationTimer);
        save(stack, tag);
    }

    private static int coinCount(CompoundTag tag) { return Math.max(0, tag.getInt(COIN_COUNT)); }

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
        if (!tag.getBoolean(INITIALIZED)) tag.putBoolean(INITIALIZED, true);
        return tag;
    }

    private static void save(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Now, don't get the wrong idea.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("I ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("fucking hate ").withStyle(ChatFormatting.RED))
                .append(Component.literal("this game.").withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("I didn't do this for you, I did it for sea.")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.baseDamage").append(": 35")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.quality.special").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    public enum GunState { DRAWING, IDLE, COOLDOWN }
    public enum GunAnimation { CYCLE, EQUIP, INSPECT }
}
