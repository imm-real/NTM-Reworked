package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.LargeNukeBlock;
import com.hbm.ntm.block.LargeNukeType;
import com.hbm.ntm.inventory.LargeNukeMenu;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class LargeNukeBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    private static final int[] NO_AUTOMATION = new int[0];
    private final LargeNukeType type;
    private final NonNullList<ItemStack> items;
    private Component customName;
    private boolean detonating;

    public LargeNukeBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.LARGE_NUKE.get(), position, state);
        type = state.getBlock() instanceof LargeNukeBlock bomb ? bomb.type() : LargeNukeType.GADGET;
        items = NonNullList.withSize(type.slots(), ItemStack.EMPTY);
    }

    public LargeNukeType type() { return type; }
    public boolean isReady() { return type.isReady(itemArray()); }
    public boolean isFilled() { return type.isFilled(itemArray()); }
    public int detonationRadius() { return type.detonationRadius(itemArray()); }

    public void clearForDetonation() {
        detonating = true;
        clearContent();
    }

    public boolean detonating() { return detonating; }

    public void dropContents() {
        if (level == null) return;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5D,
                        worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D, stack);
            }
        }
        clearContent();
    }

    private ItemStack[] itemArray() { return items.toArray(ItemStack[]::new); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
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
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > type.stackLimit()) stack.setCount(type.stackLimit());
        setChanged();
    }

    @Override public int getMaxStackSize() { return type.stackLimit(); }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public int[] getSlotsForFace(net.minecraft.core.Direction side) { return NO_AUTOMATION; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable net.minecraft.core.Direction side) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction side) { return false; }

    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Override public Component getDisplayName() { return customName != null ? customName : Component.translatable(type.containerKey()); }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new LargeNukeMenu(id, inventory, this);
    }
}
