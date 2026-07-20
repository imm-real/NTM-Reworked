package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.SirenBlockEntity;
import com.hbm.ntm.item.SirenTrackItem;
import com.hbm.ntm.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class SirenMenu extends AbstractContainerMenu {
    private final Container siren;

    public SirenMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, findSiren(inventory, buffer.readBlockPos()));
    }

    public SirenMenu(int id, Inventory inventory, Container siren) {
        super(ModMenus.MACHINE_SIREN.get(), id);
        checkContainerSize(siren, 1);
        this.siren = siren;

        addSlot(new Slot(siren, 0, 8, 35) {
            @Override public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof SirenTrackItem;
            }
            @Override public int getMaxStackSize() { return 1; }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
    }

    private static Container findSiren(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof SirenBlockEntity siren
                ? siren : new SimpleContainer(1);
    }

    public SirenTrackItem.Track track() {
        return SirenTrackItem.track(siren.getItem(0));
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof SirenTrackItem) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return siren.stillValid(player); }
}
