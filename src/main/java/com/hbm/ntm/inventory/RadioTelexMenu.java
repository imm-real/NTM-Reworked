package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.RadioTelexBlockEntity;
import com.hbm.ntm.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class RadioTelexMenu extends AbstractContainerMenu {
    private final RadioTelexBlockEntity telex;

    public RadioTelexMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()));
    }

    public RadioTelexMenu(int id, Inventory inventory, RadioTelexBlockEntity telex) {
        super(ModMenus.RADIO_TELEX.get(), id);
        this.telex = telex;
    }

    private static RadioTelexBlockEntity find(Inventory inventory, BlockPos position) {
        return inventory.player.level().getBlockEntity(position) instanceof RadioTelexBlockEntity found
                ? found : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return telex != null && telex.stillValid(player);
    }

    public RadioTelexBlockEntity blockEntity() {
        return telex;
    }
}
