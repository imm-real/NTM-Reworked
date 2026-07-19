package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.FluidStorageTankBlock;
import com.hbm.ntm.fluid.FluidTankProperties;
import com.hbm.ntm.inventory.FluidStorageTankMenu;
import com.hbm.ntm.item.BlowtorchItem;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.InfiniteFluidBarrelItem;
import com.hbm.ntm.pollution.PollutionData;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/** Large fluid tank with four opinions about where liquid should go. */
public final class FluidStorageTankBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int IDENTIFIER_INPUT = 0;
    public static final int IDENTIFIER_OUTPUT = 1;
    public static final int FILLED_INPUT = 2;
    public static final int EMPTY_OUTPUT = 3;
    public static final int EMPTY_INPUT = 4;
    public static final int FILLED_OUTPUT = 5;
    public static final int SLOT_COUNT = 6;
    public static final int CAPACITY = 256_000;

    public static final String ITEM_FLUID = "tankFluid";
    public static final String ITEM_AMOUNT = "tankAmount";
    public static final String ITEM_MODE = "tankMode";
    public static final String ITEM_DAMAGED = "tankDamaged";
    public static final String ITEM_ON_FIRE = "tankOnFire";

    public static final int MODE_INPUT = 0;
    public static final int MODE_BUFFER = 1;
    public static final int MODE_OUTPUT = 2;
    public static final int MODE_LOCKED = 3;
    private static final int[] NO_SLOTS = new int[0];

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private FluidIdentifierItem.Selection selection = FluidIdentifierItem.Selection.NONE;
    private final FluidTank tank = new FluidTank(CAPACITY,
            stack -> selection.accepts(stack.getFluid())) {
        @Override protected void onContentsChanged() { FluidStorageTankBlockEntity.this.setChanged(); }
    };
    private int mode;
    private boolean damaged;
    private boolean onFire;
    private boolean pushing;
    private Component customName;
    private Explosion lastExplosion;

    private int lastAmount = Integer.MIN_VALUE;
    private int lastMode = Integer.MIN_VALUE;
    private boolean lastDamaged;
    private boolean lastOnFire;
    private FluidIdentifierItem.Selection lastSelection;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> tank.getFluidAmount();
                case 1 -> selection.ordinal();
                case 2 -> mode;
                case 3 -> damaged ? 1 : 0;
                case 4 -> onFire ? 1 : 0;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            FluidIdentifierItem.Selection[] values = FluidIdentifierItem.Selection.values();
            switch (index) {
                case 1 -> selection = value >= 0 && value < values.length ? values[value]
                        : FluidIdentifierItem.Selection.NONE;
                case 2 -> mode = Math.clamp(value, MODE_INPUT, MODE_LOCKED);
                case 3 -> damaged = value != 0;
                case 4 -> onFire = value != 0;
                default -> { }
            }
        }
        @Override public int getCount() { return 5; }
    };

    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int index) {
            return index == 0 ? tank.getFluid().copy() : FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int index) { return index == 0 ? CAPACITY : 0; }
        @Override public boolean isFluidValid(int index, FluidStack stack) {
            return index == 0 && !damaged && !pushing && (mode == MODE_INPUT || mode == MODE_BUFFER)
                    && selection.accepts(stack.getFluid());
        }
        @Override public int fill(FluidStack resource, FluidAction action) {
            if (!isFluidValid(0, resource)) return 0;
            int rate = Math.max(500, (CAPACITY - tank.getFluidAmount()) / 100);
            FluidStack limited = resource.copyWithAmount(Math.min(resource.getAmount(), rate));
            return tank.fill(limited, action);
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            if (!canDrain() || resource.isEmpty() || tank.isEmpty()
                    || !resource.getFluid().isSame(tank.getFluid().getFluid())) return FluidStack.EMPTY;
            return tank.drain(resource.copyWithAmount(Math.min(resource.getAmount(), drainRate())), action);
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            return canDrain() ? tank.drain(Math.min(maxDrain, drainRate()), action) : FluidStack.EMPTY;
        }
        private boolean canDrain() {
            return !damaged && !pushing && (mode == MODE_BUFFER || mode == MODE_OUTPUT);
        }
        private int drainRate() { return Math.max(500, tank.getFluidAmount() / 100); }
    };

    public FluidStorageTankBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_FLUIDTANK.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, FluidStorageTankBlockEntity tank) {
        if (!level.isClientSide) tank.serverTick((ServerLevel) level, position, state);
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        processIdentifier();
        loadFromContainer();
        unloadToContainer();
        if ((mode == MODE_BUFFER || mode == MODE_OUTPUT) && !damaged) pushFluid(level, position, state);
        if (damaged && !tank.isEmpty()) leak(level, position);
        syncIfChanged(level, position, state);
    }

    private void processIdentifier() {
        ItemStack input = items.get(IDENTIFIER_INPUT);
        if (!(input.getItem() instanceof FluidIdentifierItem) || input.isEmpty()) return;
        ItemStack moved = input.copyWithCount(1);
        if (!canMerge(items.get(IDENTIFIER_OUTPUT), moved)) return;
        selectFluid(FluidIdentifierItem.primary(input));
        input.shrink(1);
        mergeInto(IDENTIFIER_OUTPUT, moved);
    }

    private void loadFromContainer() {
        if (damaged || selection == FluidIdentifierItem.Selection.NONE || tank.getSpace() <= 0) return;
        ItemStack input = items.get(FILLED_INPUT);
        if (input.isEmpty()) return;
        if (InfiniteFluidBarrelItem.is(input)) {
            if (InfiniteFluidBarrelItem.fillTank(tank, selection.fluid()) > 0) setChanged();
            return;
        }
        IFluidHandlerItem handler = input.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) return;
        FluidStack simulated = handler.drain(tank.getSpace(), IFluidHandler.FluidAction.SIMULATE);
        if (simulated.isEmpty() || !selection.accepts(simulated.getFluid())) return;
        int accepted = tank.fill(simulated, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) return;
        FluidStack drained = handler.drain(simulated.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) return;
        ItemStack result = handler.getContainer().copy();
        if (!canMerge(items.get(EMPTY_OUTPUT), result)) return;
        int filled = tank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0) return;
        input.shrink(1);
        mergeInto(EMPTY_OUTPUT, result);
    }

    private void unloadToContainer() {
        if (tank.isEmpty()) return;
        ItemStack input = items.get(EMPTY_INPUT);
        if (input.isEmpty()) return;
        if (InfiniteFluidBarrelItem.is(input)) {
            if (InfiniteFluidBarrelItem.discardTank(tank) > 0) setChanged();
            return;
        }
        if (input.getItem() instanceof BlowtorchItem) {
            refillBlowtorchInPlace(input);
            return;
        }
        IFluidHandlerItem handler = input.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) return;
        FluidStack available = tank.getFluid().copy();
        int accepted = handler.fill(available, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) return;
        int filled = handler.fill(available.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0) return;
        ItemStack result = handler.getContainer().copy();
        if (!canMerge(items.get(FILLED_OUTPUT), result)) return;
        tank.drain(filled, IFluidHandler.FluidAction.EXECUTE);
        input.shrink(1);
        mergeInto(FILLED_OUTPUT, result);
    }

    /** Dribbles 50 mB/t into a blowtorch without swapping the item. */
    private void refillBlowtorchInPlace(ItemStack input) {
        IFluidHandlerItem handler = input.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) return;
        FluidStack available = tank.getFluid().copy();
        int accepted = handler.fill(available, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) return;
        int filled = handler.fill(available.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0) return;
        tank.drain(filled, IFluidHandler.FluidAction.EXECUTE);
        items.set(EMPTY_INPUT, handler.getContainer().copy());
        setChanged();
    }

    private void pushFluid(ServerLevel level, BlockPos core, BlockState state) {
        if (tank.isEmpty()) return;
        Direction facing = state.getValue(FluidStorageTankBlock.FACING);
        Direction side = facing.getClockWise();
        pushing = true;
        try {
            pushAt(level, core.relative(side, -1).relative(facing, -1), side.getOpposite());
            pushAt(level, core.relative(side, -1).relative(facing, 1), side.getOpposite());
            pushAt(level, core.relative(side, 1).relative(facing, -1), side);
            pushAt(level, core.relative(side, 1).relative(facing, 1), side);
            pushAt(level, core.relative(side, -1).relative(facing, -1), facing.getOpposite());
            pushAt(level, core.relative(side, 1).relative(facing, -1), facing.getOpposite());
            pushAt(level, core.relative(side, -1).relative(facing, 1), facing);
            pushAt(level, core.relative(side, 1).relative(facing, 1), facing);
        } finally {
            pushing = false;
        }
    }

    private void pushAt(ServerLevel level, BlockPos proxy, Direction outward) {
        if (tank.isEmpty()) return;
        BlockPos target = proxy.relative(outward);
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, target, outward.getOpposite());
        if (handler == null) return;
        int amount = Math.min(tank.getFluidAmount(), Math.max(500, tank.getFluidAmount() / 100));
        FluidStack offered = tank.getFluid().copyWithAmount(amount);
        int accepted = handler.fill(offered, IFluidHandler.FluidAction.EXECUTE);
        if (accepted > 0) tank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
    }

    private void leak(ServerLevel level, BlockPos position) {
        FluidTankProperties.Profile profile = FluidTankProperties.get(selection);
        int leakRate = profile.gaseous() ? Math.max(CAPACITY / 100, 1)
                : Math.max(CAPACITY / 10_000, 1);
        int amount = Math.min(tank.getFluidAmount(), leakRate);
        if (amount <= 0) return;
        tank.drain(amount, IFluidHandler.FluidAction.EXECUTE);

        if (profile.flammable() && onFire) {
            igniteNearby(level, position);
            level.sendParticles(ParticleTypes.FLAME,
                    position.getX() + level.random.nextDouble(),
                    position.getY() + .5D + level.random.nextDouble(),
                    position.getZ() + level.random.nextDouble(),
                    1, .2D, .1D, .2D, .02D);
            if (level.getGameTime() % 5L == 0L && profile.burnSootPerMb() > 0F) {
                PollutionData.get(level).increment(position, PollutionData.Type.SOOT,
                        profile.burnSootPerMb() * amount * 5F);
            }
        } else if (profile.gaseous() && level.getGameTime() % 5L == 0L) {
            spawnGasParticle(level, position);
            if (profile.spillPoisonPerMb() > 0F) {
                PollutionData.get(level).increment(position, PollutionData.Type.POISON,
                        profile.spillPoisonPerMb() * amount * 5F);
            }
        }
    }

    private static void igniteNearby(ServerLevel level, BlockPos position) {
        AABB area = new AABB(position.getX() - 1.5D, position.getY(), position.getZ() - 1.5D,
                position.getX() + 2.5D, position.getY() + 5D, position.getZ() + 2.5D);
        for (Entity entity : level.getEntities((Entity) null, area, Entity::isAlive)) {
            entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), 100));
        }
    }

    private void spawnGasParticle(ServerLevel level, BlockPos position) {
        int color = selection.color();
        Vector3f rgb = new Vector3f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F,
                (color & 255) / 255F);
        level.sendParticles(new DustParticleOptions(rgb, 2F),
                position.getX() + .5D, position.getY() + 1D, position.getZ() + .5D,
                6, .35D, .75D, .35D, .03D);
    }

    private void syncIfChanged(ServerLevel level, BlockPos position, BlockState state) {
        if (tank.getFluidAmount() != lastAmount || mode != lastMode || damaged != lastDamaged
                || onFire != lastOnFire || selection != lastSelection || level.getGameTime() % 20L == 0L) {
            lastAmount = tank.getFluidAmount();
            lastMode = mode;
            lastDamaged = damaged;
            lastOnFire = onFire;
            lastSelection = selection;
            level.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS);
            level.updateNeighbourForOutputSignal(position, state.getBlock());
        }
        setChanged();
    }

    public void selectFluid(@Nullable FluidIdentifierItem.Selection newSelection) {
        if (newSelection == null) newSelection = FluidIdentifierItem.Selection.NONE;
        if (selection == newSelection) return;
        selection = newSelection;
        tank.setFluid(FluidStack.EMPTY);
        setChanged();
    }

    public void cycleMode() { mode = (mode + 1) & 3; setChanged(); }
    public FluidIdentifierItem.Selection selection() { return selection; }
    public FluidTank tank() { return tank; }
    public int mode() { return mode; }
    public boolean damaged() { return damaged; }
    public boolean onFire() { return onFire; }
    public IFluidHandler fluidHandler() { return fluidHandler; }
    public ContainerData dataAccess() { return data; }
    public int comparatorSignal() {
        return tank.isEmpty() ? 0 : Math.clamp((int) ((long) tank.getFluidAmount() * 15L / CAPACITY) + 1, 1, 15);
    }

    /** First blast dents it. Second blast settles the argument. */
    public boolean damageFrom(Explosion explosion) {
        if (lastExplosion == explosion) return true;
        lastExplosion = explosion;
        if (damaged) return false;
        damaged = true;
        onFire = FluidTankProperties.get(selection).flammable();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        return true;
    }

    /** Fixes the shell. The remembered fire is somebody else's problem. */
    public boolean repair() {
        if (!damaged) return false;
        damaged = false;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        return true;
    }

    public boolean tryExtinguish(ExtinguishType type) {
        if (!damaged || !onFire || level == null) return false;
        FluidTankProperties.Profile profile = FluidTankProperties.get(selection);
        if (type == ExtinguishType.WATER) {
            if (profile.liquid()) {
                if (!level.isClientSide) level.explode(null,
                        worldPosition.getX() + .5D, worldPosition.getY() + 1.5D, worldPosition.getZ() + .5D,
                        5F, true, Level.ExplosionInteraction.TNT);
                return true;
            }
            onFire = false;
        } else if (type == ExtinguishType.FOAM || type == ExtinguishType.CO2) {
            onFire = false;
        } else {
            return false;
        }
        setChanged();
        if (!level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                Block.UPDATE_CLIENTS);
        return true;
    }

    public enum ExtinguishType { WATER, FOAM, SAND, CO2 }

    public ItemStack machineDrop() {
        ItemStack stack = new ItemStack(ModItems.MACHINE_FLUIDTANK_ITEM.get());
        if (customName != null) stack.set(DataComponents.CUSTOM_NAME, customName);
        if (tank.isEmpty() && !damaged) return stack;
        CompoundTag data = new CompoundTag();
        data.putString(ITEM_FLUID, selection.id());
        data.putInt(ITEM_AMOUNT, tank.getFluidAmount());
        data.putInt(ITEM_MODE, mode);
        data.putBoolean(ITEM_DAMAGED, damaged);
        data.putBoolean(ITEM_ON_FIRE, onFire);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    public void restoreFromItem(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains(ITEM_FLUID)) return;
        selection = FluidIdentifierItem.Selection.byId(data.getString(ITEM_FLUID));
        int amount = Math.clamp(data.getInt(ITEM_AMOUNT), 0, CAPACITY);
        tank.setFluid(selection == FluidIdentifierItem.Selection.NONE || amount == 0
                ? FluidStack.EMPTY : new FluidStack(selection.fluid(), amount));
        mode = Math.clamp(data.getInt(ITEM_MODE), MODE_INPUT, MODE_LOCKED);
        damaged = data.getBoolean(ITEM_DAMAGED);
        onFire = data.getBoolean(ITEM_ON_FIRE);
        conformTank();
        setChanged();
    }

    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.fluidtank");
    }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new FluidStorageTankMenu(id, inventory, this, data);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putString("selectedFluid", selection.id());
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("mode", mode);
        tag.putBoolean("exploded", damaged);
        tag.putBoolean("onFire", onFire);
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        selection = FluidIdentifierItem.Selection.byId(tag.getString("selectedFluid"));
        if (tag.contains("tank")) tank.readFromNBT(registries, tag.getCompound("tank"));
        mode = Math.clamp(tag.getInt("mode"), MODE_INPUT, MODE_LOCKED);
        damaged = tag.getBoolean("exploded");
        onFire = tag.getBoolean("onFire");
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
        conformTank();
    }

    private void conformTank() {
        if (!tank.isEmpty() && !selection.accepts(tank.getFluid().getFluid())) tank.setFluid(FluidStack.EMPTY);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("selectedFluid", selection.id());
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("mode", mode);
        tag.putBoolean("exploded", damaged);
        tag.putBoolean("onFire", onFire);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        selection = FluidIdentifierItem.Selection.byId(tag.getString("selectedFluid"));
        if (tag.contains("tank")) tank.readFromNBT(registries, tag.getCompound("tank"));
        mode = Math.clamp(tag.getInt("mode"), MODE_INPUT, MODE_LOCKED);
        damaged = tag.getBoolean("exploded");
        onFire = tag.getBoolean("onFire");
        conformTank();
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static boolean canMerge(ItemStack existing, ItemStack addition) {
        return !addition.isEmpty() && (existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, addition)
                && existing.getCount() + addition.getCount() <= existing.getMaxStackSize());
    }
    private void mergeInto(int slot, ItemStack addition) {
        ItemStack existing = items.get(slot);
        if (existing.isEmpty()) items.set(slot, addition.copy()); else existing.grow(addition.getCount());
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack result = ContainerHelper.removeItem(items, slot, count);
        if (!result.isEmpty()) setChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getCenter()) <= 128D;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == IDENTIFIER_INPUT) return stack.getItem() instanceof FluidIdentifierItem;
        IFluidHandlerItem handler = stack.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) return false;
        if (slot == FILLED_INPUT) {
            if (InfiniteFluidBarrelItem.is(stack)) return selection != FluidIdentifierItem.Selection.NONE;
            FluidStack fluid = handler.drain(CAPACITY, IFluidHandler.FluidAction.SIMULATE);
            return !fluid.isEmpty() && (selection == FluidIdentifierItem.Selection.NONE
                    || selection.accepts(fluid.getFluid()));
        }
        return slot == EMPTY_INPUT && !tank.isEmpty()
                && handler.fill(tank.getFluid().copy(), IFluidHandler.FluidAction.SIMULATE) > 0;
    }
    @Override public int[] getSlotsForFace(Direction side) { return NO_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }
}
