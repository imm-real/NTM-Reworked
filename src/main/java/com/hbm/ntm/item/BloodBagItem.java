package com.hbm.ntm.item;

import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Five health goes into the bag; five health may eventually come back out. */
public final class BloodBagItem extends Item {
    public enum Type {
        EMPTY,
        BLOOD
    }

    private final Type type;

    public BloodBagItem(Type type) {
        super(new Properties());
        this.type = type;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.awardStat(Stats.ITEM_USED.get(this));
        }

        if (type == Type.EMPTY) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.SYRINGE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            float health = Math.max(player.getHealth() - 5.0F, 0.0F);
            player.setHealth(health);
            if (health <= 0.0F) {
                DamageSource source = player.damageSources().magic();
                player.die(source);
            }
            return InteractionResultHolder.consume(consumeAndReturn(stack, player,
                    new ItemStack(ModItems.IV_BLOOD.get())));
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.RADAWAY.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        player.heal(5.0F);
        return InteractionResultHolder.consume(consumeAndReturn(stack, player,
                new ItemStack(ModItems.IV_EMPTY.get())));
    }

    private static ItemStack consumeAndReturn(ItemStack stack, Player player, ItemStack container) {
        // ItemSimpleConsumable manually decrements this family even in creative mode.
        stack.shrink(1);
        if (stack.isEmpty()) return container;
        if (!player.getInventory().add(container)) player.drop(container, false);
        return stack;
    }

    public Type type() {
        return type;
    }
}
