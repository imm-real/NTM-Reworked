package com.hbm.ntm.inventory;

import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class FluidIdentifierMenu extends AbstractContainerMenu {
    private final InteractionHand hand;
    private final Player player;

    public FluidIdentifierMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readEnum(InteractionHand.class));
    }

    public FluidIdentifierMenu(int id, Inventory inventory, InteractionHand hand) {
        super(ModMenus.FLUID_IDENTIFIER.get(), id);
        this.hand = hand;
        this.player = inventory.player;
    }

    public InteractionHand hand() { return hand; }
    public ItemStack identifier() { return player.getItemInHand(hand); }
    public FluidIdentifierItem.Selection primary() { return FluidIdentifierItem.primary(identifier()); }
    public FluidIdentifierItem.Selection secondary() { return FluidIdentifierItem.secondary(identifier()); }

    public boolean setSelection(FluidIdentifierItem.Selection selection, boolean primary) {
        ItemStack stack = identifier();
        if (!stack.is(ModItems.FLUID_IDENTIFIER_MULTI.get())) return false;
        FluidIdentifierItem.set(stack, selection, primary);
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) {
        return player == this.player && identifier().is(ModItems.FLUID_IDENTIFIER_MULTI.get());
    }
}
