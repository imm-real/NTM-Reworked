package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.RadioAutocalBlockEntity;
import com.hbm.ntm.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class RadioAutocalMenu extends AbstractContainerMenu {
    private final RadioAutocalBlockEntity autocal;

    public RadioAutocalMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()));
    }

    public RadioAutocalMenu(int id, Inventory inventory, RadioAutocalBlockEntity autocal) {
        super(ModMenus.RADIO_AUTOCAL.get(), id);
        this.autocal = autocal;
    }

    private static RadioAutocalBlockEntity find(Inventory inventory, BlockPos position) {
        return inventory.player.level().getBlockEntity(position) instanceof RadioAutocalBlockEntity found
                ? found : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return autocal != null && autocal.stillValid(player);
    }

    public RadioAutocalBlockEntity blockEntity() {
        return autocal;
    }
}
