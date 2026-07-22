package com.hbm.ntm.item;

import com.hbm.ntm.explosion.MineExplosion;
import com.hbm.ntm.network.GunEffectPayload;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.weapon.GunInput;
import com.hbm.ntm.weapon.SednaCrosshair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

public final class DrillItem extends SednaGunItem {
    public static final int DURABILITY = 3_000;
    public static final int CAPACITY = 4_000;
    public static final int FUEL_PER_CYCLE = 10;
    public static final int DRAW_TICKS = 10;
    public static final int FIRE_DELAY = 20;
    public static final int DRY_DELAY = 30;
    public static final float DAMAGE = 10.0F;
    public static final double REACH = 5.0D;

    private static final String INITIALIZED = "hbm_initialized";
    private static final String STATE = "state_0";
    private static final String TIMER = "timer_0";
    private static final String FUEL = "magcount0";
    private static final String AIMING = "aiming";
    private static final String PRIMARY_HELD = "primary0";
    private static final String EQUIPPED = "eqipped";
    private static final String LAST_ANIM = "lastanim_0";
    private static final String ANIM_TIMER = "animtimer_0";

    @Override
    protected void handleGunInput(Player player, ItemStack stack, GunInput input) {
        switch (input) {
            case PRIMARY -> pressPrimary(player, stack);
            case PRIMARY_RELEASE -> releasePrimary(stack);
            case RELOAD -> refuel(player, stack);
            case TOGGLE_AIM -> toggleAim(stack);
            default -> { }
        }
    }

    @Override public boolean gunAiming(ItemStack stack) { return aiming(stack); }
    @Override public boolean gunAutomatic() { return true; }
    @Override public SednaCrosshair gunCrosshair() { return SednaCrosshair.L_CIRCUMFLEX; }
    @Override public boolean gunHideCrosshairWhenAimed() { return false; }
    @Override public int gunRounds(ItemStack stack) { return fuel(stack); }
    @Override public int gunCapacity() { return CAPACITY; }
    @Override public float gunWear(ItemStack stack) { return 0.0F; }
    @Override public float gunDurability() { return DURABILITY; }
    @Override public ItemStack gunAmmoIcon(ItemStack stack) {
        return SourceFluidContainerItem.create(ModItems.CANISTER_FULL.get(),
                SourceFluidContainerItem.ContainedFluid.GASOLINE, 1);
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
            setState(tag, GunState.DRAWING);
            tag.putInt(TIMER, DRAW_TICKS);
            tag.putBoolean(PRIMARY_HELD, false);
            tag.putBoolean(AIMING, false);
            tag.putBoolean(EQUIPPED, false);
            save(stack, tag);
            return;
        }
        if (!tag.getBoolean(EQUIPPED)) playAnimation(tag, GunAnimation.EQUIP);
        tag.putBoolean(EQUIPPED, true);
        tag.putInt(ANIM_TIMER, tag.getInt(ANIM_TIMER) + 1);
        int timer = tag.getInt(TIMER);
        if (timer > 0) tag.putInt(TIMER, timer - 1);
        if (timer <= 1) decide(living, tag, previous);
        save(stack, tag);
    }

    private static void decide(LivingEntity living, CompoundTag tag, GunState previous) {
        if (previous == GunState.DRAWING) {
            setState(tag, GunState.IDLE);
            tag.putInt(TIMER, 0);
        } else if (previous == GunState.COOLDOWN) {
            if (tag.getBoolean(PRIMARY_HELD) && living instanceof Player player && fuel(tag) >= FUEL_PER_CYCLE) {
                fire(player, tag);
            } else {
                setState(tag, GunState.IDLE);
                tag.putInt(TIMER, 0);
            }
        }
    }

    private static void pressPrimary(Player player, ItemStack stack) {
        CompoundTag tag = data(stack);
        tag.putBoolean(PRIMARY_HELD, true);
        if (state(tag) == GunState.IDLE) {
            if (fuel(tag) >= FUEL_PER_CYCLE) fire(player, tag);
            else {
                playAnimation(tag, GunAnimation.CYCLE_DRY);
                setState(tag, GunState.COOLDOWN);
                tag.putInt(TIMER, DRY_DELAY);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        ModSounds.GUN_DRY_FIRE.get(), SoundSource.PLAYERS, 1.0F, 0.75F);
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
        if (!(player.level() instanceof ServerLevel level)) return;
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(REACH));
        BlockHitResult blockHit = level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 collisionEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        LivingEntity target = nearestLiving(level, player, start, collisionEnd);
        if (target != null) {
            var source = level.damageSources().playerAttack(player);
            float amount = MineExplosion.compensateForArmorPiercing(target, source, DAMAGE, 2.0F, 0.15F);
            target.invulnerableTime = 0;
            target.hurt(source, amount);
        } else if (blockHit.getType() != HitResult.Type.MISS && player instanceof ServerPlayer serverPlayer) {
            breakBlocks(serverPlayer, blockHit.getBlockPos(), player.isShiftKeyDown() ? 0 : 1);
        }
        tag.putInt(FUEL, Math.max(0, fuel(tag) - FUEL_PER_CYCLE));
        setState(tag, GunState.COOLDOWN);
        tag.putInt(TIMER, FIRE_DELAY);
        playAnimation(tag, GunAnimation.CYCLE);
        if (player instanceof ServerPlayer serverPlayer
                && serverPlayer.connection.getConnection().isConnected()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    GunEffectPayload.fired(player.getId(), start, player.getLookAngle(), false));
        }
    }

    private static LivingEntity nearestLiving(ServerLevel level, Player player, Vec3 start, Vec3 end) {
        AABB area = new AABB(start, end).inflate(1.0D);
        LivingEntity nearest = null;
        double distance = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != player && entity.isAlive() && !entity.isSpectator())) {
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.3D).clip(start, end);
            if (hit.isEmpty()) continue;
            double current = start.distanceToSqr(hit.get());
            if (current < distance) {
                nearest = candidate;
                distance = current;
            }
        }
        return nearest;
    }

    private static void breakBlocks(ServerPlayer player, BlockPos center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (!player.serverLevel().getBlockState(pos).isAir()) player.gameMode.destroyBlock(pos);
                }
            }
        }
    }

    private static void refuel(Player player, ItemStack stack) {
        CompoundTag tag = data(stack);
        if (fuel(tag) > CAPACITY - 1_000) return;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (!candidate.is(ModItems.CANISTER_FULL.get())) continue;
            SourceFluidContainerItem.ContainedFluid type = SourceFluidContainerItem.fluid(candidate);
            if (!accepted(type)) continue;
            int room = CAPACITY - fuel(tag);
            tag.putInt(FUEL, fuel(tag) + Math.min(1_000, room));
            if (!player.getAbilities().instabuild) {
                candidate.shrink(1);
                ItemStack empty = new ItemStack(ModItems.CANISTER_EMPTY.get());
                if (!player.getInventory().add(empty)) player.drop(empty, false);
            }
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.GUN_PRESSURE_VALVE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            save(stack, tag);
            return;
        }
        playAnimation(tag, GunAnimation.INSPECT);
        save(stack, tag);
    }

    private static boolean accepted(SourceFluidContainerItem.ContainedFluid type) {
        return type == SourceFluidContainerItem.ContainedFluid.GASOLINE
                || type == SourceFluidContainerItem.ContainedFluid.GASOLINE_LEADED
                || type == SourceFluidContainerItem.ContainedFluid.COALGAS
                || type == SourceFluidContainerItem.ContainedFluid.COALGAS_LEADED;
    }

    private static void toggleAim(ItemStack stack) {
        CompoundTag tag = data(stack);
        tag.putBoolean(AIMING, !tag.getBoolean(AIMING));
        save(stack, tag);
    }

    public static ItemStack filledStack() {
        ItemStack stack = new ItemStack(ModItems.GUN_DRILL.get());
        CompoundTag tag = data(stack);
        tag.putInt(FUEL, CAPACITY);
        save(stack, tag);
        return stack;
    }
    public static int fuel(ItemStack stack) { return fuel(data(stack)); }
    private static int fuel(CompoundTag tag) { return Mth.clamp(tag.getInt(FUEL), 0, CAPACITY); }
    public static boolean aiming(ItemStack stack) { return data(stack).getBoolean(AIMING); }
    public static boolean primaryHeld(ItemStack stack) { return data(stack).getBoolean(PRIMARY_HELD); }
    public static GunAnimation animation(ItemStack stack) { return animation(data(stack)); }
    public static int animationTimer(ItemStack stack) { return data(stack).getInt(ANIM_TIMER); }
    public static GunState state(ItemStack stack) { return state(data(stack)); }

    public static void setTestState(ItemStack stack, GunState state, int timer, int fuel, boolean held) {
        CompoundTag tag = data(stack);
        setState(tag, state);
        tag.putInt(TIMER, timer);
        tag.putInt(FUEL, Mth.clamp(fuel, 0, CAPACITY));
        tag.putBoolean(PRIMARY_HELD, held);
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
            tag.putInt(FUEL, 0);
        }
        return tag;
    }
    private static void save(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("gui.weapon.ammo").append(": " + fuel(stack)
                + " / " + CAPACITY + " mB").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.baseDamage").append(": " + (int) DAMAGE)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.weapon.quality.utility").withStyle(ChatFormatting.YELLOW));
    }

    public enum GunState { DRAWING, IDLE, COOLDOWN }
    public enum GunAnimation { EQUIP, CYCLE, CYCLE_DRY, INSPECT }
}
