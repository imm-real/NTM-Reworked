package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.BrickFurnaceBlock;
import com.hbm.ntm.inventory.BrickFurnaceMenu;
import com.hbm.ntm.item.AshItem;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.thermal.FireboxFuel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Bricked Furnace burn ordering, accelerated inputs, ash ledger, and sided slots. */
public final class BrickFurnaceBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int INPUT = 0;
    public static final int FUEL = 1;
    public static final int OUTPUT = 2;
    public static final int ASH_OUTPUT = 3;
    public static final int SLOT_COUNT = 4;
    public static final int MAX_PROGRESS = 200;
    public static final int ASH_THRESHOLD = 2_000;
    public static final int DATA_COUNT = 3;
    private static final int[] TOP_SLOTS = {INPUT};
    private static final int[] BOTTOM_SLOTS = {OUTPUT, FUEL, ASH_OUTPUT};
    private static final int[] SIDE_SLOTS = {FUEL};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> burnTime;
                case 1 -> maxBurnTime;
                case 2 -> progress;
                default -> 0;
            };
        }

        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> burnTime = value;
                case 1 -> maxBurnTime = value;
                case 2 -> progress = value;
                default -> { }
            }
        }

        @Override public int getCount() { return DATA_COUNT; }
    };

    private Component customName;
    private int burnTime;
    private int maxBurnTime;
    private int progress;
    // These three accumulators vanish on reload by design.
    private int ashLevelWood;
    private int ashLevelCoal;
    private int ashLevelMisc;

    public BrickFurnaceBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_FURNACE_BRICK.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, BrickFurnaceBlockEntity furnace) {
        if (!level.isClientSide) furnace.serverTick((ServerLevel) level, position);
    }

    private void serverTick(ServerLevel level, BlockPos position) {
        boolean wasBurning = burnTime > 0;
        if (burnTime > 0) burnTime--;

        if (burnTime != 0 || !items.get(FUEL).isEmpty() && !items.get(INPUT).isEmpty()) {
            if (burnTime == 0 && canSmelt()) startFuel();

            if (burnTime > 0 && canSmelt()) {
                progress += burnSpeed(items.get(INPUT));
                if (progress >= MAX_PROGRESS) {
                    progress = 0;
                    smeltItem();
                }
            } else {
                progress = 0;
            }
        }

        boolean burning = burnTime > 0;
        if (wasBurning != burning) setLit(level, position, burning);
        setChanged();
    }

    private void startFuel() {
        ItemStack fuelStack = items.get(FUEL);
        int fuelTime = FireboxFuel.rawBurnTime(fuelStack);
        if (fuelTime <= 0) return;

        maxBurnTime = burnTime = fuelTime;
        ItemStack consumed = fuelStack.copyWithCount(1);
        fuelStack.shrink(1);
        addAsh(FireboxFuel.ashType(consumed), burnTime);
        if (processAsh(ashLevelWood, AshItem.AshType.WOOD)) ashLevelWood -= ASH_THRESHOLD;
        if (processAsh(ashLevelCoal, AshItem.AshType.COAL)) ashLevelCoal -= ASH_THRESHOLD;
        if (processAsh(ashLevelMisc, AshItem.AshType.MISC)) ashLevelMisc -= ASH_THRESHOLD;

        if (fuelStack.isEmpty()) items.set(FUEL, consumed.getCraftingRemainingItem());
    }

    private void addAsh(AshItem.AshType type, int amount) {
        if (type == AshItem.AshType.WOOD) ashLevelWood += amount;
        else if (type == AshItem.AshType.COAL) ashLevelCoal += amount;
        else ashLevelMisc += amount;
    }

    private boolean processAsh(int level, AshItem.AshType type) {
        if (level < ASH_THRESHOLD) return false;
        ItemStack output = items.get(ASH_OUTPUT);
        if (output.isEmpty()) {
            items.set(ASH_OUTPUT, AshItem.create(ModItems.POWDER_ASH.get(), type));
            return true;
        }
        if (output.is(ModItems.POWDER_ASH.get()) && AshItem.type(output) == type
                && output.getCount() < output.getMaxStackSize()) {
            output.grow(1);
            return true;
        }
        return false;
    }

    public static int burnSpeed(ItemStack input) {
        if (input.is(Items.CLAY_BALL) || input.is(ModItems.BALL_FIRECLAY.get())
                || input.is(Items.NETHERRACK)) return 4;
        if (input.is(Items.COBBLESTONE) || input.is(Items.SAND) || input.is(ItemTags.LOGS)) return 2;
        return 1;
    }

    public boolean canSmelt() {
        ItemStack result = smeltingResult(items.get(INPUT));
        if (items.get(INPUT).isEmpty() || result.isEmpty()) return false;
        ItemStack output = items.get(OUTPUT);
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= Math.min(getMaxStackSize(), output.getMaxStackSize());
    }

    private ItemStack smeltingResult(ItemStack input) {
        if (level == null || input.isEmpty()) return ItemStack.EMPTY;
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, recipeInput, level)
                .map(holder -> holder.value().assemble(recipeInput, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    private void smeltItem() {
        if (!canSmelt()) return;
        ItemStack result = smeltingResult(items.get(INPUT));
        if (items.get(OUTPUT).isEmpty()) items.set(OUTPUT, result.copy());
        else items.get(OUTPUT).grow(result.getCount());
        removeItem(INPUT, 1);
    }

    private void setLit(ServerLevel level, BlockPos position, boolean lit) {
        BlockState current = level.getBlockState(position);
        if (current.hasProperty(BrickFurnaceBlock.LIT) && current.getValue(BrickFurnaceBlock.LIT) != lit) {
            level.setBlock(position, current.setValue(BrickFurnaceBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    public int burnTime() { return burnTime; }
    public int maxBurnTime() { return maxBurnTime; }
    public int progress() { return progress; }
    public int ashLevel(AshItem.AshType type) {
        return type == AshItem.AshType.WOOD ? ashLevelWood
                : type == AshItem.AshType.COAL ? ashLevelCoal : ashLevelMisc;
    }
    public ContainerData dataAccess() { return data; }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("burnTime", burnTime);
        tag.putInt("maxBurn", maxBurnTime);
        tag.putInt("progress", progress);
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        burnTime = tag.getInt("burnTime");
        maxBurnTime = tag.getInt("maxBurn");
        progress = tag.getInt("progress");
        customName = tag.contains("name")
                ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.furnaceBrick");
    }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new BrickFurnaceMenu(id, inventory, this, data);
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getCenter()) <= 64.0D;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == INPUT) return true;
        return slot == FUEL && FireboxFuel.rawBurnTime(stack) > 0;
    }

    @Override public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) return BOTTOM_SLOTS.clone();
        if (side == Direction.UP) return TOP_SLOTS.clone();
        return SIDE_SLOTS.clone();
    }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= OUTPUT;
    }
}
