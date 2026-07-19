package com.hbm.ntm.armor;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.item.DntArmorItem;
import com.hbm.ntm.mixin.ServerGamePacketListenerImplAccessor;
import com.hbm.ntm.network.DntJetpackStatePayload;
import com.hbm.ntm.radiation.RadiationClicker;
import com.hbm.ntm.radiation.RadiationSystem;
import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.registry.ModAttachments;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/** DNT armor power, protection and assorted resistance paperwork. */
public final class DntArmorEvents {
    public static final ResourceLocation SPRINT_SPEED_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "dnt_sprint_speed");
    private static final String LAST_STEP = "hbm.dntLastStep";
    private static final DustParticleOptions JET_DUST =
            new DustParticleOptions(new Vector3f(0.0F, 1.0F, 1.0F), 1.0F);

    private DntArmorEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(DntArmorEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, DntArmorEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(DntArmorEvents::onFall);
        NeoForge.EVENT_BUS.addListener(DntArmorEvents::onJump);
        NeoForge.EVENT_BUS.addListener(DntArmorEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(DntArmorEvents::onRespawn);
        NeoForge.EVENT_BUS.addListener(DntArmorEvents::onDimensionChange);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        applyTick(event.getEntity());
    }

    public static void applyTick(Player player) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (!player.level().isClientSide && speed != null) speed.removeModifier(SPRINT_SPEED_MODIFIER);
        if (!player.isAlive() || !DntArmorItem.hasFullPoweredSet(player)) return;

        if (!player.level().isClientSide) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20, 9, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 20, 7, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 20, 2, true, true));
            if (player.isSprinting() && speed != null) {
                speed.addOrUpdateTransientModifier(new AttributeModifier(
                        SPRINT_SPEED_MODIFIER, 0.25D, AttributeModifier.Operation.ADD_VALUE));
            }

            if (player instanceof ServerPlayer serverPlayer) {
                ((ServerGamePacketListenerImplAccessor) serverPlayer.connection)
                        .hbm$setAboveGroundTickCount(0);
                boolean jets = applyJetpackMovement(player,
                        player.getData(ModAttachments.DNT_JETPACK_ENABLED),
                        player.getData(ModAttachments.DNT_JETPACK_ACTIVE));
                if (jets) emitJets(serverPlayer);
                playMetalStep(serverPlayer);
                tickGeiger(serverPlayer);
                if (!player.hasInfiniteMaterials()) drainSet(player);
            }
        }
    }

    /** Shared with the client prediction path. Returns whether the exhaust should be active. */
    public static boolean applyJetpackMovement(Player player, boolean enabled, boolean jumpHeld) {
        Vec3 velocity = player.getDeltaMovement();
        if (enabled && jumpHeld) {
            player.setDeltaMovement(velocity.x, velocity.y < 0.6D ? velocity.y + 0.2D : velocity.y, velocity.z);
            player.resetFallDistance();
            return true;
        }

        if (enabled && !player.onGround() && !player.isCrouching()) {
            double y = velocity.y;
            if (y < -1.0D) y += 0.4D;
            else if (y < -0.1D) y += 0.2D;
            else if (y < 0.0D) y = 0.0D;

            double x = velocity.x * 1.05D;
            double z = velocity.z * 1.05D;
            if (player.zza != 0.0F) {
                Vec3 look = player.getLookAngle();
                x += look.x * 0.25D * player.zza;
                z += look.z * 0.25D * player.zza;
            }
            player.setDeltaMovement(x, y, z);
            player.resetFallDistance();
            return true;
        }

        if (!player.onGround() && player.isCrouching()) {
            player.setDeltaMovement(velocity.x, velocity.y - 0.1D, velocity.z);
        }
        return false;
    }

    private static void drainSet(Player player) {
        for (EquipmentSlot slot : new EquipmentSlot[] {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof DntArmorItem dnt) dnt.discharge(stack, DntArmorItem.IDLE_DRAIN);
        }
    }

    private static void emitJets(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize().scale(0.18D);
        double y = player.getY() + 0.12D;
        for (double sign : new double[] {-1.0D, 1.0D}) {
            level.sendParticles(JET_DUST, player.getX() + side.x * sign, y,
                    player.getZ() + side.z * sign, 1, 0.02D, 0.02D, 0.02D, 0.01D);
        }

        for (int down = 1; down <= 10; down++) {
            BlockPos position = player.blockPosition().below(down);
            BlockState state = level.getBlockState(position);
            if (state.isAir()) continue;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    player.getX(), position.getY() + 1.05D, player.getZ(),
                    3, 0.25D, 0.03D, 0.25D, 0.03D);
            break;
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.IMMOLATOR_SHOOT.get(), SoundSource.PLAYERS, 0.125F, 1.5F);
    }

    private static void playMetalStep(ServerPlayer player) {
        int current = (int) Math.floor(player.moveDist);
        int previous = player.getPersistentData().getInt(LAST_STEP);
        if (current <= previous) return;
        player.getPersistentData().putInt(LAST_STEP, current);
        if (player.onGround()) player.serverLevel().playSound(null, player.blockPosition(),
                ModSounds.STEP_METAL.get(), SoundSource.PLAYERS, 0.35F, 1.0F);
    }

    private static void tickGeiger(ServerPlayer player) {
        if (player.level().getGameTime() % 5L != 0L) return;
        boolean hasInstrument = player.getInventory().items.stream().anyMatch(stack ->
                stack.is(ModItems.GEIGER_COUNTER.get()) || stack.is(ModItems.DOSIMETER.get()));
        if (hasInstrument) return;
        float insideSuit = RadiationSystem.data(player).radBuf()
                * (float) Math.pow(10.0D, -RadiationSystem.calculateResistance(player));
        RadiationClicker.tickCarriedGeiger(player.level(), player, insideSuit);
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        float adjusted = applyDamageTable(event.getEntity(), event.getSource(), event.getAmount());
        if (adjusted <= 0.0F) {
            event.setAmount(0.0F);
            if (DntArmorItem.hasFullPoweredSet(event.getEntity())
                    && !event.getSource().is(DamageTypeTags.IS_EXPLOSION)
                    && event.getEntity().tickCount % 10 == 0) {
                event.getEntity().level().playSound(null, event.getEntity().blockPosition(),
                        SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.25F, 1.8F);
            }
        } else {
            event.setAmount(adjusted);
        }
    }

    public static float applyDamageTable(LivingEntity wearer, DamageSource source, float amount) {
        float adjusted = amount;
        if (source.is(ModDamageTypes.ELECTRIC) && DntArmorItem.hasChest(wearer)) adjusted *= 5.0F;

        boolean fullSet = DntArmorItem.hasFullSet(wearer);
        boolean powered = DntArmorItem.hasFullPoweredSet(wearer);
        boolean explosion = source.is(DamageTypeTags.IS_EXPLOSION);

        if (fullSet && !source.is(DamageTypeTags.BYPASSES_EFFECTS)) {
            adjusted = explosion ? Math.max(0.0F, adjusted - 100.0F) * 0.01F : 0.0F;
        }
        if (powered) {
            if (!explosion) return 0.0F;
            adjusted *= 0.001F;
        }
        return adjusted;
    }

    private static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player) || !DntArmorItem.hasFullPoweredSet(player)) return;
        if (!player.level().isClientSide) {
            player.level().playSound(null, player.blockPosition(), ModSounds.STEP_IRON_LAND.get(),
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            if (event.getDistance() > 10.0F) hardLanding(player);
        }
        event.setCanceled(true);
    }

    private static void hardLanding(Player player) {
        DamageSource damage = player.damageSources().source(ModDamageTypes.HARD_LANDING, player);
        for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(3.0D, 0.0D, 3.0D),
                entity -> !(entity instanceof ItemEntity))) {
            double dx = player.getX() - entity.getX();
            double dz = player.getZ() - entity.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance >= 3.0D) continue;
            double intensity = 3.0D - distance;
            entity.setDeltaMovement(entity.getDeltaMovement().add(
                    dx * intensity * -2.0D, 0.1D * intensity, dz * intensity * -2.0D));
            entity.hurt(damage, (float) (intensity * 10.0D));
        }
    }

    private static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player player && DntArmorItem.hasFullPoweredSet(player)) {
            player.level().playSound(null, player.blockPosition(), ModSounds.STEP_IRON_JUMP.get(),
                    SoundSource.PLAYERS, 0.5F, 1.0F);
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        sync(event.getEntity());
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        sync(event.getEntity());
    }

    private static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        sync(event.getEntity());
    }

    private static void sync(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer,
                    new DntJetpackStatePayload(
                            player.getData(ModAttachments.DNT_JETPACK_ENABLED),
                            player.getData(ModAttachments.DNT_HUD_ENABLED)));
        }
    }
}
