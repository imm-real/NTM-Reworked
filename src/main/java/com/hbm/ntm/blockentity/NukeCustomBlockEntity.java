package com.hbm.ntm.blockentity;

import com.hbm.ntm.inventory.NukeCustomMenu;
import com.hbm.ntm.nuclear.CustomNukeExplosion;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Twenty-seven manual-only slots. Hoppers are not licensed nuclear technicians. */
public final class NukeCustomBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SLOTS = 27;
    private static final int[] NO_AUTOMATION = new int[0];

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private Component customName;
    private boolean detonating;

    public NukeCustomBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.NUKE_CUSTOM.get(), position, state);
    }

    /** Recount the bomb whenever somebody asks how doomed they are. */
    public CustomNukeExplosion.Yields yields() {
        return CustomNukeExplosion.computeYields(items);
    }

    /** TODO falling upgrade; current bombs respect parking regulations */
    public boolean isFalling() {
        return false;
    }

    public void clearForDetonation() {
        detonating = true;
        for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
        setChanged();
    }

    public boolean detonating() {
        return detonating;
    }

    /** Source {@code breakBlock}: furnace-style scatter of every slot when broken (not detonated). */
    public void dropContents() {
        if (level == null) return;
        Containers.dropContents(level, worldPosition, this);
        for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
    }

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
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }

    @Override public int getMaxStackSize() { return 64; }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override public void clearContent() { items.clear(); setChanged(); }

    // No sided slots makes this hopper-proof. Menu slots remain happy to accept anything.
    @Override public int[] getSlotsForFace(Direction side) { return NO_AUTOMATION; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }

    public void setCustomName(Component name) { customName = name; setChanged(); }

    @Override
    public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.nukeCustom");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new NukeCustomMenu(id, inventory, this);
    }
}
