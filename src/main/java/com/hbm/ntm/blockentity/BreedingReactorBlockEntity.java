package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.BreedingReactorBlock;
import com.hbm.ntm.inventory.BreedingReactorMenu;
import com.hbm.ntm.machine.BreederFluxProvider;
import com.hbm.ntm.recipe.BreederRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/** Two slots, borrowed neutron flux and absolutely no thermostat. */
public final class BreedingReactorBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    public static final int SLOT_COUNT = 2;
    public static final int[] AUTOMATION_SLOTS = {INPUT, OUTPUT};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private Component customName;
    private int flux;
    private float progress;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) { case 0 -> flux; case 1 -> Float.floatToIntBits(progress); default -> 0; };
        }
        @Override public void set(int index, int value) {
            if (index == 0) flux = value;
            else if (index == 1) progress = Float.intBitsToFloat(value);
        }
        @Override public int getCount() { return 2; }
    };

    public BreedingReactorBlockEntity(BlockPos pos, BlockState state) {
        super(registeredType(), pos, state);
    }

    @SuppressWarnings("unchecked")
    private static BlockEntityType<BreedingReactorBlockEntity> registeredType() {
        return (BlockEntityType<BreedingReactorBlockEntity>) (BlockEntityType<?>)
                BuiltInRegistries.BLOCK_ENTITY_TYPE.get(BreedingReactorBlock.CORE_BE_ID);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BreedingReactorBlockEntity reactor) {
        if (!level.isClientSide) reactor.serverTick((ServerLevel) level, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        applyFlux(adjacentFlux(level, pos));
        setChanged();
        // The GUI expects fresh flux every tick.
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    private void applyFlux(int suppliedFlux) {
        flux = suppliedFlux;
        BreederRecipes.Recipe recipe = BreederRecipes.get(items.get(INPUT));
        if (canProcess(recipe)) {
            progress += BreederRecipes.progressPerTick(flux, recipe.flux());
            if (progress >= 1.0F) {
                progress = 0.0F;
                process(recipe);
            }
        } else {
            progress = 0.0F;
        }
    }

    /** Test hook. Real ticks borrow flux from the research reactor next door. */
    public void testTickWithFlux(int suppliedFlux) { applyFlux(suppliedFlux); }

    public static int adjacentFlux(Level level, BlockPos pos) {
        int total = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (level.getBlockEntity(pos.relative(direction)) instanceof BreederFluxProvider provider) {
                total += provider.hbm$breederFlux();
            }
        }
        return total;
    }

    private boolean canProcess(@Nullable BreederRecipes.Recipe recipe) {
        if (recipe == null || flux < recipe.flux()) return false;
        ItemStack output = items.get(OUTPUT);
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, recipe.output())
                && output.getCount() < output.getMaxStackSize();
    }

    private void process(BreederRecipes.Recipe recipe) {
        if (!canProcess(recipe)) return;
        ItemStack output = items.get(OUTPUT);
        if (output.isEmpty()) items.set(OUTPUT, recipe.output().copy());
        else output.grow(recipe.output().getCount());
        items.get(INPUT).shrink(1);
        if (items.get(INPUT).isEmpty()) items.set(INPUT, ItemStack.EMPTY);
    }

    public int flux() { return flux; }
    public float progress() { return progress; }
    public int progressScaled(int width) { return (int) (progress * width); }
    public AABB renderBounds() {
        return new AABB(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                worldPosition.getX() + 1, worldPosition.getY() + 3, worldPosition.getZ() + 1);
    }
    public void setCustomName(Component name) { customName = name; setChanged(); }

    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.reactorBreeding");
    }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new BreedingReactorMenu(id, inventory, this, data);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("flux", flux);
        tag.putFloat("progress", progress);
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        flux = tag.getInt("flux");
        progress = tag.getFloat("progress");
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag(); tag.putInt("flux", flux); tag.putFloat("progress", progress); return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        flux = tag.getInt("flux"); progress = tag.getFloat("progress");
    }
    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack result = ContainerHelper.removeItem(items, slot, count); if (!result.isEmpty()) setChanged(); return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack); if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize()); setChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getCenter()) <= 128.0D;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public int[] getSlotsForFace(Direction side) { return AUTOMATION_SLOTS; }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot == INPUT; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot == INPUT;
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return slot == OUTPUT; }
}
