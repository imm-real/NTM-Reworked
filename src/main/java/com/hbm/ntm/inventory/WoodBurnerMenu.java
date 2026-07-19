package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.WoodBurnerBlockEntity;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class WoodBurnerMenu extends AbstractContainerMenu {
    private final Container burner;
    private final ContainerData data;

    public WoodBurnerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()),
                new SimpleContainerData(WoodBurnerBlockEntity.DATA_COUNT));
    }

    public WoodBurnerMenu(int id, Inventory inventory, Container burner, ContainerData data) {
        super(ModMenus.MACHINE_WOOD_BURNER.get(), id);
        checkContainerSize(burner, WoodBurnerBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, WoodBurnerBlockEntity.DATA_COUNT);
        this.burner = burner;
        this.data = data;

        addSlot(new Slot(burner, WoodBurnerBlockEntity.SOLID_FUEL, 26, 18) {
            @Override public boolean mayPlace(ItemStack stack) {
                return WoodBurnerBlockEntity.burnTime(stack) > 0;
            }
        });
        addSlot(new OutputSlot(burner, WoodBurnerBlockEntity.ASH_OUTPUT, 26, 54));
        addSlot(new Slot(burner, WoodBurnerBlockEntity.FLUID_IDENTIFIER, 98, 54) {
            @Override public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof FluidIdentifierItem;
            }
        });
        addSlot(new Slot(burner, WoodBurnerBlockEntity.FLUID_INPUT, 98, 18) {
            @Override public boolean mayPlace(ItemStack stack) {
                return WoodBurnerBlockEntity.isFluidContainer(stack);
            }
        });
        addSlot(new OutputSlot(burner, WoodBurnerBlockEntity.FLUID_OUTPUT, 98, 36));
        addSlot(new Slot(burner, WoodBurnerBlockEntity.BATTERY, 143, 54) {
            @Override public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof HeBatteryItem;
            }
        });

        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 104 + row * 18));
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 162));
        }
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos position) {
        return inventory.player.level().getBlockEntity(position) instanceof WoodBurnerBlockEntity burner
                ? burner : new SimpleContainer(WoodBurnerBlockEntity.SLOT_COUNT);
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (!(burner instanceof WoodBurnerBlockEntity entity)) return false;
        if (id == 0) entity.toggleOn();
        else if (id == 1) entity.switchMode();
        else return false;
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < WoodBurnerBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(stack, WoodBurnerBlockEntity.SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof HeBatteryItem) {
            if (!moveItemStackTo(stack, WoodBurnerBlockEntity.BATTERY,
                    WoodBurnerBlockEntity.BATTERY + 1, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof FluidIdentifierItem) {
            if (!moveItemStackTo(stack, WoodBurnerBlockEntity.FLUID_IDENTIFIER,
                    WoodBurnerBlockEntity.FLUID_IDENTIFIER + 1, false)) return ItemStack.EMPTY;
        } else if (WoodBurnerBlockEntity.burnTime(stack) > 0) {
            if (!moveItemStackTo(stack, WoodBurnerBlockEntity.SOLID_FUEL,
                    WoodBurnerBlockEntity.SOLID_FUEL + 1, false)) return ItemStack.EMPTY;
        } else if (WoodBurnerBlockEntity.isFluidContainer(stack)) {
            if (!moveItemStackTo(stack, WoodBurnerBlockEntity.FLUID_INPUT,
                    WoodBurnerBlockEntity.FLUID_INPUT + 1, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return burner.stillValid(player); }

    public long power() { return data.get(0) & 0xFFFFFFFFL | (long) data.get(1) << 32; }
    public int burnTime() { return data.get(2); }
    public int maxBurnTime() { return data.get(3); }
    public int tankAmount() { return data.get(4); }
    public FluidIdentifierItem.Selection selectedFluid() {
        int ordinal = data.get(5);
        FluidIdentifierItem.Selection[] values = FluidIdentifierItem.Selection.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FluidIdentifierItem.Selection.NONE;
    }
    public boolean isOn() { return data.get(6) != 0; }
    public boolean liquidBurn() { return data.get(7) != 0; }
    public int powerGeneration() { return data.get(8); }

    private static final class OutputSlot extends Slot {
        private OutputSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
