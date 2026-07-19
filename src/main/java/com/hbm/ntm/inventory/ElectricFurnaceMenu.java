package com.hbm.ntm.inventory;

import com.hbm.ntm.blockentity.ElectricFurnaceBlockEntity;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.item.MachineUpgradeItem;
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

/** Source four-slot Electric Furnace layout and transfer routing. */
public final class ElectricFurnaceMenu extends AbstractContainerMenu {
    private final Container furnace;
    private final ContainerData data;

    public ElectricFurnaceMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()),
                new SimpleContainerData(ElectricFurnaceBlockEntity.DATA_COUNT));
    }

    public ElectricFurnaceMenu(int id, Inventory inventory, Container furnace, ContainerData data) {
        super(ModMenus.MACHINE_ELECTRIC_FURNACE.get(), id);
        checkContainerSize(furnace, ElectricFurnaceBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, ElectricFurnaceBlockEntity.DATA_COUNT);
        this.furnace = furnace;
        this.data = data;

        addSlot(new RestrictedSlot(furnace, ElectricFurnaceBlockEntity.BATTERY, 152, 54));
        addSlot(new RestrictedSlot(furnace, ElectricFurnaceBlockEntity.INPUT, 20, 35));
        addSlot(new ElectricResultSlot(inventory.player, furnace, ElectricFurnaceBlockEntity.OUTPUT, 80, 35));
        addSlot(new RestrictedSlot(furnace, ElectricFurnaceBlockEntity.UPGRADE, 111, 34));

        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 104 + row * 18));
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 162));
        }
        addDataSlots(data);
    }

    private static Container find(Inventory inventory, BlockPos position) {
        return inventory.player.level().getBlockEntity(position) instanceof ElectricFurnaceBlockEntity furnace
                ? furnace : new SimpleContainer(ElectricFurnaceBlockEntity.SLOT_COUNT);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < ElectricFurnaceBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(stack, ElectricFurnaceBlockEntity.SLOT_COUNT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            if (index == ElectricFurnaceBlockEntity.OUTPUT) slot.onQuickCraft(stack, copy);
        } else if (stack.getItem() instanceof HeBatteryItem) {
            if (!moveItemStackTo(stack, ElectricFurnaceBlockEntity.BATTERY,
                    ElectricFurnaceBlockEntity.BATTERY + 1, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof MachineUpgradeItem) {
            if (!moveItemStackTo(stack, ElectricFurnaceBlockEntity.UPGRADE,
                    ElectricFurnaceBlockEntity.UPGRADE + 1, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, ElectricFurnaceBlockEntity.INPUT,
                ElectricFurnaceBlockEntity.INPUT + 1, false)) return ItemStack.EMPTY;

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return furnace.stillValid(player); }
    public int progress() { return data.get(0); }
    public int maxProgress() { return data.get(1); }
    public long power() { return (data.get(2) & 0xFFFFFFFFL) | (long) data.get(3) << 32; }
    public int consumption() { return data.get(4); }
    public boolean lit() { return data.get(5) != 0; }
    public boolean hasPower() { return power() >= consumption(); }

    private final class RestrictedSlot extends Slot {
        private RestrictedSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) {
            return furnace.canPlaceItem(getContainerSlot(), stack);
        }
    }

    /** Output-count XP and smelt hooks, exactly where shift-click can find them. */
    private static final class ElectricResultSlot extends Slot {
        private final Player player;
        private int removeCount;

        private ElectricResultSlot(Player player, Container container, int slot, int x, int y) {
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
                if (experience > 0) {
                    ExperienceOrb.award(level, player.position().add(0.0D, 0.5D, 0.0D), experience);
                }
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
            if (rounded < Mth.ceil(exact) && level.random.nextFloat() < exact - rounded) rounded++;
            return rounded;
        }
    }
}
