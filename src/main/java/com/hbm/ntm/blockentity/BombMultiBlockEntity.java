package com.hbm.ntm.blockentity;

import com.hbm.ntm.inventory.BombMultiMenu;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Six slots, four TNT, two modifiers and absolutely no hopper privileges. */
public final class BombMultiBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    private static final int[] NO_AUTOMATION = new int[0];

    private final NonNullList<ItemStack> items = NonNullList.withSize(6, ItemStack.EMPTY);
    private Component customName;
    private boolean detonating;

    public BombMultiBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.BOMB_MULTI.get(), position, state);
    }

    /** Four corners of TNT make the safety inspector disappear. */
    public boolean isLoaded() {
        return items.get(0).is(Items.TNT) && items.get(1).is(Items.TNT)
                && items.get(3).is(Items.TNT) && items.get(4).is(Items.TNT);
    }

    public int return2type() {
        return typeOf(items.get(2));
    }

    public int return5type() {
        return typeOf(items.get(5));
    }

    /**
     * Source modifier-item to integer mapping: gunpowder=1, TNT=2, pellet_cluster=3, powder_fire=4,
     * powder_poison=5, pellet_gas=6, else 0. {@code pellet_cluster} (3) and {@code powder_poison}
     * (5) are not registered in this port, so those two types are unreachable by construction.
     */
    public static int typeOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        Item item = stack.getItem();
        if (item == Items.GUNPOWDER) {
            return 1;
        }
        if (item == Items.TNT) {
            return 2;
        }
        // pellet_cluster (3): ingredient unregistered in this port.
        if (item == ModItems.legacyOreResourceItem("powder_fire").get()) {
            return 4;
        }
        // powder_poison (5): ingredient unregistered in this port.
        if (item == ModItems.PELLET_GAS.get()) {
            return 6;
        }
        return 0;
    }

    public void clearSlots() {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        setChanged();
    }

    /** Empties the inventory and marks the block entity as detonating so its break drops nothing. */
    public void clearForDetonation() {
        detonating = true;
        clearSlots();
    }

    public boolean detonating() {
        return detonating;
    }

    /** Scatters all six stacks using the furnace-approved mess algorithm. */
    public void dropContents() {
        if (level == null) {
            return;
        }
        net.minecraft.world.Containers.dropContents(level, worldPosition, this);
        clearSlots();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        if (customName != null) {
            tag.putString("name", Component.Serializer.toJson(customName, registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override public void clearContent() { items.clear(); setChanged(); }

    // Source isItemValidForSlot() always returns false -- automation cannot insert. The GUI Slot's
    // own mayPlace() is unaffected, so manual drag placement still works.
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return false; }
    @Override public int[] getSlotsForFace(Direction side) { return NO_AUTOMATION; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }

    public void setCustomName(Component name) { customName = name; setChanged(); }

    @Override
    public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.bombMulti");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new BombMultiMenu(id, inventory, this);
    }
}
