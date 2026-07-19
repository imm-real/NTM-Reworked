package com.hbm.ntm.inventory;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.BreedingReactorBlockEntity;
import com.hbm.ntm.item.BreedingRodItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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

public final class BreedingReactorMenu extends AbstractContainerMenu {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "machine_reactor_breeding");
    private final Container reactor;
    private final ContainerData data;

    public BreedingReactorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(2));
    }
    public BreedingReactorMenu(int id, Inventory inventory, Container reactor, ContainerData data) {
        super(registeredType(), id);
        checkContainerSize(reactor, 2); checkContainerDataCount(data, 2);
        this.reactor = reactor; this.data = data;
        addSlot(new Slot(reactor, BreedingReactorBlockEntity.INPUT, 35, 35));
        addSlot(new OutputSlot(reactor, BreedingReactorBlockEntity.OUTPUT, 125, 35));
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
        for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        addDataSlots(data);
    }

    @SuppressWarnings("unchecked") private static MenuType<BreedingReactorMenu> registeredType() {
        return (MenuType<BreedingReactorMenu>) BuiltInRegistries.MENU.get(ID);
    }
    private static Container find(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof BreedingReactorBlockEntity reactor
                ? reactor : new SimpleContainer(2);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(); ItemStack copy = stack.copy();
        // Keep the <=2 boundary, including its first-player-slot oddity.
        if (index <= 2) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof BreedingRodItem) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack); return copy;
    }
    @Override public boolean stillValid(Player player) { return reactor.stillValid(player); }
    public int flux() { return data.get(0); }
    public float progress() { return Float.intBitsToFloat(data.get(1)); }

    private static final class OutputSlot extends Slot {
        private OutputSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
