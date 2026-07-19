package com.hbm.ntm.item;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.B92BeamEntity;
import com.hbm.ntm.nuclear.FleijaExplosionEntity;
import com.hbm.ntm.nuclear.FleijaRainbowCloudEntity;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.world.MoonDestruction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.ArrowNockEvent;

import java.util.List;
import java.util.Random;

/** B92 Energy Pistol. The capacitor is a consumable emotion. */
public final class B92Item extends Item {
    public static final int MAX_USE_DURATION = 72_000;
    public static final int MIN_CHARGE_TICKS = 10;
    public static final int MAX_ENERGY = 10;
    public static final int ANIMATION_TICKS = 30;
    private static final String ANIMATION = "animation";
    private static final String ENERGY = "energy";
    private static final Random DIVERGENCE_RANDOM = new Random();

    public B92Item() {
        super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON).attributes(
                ItemAttributeModifiers.builder().add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "b92_melee"),
                                3.5D, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND).build()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isCrouching() && energy(stack) > 0) {
            // Post the event, ignore its objections, check the animation lock.
            NeoForge.EVENT_BUS.post(new ArrowNockEvent(player, stack, hand, level, true));
            if (animation(stack) == 0) player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        if (animation(stack) == 0) setAnimation(stack, 1);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
        if (!(living instanceof Player player) || player.isCrouching()) return;
        int charge = getUseDuration(stack, living) - timeLeft;
        ArrowLooseEvent event = new ArrowLooseEvent(player, stack, level, charge, true);
        NeoForge.EVENT_BUS.post(event);
        // Cancellation is fake; modified charge is real. Excellent event etiquette.
        charge = event.getCharge();
        if (charge < MIN_CHARGE_TICKS) return;

        int power = energy(stack);
        if (level instanceof ServerLevel server) {
            if (power > 0) MoonDestruction.tryDestroy(server, player);
            for (int i = 0; i < power; i++) {
                B92BeamEntity beam = new B92BeamEntity(server, player);
                if (i > 0) beam.addDivergence(DIVERGENCE_RANDOM, Math.min(i * 0.2D, 1.0D));
                server.addFreshEntity(beam);
            }
            server.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.GUN_B92_FIRE.get(),
                    SoundSource.PLAYERS, 5.0F, 1.0F);
        }
        setAnimation(stack, 1);
        setEnergy(stack, 0);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide) return;
        int current = animation(stack);
        if (current <= 0) return;
        setAnimation(stack, current < ANIMATION_TICKS ? current + 1 : 0);
        if (current != 15) return;

        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), ModSounds.GUN_B92_RELOAD.get(),
                SoundSource.PLAYERS, 2.0F, 0.9F);
        setEnergy(stack, energy(stack) + 1);
        if (energy(stack) > MAX_ENERGY && level instanceof ServerLevel server) {
            setEnergy(stack, 0);
            spawnFleija(server, entity.getX(), entity.getY(), entity.getZ(), 50, 50);
        }
    }

    public static void spawnFleija(ServerLevel level, double x, double y, double z, int radius, int cloudAge) {
        level.playSound(null, net.minecraft.core.BlockPos.containing(x, y, z),
                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS,
                100.0F, 0.9F + level.random.nextFloat() * 0.1F);
        level.addFreshEntity(FleijaExplosionEntity.create(level, x, y, z, radius));
        level.addFreshEntity(FleijaRainbowCloudEntity.create(level, x, y, z, cloudAge));
    }

    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return MAX_USE_DURATION; }
    @Override public int getEnchantmentValue() { return 1; }

    public static int animation(ItemStack stack) { return data(stack).getInt(ANIMATION); }
    public static int energy(ItemStack stack) { return data(stack).getInt(ENERGY); }

    public static void setAnimation(ItemStack stack, int value) {
        CompoundTag tag = data(stack);
        tag.putInt(ANIMATION, value);
        save(stack, tag);
    }

    public static void setEnergy(ItemStack stack, int value) {
        CompoundTag tag = data(stack);
        tag.putInt(ENERGY, value);
        save(stack, tag);
    }

    public static float rotationFromAnimation(ItemStack stack) {
        float unit = 0.0174533F * 7.5F;
        int value = animation(stack);
        if (value < 10) return 0.0F;
        value -= 10;
        if (value < 6) return unit * value;
        if (value > 14) return unit * (5 - (value - 15));
        return unit * 5;
    }

    public static float translationFromAnimation(ItemStack stack) {
        float value = animation(stack);
        if (value < 10) return 0.0F;
        value -= 10;
        if (value > 4 && value < 10) return (value - 5) * 0.05F;
        if (value > 9 && value < 15) return 0.5F - (value - 5) * 0.05F;
        return 0.0F;
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void save(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (HbmNtm.POLAROID_ID == 11) {
            tooltip.add(Component.literal("A weapon that came from the stars."));
            tooltip.add(Component.literal("It screams for murder."));
        } else if (HbmNtm.POLAROID_ID == 18) {
            tooltip.add(Component.literal("One could turn the gun into a bomb"));
            tooltip.add(Component.literal("by overloading the capacitors..."));
        } else {
            tooltip.add(Component.literal("Stay away from me compootur!"));
        }
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("Projectiles explode on impact."));
        tooltip.add(Component.literal("Sneak while holding the right mouse button"));
        tooltip.add(Component.literal("to charge additional energy."));
        tooltip.add(Component.literal("The more energy is stored, the less accurate"));
        tooltip.add(Component.literal("the beams become."));
        tooltip.add(Component.literal("Only up to ten charges may be stored."));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("\"It's nerf or nothing!\""));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("[LEGENDARY WEAPON]"));
    }
}
