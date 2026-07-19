package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.FensuBlockEntity;
import com.hbm.ntm.energy.HeReceiver;
import com.hbm.ntm.item.HeBatteryItem;
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
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;

public final class FensuMenu extends AbstractContainerMenu {
    private final Container inventory;
    @Nullable private final FensuBlockEntity fensu;

    public FensuMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(id, playerInventory, findFensu(playerInventory, buffer.readBlockPos()), true);
    }

    public FensuMenu(int id, Inventory playerInventory, FensuBlockEntity fensu) {
        this(id, playerInventory, fensu, true);
    }

    private FensuMenu(int id, Inventory playerInventory, @Nullable FensuBlockEntity fensu, boolean ignored) {
        super(ModMenus.FENSU.get(), id);
        this.fensu = fensu;
        this.inventory = fensu == null ? new SimpleContainer(2) : fensu;
        checkContainerSize(inventory, 2);
        addSlot(new BatterySlot(inventory, 0, 26, 53));
        addSlot(new BatterySlot(inventory, 1, 80, 53));
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 99 + row * 18));
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 157));
        }
    }

    @Nullable
    private static FensuBlockEntity findFensu(Inventory inventory, BlockPos position) {
        return inventory.player.level().getBlockEntity(position) instanceof FensuBlockEntity fensu ? fensu : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof HeBatteryItem) {
            if (!moveItemStackTo(stack, 0, 2, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (fensu == null) return false;
        return switch (id) {
            case 0 -> { fensu.cycleLowMode(); yield true; }
            case 1 -> { fensu.cycleHighMode(); yield true; }
            case 2 -> { fensu.cyclePriority(); yield true; }
            default -> false;
        };
    }

    @Override public boolean stillValid(Player player) { return inventory.stillValid(player); }
    public BigInteger power() { return fensu == null ? BigInteger.ZERO : fensu.storedPower(); }
    public BigInteger delta() { return fensu == null ? BigInteger.ZERO : fensu.delta(); }
    public int lowMode() { return fensu == null ? FensuBlockEntity.MODE_INPUT : fensu.lowMode(); }
    public int highMode() { return fensu == null ? FensuBlockEntity.MODE_OUTPUT : fensu.highMode(); }
    public int priority() { return fensu == null ? HeReceiver.ConnectionPriority.LOW.ordinal() : fensu.getPriority().ordinal(); }

    private static final class BatterySlot extends Slot {
        private BatterySlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof HeBatteryItem; }
        @Override public int getMaxStackSize() { return 1; }
    }
}
