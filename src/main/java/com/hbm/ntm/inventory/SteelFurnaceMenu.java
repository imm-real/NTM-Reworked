package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.SteelFurnaceBlockEntity;
import com.hbm.ntm.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.event.EventHooks;

public final class SteelFurnaceMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 8;
    private static final int MACHINE_SLOT_COUNT = 6;
    private final Container furnace;
    private final ContainerData data;

    public SteelFurnaceMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()), new SimpleContainerData(DATA_COUNT));
    }

    public SteelFurnaceMenu(int id, Inventory inventory, Container furnace, ContainerData data) {
        super(ModMenus.FURNACE_STEEL.get(), id);
        checkContainerSize(furnace, SteelFurnaceBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, DATA_COUNT);
        this.furnace = furnace;
        this.data = data;

        addSlot(new Slot(furnace, 0, 35, 17));
        addSlot(new Slot(furnace, 1, 35, 35));
        addSlot(new Slot(furnace, 2, 35, 53));
        addSlot(new SteelResultSlot(inventory.player, furnace, 3, 125, 17));
        addSlot(new SteelResultSlot(inventory.player, furnace, 4, 125, 35));
        addSlot(new SteelResultSlot(inventory.player, furnace, 5, 125, 53));

        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos position) {
        return inventory.player.level().getBlockEntity(position) instanceof SteelFurnaceBlockEntity furnace
                ? furnace : new SimpleContainer(SteelFurnaceBlockEntity.SLOT_COUNT);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < MACHINE_SLOT_COUNT) {
            if (!moveItemStackTo(stack, MACHINE_SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
            if (index >= SteelFurnaceBlockEntity.LANE_COUNT) slot.onQuickCraft(stack, copy);
        } else if (!moveItemStackTo(stack, 0, SteelFurnaceBlockEntity.LANE_COUNT, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return furnace.stillValid(player); }
    public int progress(int lane) { return data.get(lane); }
    public int bonus(int lane) { return data.get(3 + lane); }
    public int heat() { return data.get(6); }
    public boolean wasOn() { return data.get(7) != 0; }

    /** Pays XP by output count; furnace recipe bookkeeping may file a complaint. */
    private static final class SteelResultSlot extends Slot {
        private final Player player;
        private int removeCount;

        private SteelResultSlot(Player player, Container container, int slot, int x, int y) {
            super(container, slot, x, y);
            this.player = player;
        }

        @Override public boolean mayPlace(ItemStack stack) { return false; }

        @Override public ItemStack remove(int amount) {
            if (hasItem()) removeCount += Math.min(amount, getItem().getCount());
            return super.remove(amount);
        }

        @Override protected void onQuickCraft(ItemStack stack, int amount) {
            removeCount += amount;
            checkTake(stack);
        }

        @Override public void onTake(Player player, ItemStack stack) {
            checkTake(stack);
            super.onTake(player, stack);
        }

        private void checkTake(ItemStack stack) {
            stack.onCraftedBy(player.level(), player, removeCount);
            if (player.level() instanceof ServerLevel level) {
                int experience = experienceToDrop(level, stack, removeCount);
                if (experience > 0) ExperienceOrb.award(level, player.position().add(0.0D, 0.5D, 0.0D), experience);
            }
            removeCount = 0;
            EventHooks.firePlayerSmeltedEvent(player, stack);
        }

        private static int experienceToDrop(ServerLevel level, ItemStack stack, int outputCount) {
            float experience = 0.0F;
            for (var holder : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
                ItemStack result = holder.value().getResultItem(level.registryAccess());
                if (ItemStack.isSameItem(result, stack)) {
                    experience = holder.value().getExperience();
                    break;
                }
            }
            if (experience == 0.0F) return 0;
            if (experience >= 1.0F) return outputCount;
            float exact = outputCount * experience;
            int rounded = Mth.floor(exact);
            if (rounded < Mth.ceil(exact) && playerRandom(level) < exact - rounded) rounded++;
            return rounded;
        }

        private static float playerRandom(ServerLevel level) {
            return level.random.nextFloat();
        }
    }
}
