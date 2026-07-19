package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.ResearchReactorBlockEntity;
import com.hbm.ntm.item.PlateFuelItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Twelve reactor slots and one shift-click trap. */
public final class ResearchReactorMenu extends AbstractContainerMenu {
    private static final int[][] ROD_POSITIONS = {
            {95, 22}, {131, 22}, {77, 40}, {113, 40}, {149, 40}, {95, 58},
            {131, 58}, {77, 76}, {113, 76}, {149, 76}, {95, 94}, {131, 94}
    };
    private final Container reactor;
    private final ContainerData data;

    public ResearchReactorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(5));
    }

    @SuppressWarnings("unchecked")
    public ResearchReactorMenu(int id, Inventory inventory, Container reactor, ContainerData data) {
        super((MenuType<ResearchReactorMenu>) BuiltInRegistries.MENU.get(ResearchReactorBlockEntity.MENU_ID), id);
        checkContainerSize(reactor, ResearchReactorBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, 5);
        this.reactor = reactor;
        this.data = data;
        for (int slot = 0; slot < ROD_POSITIONS.length; slot++) {
            int[] p = ROD_POSITIONS[slot];
            // Source used plain Slot: manual insertion deliberately ignores isItemValidForSlot.
            addSlot(new Slot(reactor, slot, p[0], p[1]));
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 140 + row * 18));
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 198));
        }
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof ResearchReactorBlockEntity reactor
                ? reactor : new SimpleContainer(ResearchReactorBlockEntity.SLOT_COUNT);
    }

    public int heat() { return data.get(0); }
    public int water() { return data.get(1); }
    public double level() { return data.get(2) / 10_000.0D; }
    public double targetLevel() { return data.get(3) / 10_000.0D; }
    public int totalFlux() { return data.get(4); }
    public int temperature() { return (int) Math.round(heat() * 0.00002D * 980.0D + 20.0D); }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(reactor instanceof ResearchReactorBlockEntity entity) || id < 0 || id > 100) return false;
        entity.setTarget(id * 0.01D);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        // Source uses index <= 12 even though machine slots end at 11. Preserve the first-player-slot quirk.
        if (index <= 12) {
            if (!moveItemStackTo(stack, 13, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!(stack.getItem() instanceof PlateFuelItem)
                    || !moveItemStackTo(stack, 0, ResearchReactorBlockEntity.SLOT_COUNT, true)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return reactor.stillValid(player); }
}
