package com.hbm.ntm.item;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.entity.B93BeamEntity;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.world.SunDestruction;
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
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.ArrowNockEvent;

import java.util.List;

/** B93 Energy Mod, recovered from history like a cursed save file. */
public final class B93Item extends Item {
    public static final int MAX_USE_DURATION = 72_000;
    public static final int MIN_CHARGE_TICKS = 10;
    public static final int MAX_ENERGY = 10;
    public static final int ANIMATION_TICKS = 30;

    public B93Item() {
        super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON).attributes(
                ItemAttributeModifiers.builder().add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "b93_melee"),
                                3.5D, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND).build()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isCrouching() && energy(stack) > 0) {
            // Announce the event, then ignore every attempt to stop it.
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
        // Cancellation fake, modified charge real. Same excellent manners as B92.
        charge = event.getCharge();
        if (charge < MIN_CHARGE_TICKS) return;

        int power = energy(stack);
        if (level instanceof ServerLevel server) {
            if (power > 0) SunDestruction.tryDestroy(server, player);
            B93BeamEntity beam = new B93BeamEntity(server, player);
            beam.setMode(power - 1);
            server.addFreshEntity(beam);
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
            B92Item.spawnFleija(server, entity.getX(), entity.getY(), entity.getZ(), 50, 50);
        }
    }

    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return MAX_USE_DURATION; }
    @Override public int getEnchantmentValue() { return 1; }

    // B92 and B93 share these keys because paperwork is expensive.
    public static int animation(ItemStack stack) { return B92Item.animation(stack); }
    public static int energy(ItemStack stack) { return B92Item.energy(stack); }
    public static void setAnimation(ItemStack stack, int value) { B92Item.setAnimation(stack, value); }
    public static void setEnergy(ItemStack stack, int value) { B92Item.setEnergy(stack, value); }
    public static float rotationFromAnimation(ItemStack stack) { return B92Item.rotationFromAnimation(stack); }
    public static float translationFromAnimation(ItemStack stack) { return B92Item.translationFromAnimation(stack); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("[LEGENDARY WEAPON]"));
    }
}
