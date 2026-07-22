package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.ZirnoxBlock;
import com.hbm.ntm.block.ZirnoxDestroyedBlock;
import com.hbm.ntm.explosion.MultiBombExplosion;
import com.hbm.ntm.inventory.ZirnoxMenu;
import com.hbm.ntm.item.InfiniteFluidBarrelItem;
import com.hbm.ntm.item.ZirnoxRodItem;
import com.hbm.ntm.recipe.ZirnoxRecipes;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.ror.RorFunctionException;
import com.hbm.ntm.ror.RorInteractive;
import com.hbm.ntm.ror.RorValueProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/** Gas-cooled ZIRNOX reactor. Eggs go in, consequences come out. */
public final class ZirnoxBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider, RorValueProvider, RorInteractive {
    public static final int FUEL_SLOTS = 24;
    public static final int CO2_INPUT = 24;
    public static final int WATER_INPUT = 25;
    public static final int CO2_OUTPUT = 26;
    public static final int WATER_OUTPUT = 27;
    public static final int SLOT_COUNT = 28;
    public static final int MAX_HEAT = 100_000;
    public static final int MAX_PRESSURE = 100_000;
    public static final int STEAM_CAPACITY = 8_000;
    public static final int CO2_CAPACITY = 16_000;
    public static final int WATER_CAPACITY = 32_000;

    private static final int[][] NEIGHBORS = {
            {1,7},{0,2,8},{1,9},{4,10},{3,5,11},{4,6,12},{5,13},
            {0,8,14},{1,7,9,15},{2,8,16},{3,11,17},{4,10,12,18},{5,11,13,19},{6,12,20},
            {7,15,21},{8,14,16,22},{9,15,23},{10,18},{11,17,19},{12,18,20},{13,19},
            {14,22},{15,21,23},{16,22}
    };
    private static final int[] FUEL_AUTOMATION = java.util.stream.IntStream.range(0, FUEL_SLOTS).toArray();

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final FluidTank steam = tank(STEAM_CAPACITY, ModFluids.SUPERHOTSTEAM.get());
    private final FluidTank carbonDioxide = tank(CO2_CAPACITY, ModFluids.CARBONDIOXIDE.get());
    private final FluidTank water = tank(WATER_CAPACITY, Fluids.WATER);
    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return 3; }
        @Override public FluidStack getFluidInTank(int tank) {
            return switch (tank) { case 0 -> carbonDioxide.getFluid().copy(); case 1 -> water.getFluid().copy();
                case 2 -> steam.getFluid().copy(); default -> FluidStack.EMPTY; };
        }
        @Override public int getTankCapacity(int tank) {
            return switch (tank) { case 0 -> CO2_CAPACITY; case 1 -> WATER_CAPACITY; case 2 -> STEAM_CAPACITY; default -> 0; };
        }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 ? stack.getFluid().isSame(ModFluids.CARBONDIOXIDE.get())
                    : tank == 1 && stack.getFluid().isSame(Fluids.WATER);
        }
        @Override public int fill(FluidStack resource, FluidAction action) {
            if (resource.getFluid().isSame(ModFluids.CARBONDIOXIDE.get())) return carbonDioxide.fill(resource, action);
            if (resource.getFluid().isSame(Fluids.WATER)) return water.fill(resource, action);
            return 0;
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            return resource.getFluid().isSame(ModFluids.SUPERHOTSTEAM.get()) ? steam.drain(resource, action) : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return steam.drain(maxDrain, action); }
    };

    private int heat;
    private int pressure;
    private boolean on;
    private boolean redstone;
    private int lastHeat = Integer.MIN_VALUE;
    private int lastPressure = Integer.MIN_VALUE;
    private int lastSteam = Integer.MIN_VALUE;
    private int lastCo2 = Integer.MIN_VALUE;
    private int lastWater = Integer.MIN_VALUE;
    private boolean lastOn;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return switch (index) {
            case 0 -> heat; case 1 -> pressure; case 2 -> on ? 1 : 0; case 3 -> redstone ? 1 : 0;
            case 4 -> steam.getFluidAmount(); case 5 -> carbonDioxide.getFluidAmount(); case 6 -> water.getFluidAmount();
            case 7 -> MAX_HEAT; case 8 -> MAX_PRESSURE; case 9 -> activeRodCount(); default -> 0; }; }
        @Override public void set(int index, int value) { switch (index) {
            case 0 -> heat = value; case 1 -> pressure = value; case 2 -> on = value != 0; case 3 -> redstone = value != 0;
            case 4 -> clientTank(steam, ModFluids.SUPERHOTSTEAM.get(), value);
            case 5 -> clientTank(carbonDioxide, ModFluids.CARBONDIOXIDE.get(), value);
            case 6 -> clientTank(water, Fluids.WATER, value); default -> { } } }
        @Override public int getCount() { return 10; }
    };

    public ZirnoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REACTOR_ZIRNOX.get(), pos, state);
    }

    private FluidTank tank(int capacity, net.minecraft.world.level.material.Fluid fluid) {
        return new FluidTank(capacity, stack -> stack.getFluid().isSame(fluid)) {
            @Override protected void onContentsChanged() { ZirnoxBlockEntity.this.setChanged(); }
        };
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ZirnoxBlockEntity reactor) {
        if (!level.isClientSide) reactor.serverTick((ServerLevel) level, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        loadContainer(CO2_INPUT, CO2_OUTPUT, carbonDioxide, ModFluids.CARBONDIOXIDE.get());
        loadContainer(WATER_INPUT, WATER_OUTPUT, water, Fluids.WATER);
        pullPorts(level, pos, state.getValue(ZirnoxBlock.FACING));

        boolean powered = false;
        powerScan: for (int dx = -2; dx <= 2; dx++) for (int dy = 0; dy <= 4; dy++)
            for (int dz = -2; dz <= 2; dz++) {
                if (dx != -2 && dx != 2 && dy != 0 && dy != 4 && dz != -2 && dz != 2) continue;
                if (level.hasNeighborSignal(pos.offset(dx, dy, dz))) { powered = true; break powerScan; }
            }
        if (powered) on = true;
        else if (redstone) on = false;
        redstone = powered;

        if (on) decayRods();
        pressure = carbonDioxide.getFluidAmount() * 2
                + (int) (heat * (carbonDioxide.getFluidAmount() / (float) CO2_CAPACITY));
        if (heat > 0 && heat < MAX_HEAT) {
            boolean cooled = !water.isEmpty() && !carbonDioxide.isEmpty() && steam.getSpace() > 0;
            if (cooled) generateSteam();
            int cooling = cooled
                    ? (int) (heat * pressure / 1_000_000F) : 10;
            heat -= cooling;
        }
        pushSteam(level, pos, state.getValue(ZirnoxBlock.FACING));
        if (heat > MAX_HEAT || pressure > MAX_PRESSURE) { meltdown(level, pos, state); return; }
        sync(level, pos, state);
    }

    private void decayRods() {
        for (int slot = 0; slot < FUEL_SLOTS; slot++) {
            ItemStack stack = items.get(slot);
            if (!(stack.getItem() instanceof ZirnoxRodItem rod)) continue;
            int reactions = rod.type().breeding() ? 0 : 1;
            for (int neighbor : NEIGHBORS[slot]) {
                if (items.get(neighbor).getItem() instanceof ZirnoxRodItem neighborRod
                        && !neighborRod.type().breeding()) reactions++;
            }
            for (int reaction = 0; reaction < reactions; reaction++) {
                heat += rod.type().heat();
                if (rod.advance(stack, 1) > rod.type().maxLife()) {
                    items.set(slot, ZirnoxRecipes.burnResult(rod.type()));
                    break;
                }
            }
        }
    }

    private void generateSteam() {
        if (heat <= 10_256) return;
        int cycle = (int) (((heat - 10_256) / 100_000F)
                * Math.min(carbonDioxide.getFluidAmount() / 14_000F, 1F) * 25F * 7.5F);
        if (cycle <= 0) return;
        water.setFluid(new FluidStack(Fluids.WATER, Math.max(water.getFluidAmount() - cycle, 0)));
        steam.setFluid(new FluidStack(ModFluids.SUPERHOTSTEAM.get(),
                Math.min(steam.getFluidAmount() + cycle, STEAM_CAPACITY)));
    }

    private void pullPorts(ServerLevel level, BlockPos core, Direction facing) {
        for (ZirnoxBlock.Connection connection : ZirnoxBlock.connections(core, facing)) {
            IFluidHandler neighbor = level.getCapability(Capabilities.FluidHandler.BLOCK,
                    connection.target(), connection.outward().getOpposite());
            if (neighbor == null) continue;
            pull(neighbor, carbonDioxide, ModFluids.CARBONDIOXIDE.get());
            pull(neighbor, water, Fluids.WATER);
        }
    }

    private void pushSteam(ServerLevel level, BlockPos core, Direction facing) {
        if (steam.isEmpty()) return;
        for (ZirnoxBlock.Connection connection : ZirnoxBlock.connections(core, facing)) {
            IFluidHandler neighbor = level.getCapability(Capabilities.FluidHandler.BLOCK,
                    connection.target(), connection.outward().getOpposite());
            if (neighbor == null) continue;
            if (!steam.isEmpty()) {
                int moved = neighbor.fill(steam.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
                if (moved > 0) steam.drain(moved, IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    private static void pull(IFluidHandler neighbor, FluidTank tank, net.minecraft.world.level.material.Fluid fluid) {
        if (tank.getSpace() <= 0) return;
        FluidStack available = neighbor.drain(new FluidStack(fluid, tank.getSpace()), IFluidHandler.FluidAction.SIMULATE);
        if (available.isEmpty() || !available.getFluid().isSame(fluid)) return;
        int accepted = tank.fill(available, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) return;
        FluidStack drained = neighbor.drain(new FluidStack(fluid, accepted), IFluidHandler.FluidAction.EXECUTE);
        tank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
    }

    private void loadContainer(int inputSlot, int outputSlot, FluidTank tank,
                               net.minecraft.world.level.material.Fluid fluid) {
        ItemStack source = items.get(inputSlot);
        if (source.isEmpty()) return;
        if (InfiniteFluidBarrelItem.is(source)) {
            if (InfiniteFluidBarrelItem.fillTank(tank, fluid) > 0) setChanged();
            return;
        }
        IFluidHandlerItem handler = source.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) return;
        FluidStack simulated = handler.drain(tank.getSpace(), IFluidHandler.FluidAction.SIMULATE);
        if (simulated.isEmpty() || !simulated.getFluid().isSame(fluid)) return;
        int accepted = tank.fill(simulated, IFluidHandler.FluidAction.SIMULATE);
        if (accepted != simulated.getAmount()) return;
        FluidStack drained = handler.drain(simulated.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
        if (drained.getAmount() != accepted) return;
        ItemStack remainder = handler.getContainer().copy();
        if (!canMerge(items.get(outputSlot), remainder)) return;
        tank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        source.shrink(1);
        if (source.isEmpty()) items.set(inputSlot, ItemStack.EMPTY);
        if (!remainder.isEmpty()) {
            if (items.get(outputSlot).isEmpty()) items.set(outputSlot, remainder);
            else items.get(outputSlot).grow(remainder.getCount());
        }
        setChanged();
    }

    private static boolean canMerge(ItemStack target, ItemStack addition) {
        return addition.isEmpty() || target.isEmpty() || ItemStack.isSameItemSameComponents(target, addition)
                && target.getCount() + addition.getCount() <= target.getMaxStackSize();
    }

    private void meltdown(ServerLevel level, BlockPos pos, BlockState state) {
        items.clear();
        Direction facing = state.getValue(ZirnoxBlock.FACING);
        for (BlockPos part : ZirnoxBlock.partPositions(pos, facing)) {
            if (level.getBlockState(part).is(state.getBlock())) level.setBlock(part, Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        ZirnoxDestroyedBlock.place(level, pos, facing, com.hbm.ntm.registry.ModBlocks.ZIRNOX_DESTROYED.get());
        level.explode(null, pos.getX() + .5, pos.getY() + 3.0, pos.getZ() + .5,
                12.0F, true, Level.ExplosionInteraction.BLOCK);
        MultiBombExplosion.wasteNoSchrab(level, pos.getX(), pos.getY(), pos.getZ(), 35);
    }

    private void sync(ServerLevel level, BlockPos pos, BlockState state) {
        if (lastHeat != heat || lastPressure != pressure || lastSteam != steam.getFluidAmount()
                || lastCo2 != carbonDioxide.getFluidAmount() || lastWater != water.getFluidAmount()
                || lastOn != on || level.getGameTime() % 20 == 0) {
            lastHeat = heat; lastPressure = pressure; lastSteam = steam.getFluidAmount();
            lastCo2 = carbonDioxide.getFluidAmount(); lastWater = water.getFluidAmount(); lastOn = on;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
        setChanged();
    }

    public void toggle() { if (!redstone) { on = !on; setChanged(); } }
    public void vent() { carbonDioxide.drain(1_000, IFluidHandler.FluidAction.EXECUTE); setChanged(); }
    public IFluidHandler fluidHandler() { return fluidHandler; }
    public FluidTank steamTank() { return steam; }
    public FluidTank carbonDioxideTank() { return carbonDioxide; }
    public FluidTank waterTank() { return water; }
    public int heat() { return heat; }
    public int pressure() { return pressure; }
    public boolean isOn() { return on; }

    @Override public String[] rorInfo() {
        return new String[]{VALUE_PREFIX + "heat", VALUE_PREFIX + "pressure", VALUE_PREFIX + "water",
                VALUE_PREFIX + "steam", VALUE_PREFIX + "co2", VALUE_PREFIX + "state",
                FUNCTION_PREFIX + "setstate!active (0 or 1)", FUNCTION_PREFIX + "ventco2"};
    }

    @Override public String provideRorValue(String name) {
        if ((VALUE_PREFIX + "heat").equals(name)) return Integer.toString((int) Math.round(heat * 0.0078D + 20D));
        if ((VALUE_PREFIX + "pressure").equals(name)) return Integer.toString((int) Math.round(pressure * 0.0003D));
        if ((VALUE_PREFIX + "water").equals(name)) return Integer.toString(water.getFluidAmount());
        if ((VALUE_PREFIX + "steam").equals(name)) return Integer.toString(steam.getFluidAmount());
        if ((VALUE_PREFIX + "co2").equals(name)) return Integer.toString(carbonDioxide.getFluidAmount());
        if ((VALUE_PREFIX + "state").equals(name)) return on ? "1" : "0";
        return null;
    }

    @Override public void runRorFunction(String name, String[] parameters) throws RorFunctionException {
        if ((FUNCTION_PREFIX + "setstate").equals(name)) {
            if (parameters.length == 0 || redstone) return;
            on = RorInteractive.integer(parameters[0], 0, 1) == 1;
            setChanged();
        } else if ((FUNCTION_PREFIX + "ventco2").equals(name)) {
            vent();
        }
    }
    private int activeRodCount() { int count = 0; for (int i = 0; i < FUEL_SLOTS; i++)
        if (items.get(i).getItem() instanceof ZirnoxRodItem) count++; return count; }
    public AABB renderBounds() { return new AABB(worldPosition).inflate(4, 1, 4).expandTowards(0, 6, 0); }

    private static void clientTank(FluidTank tank, net.minecraft.world.level.material.Fluid fluid, int amount) {
        tank.setFluid(amount <= 0 ? FluidStack.EMPTY : new FluidStack(fluid, Math.min(amount, tank.getCapacity())));
    }

    @Override public Component getDisplayName() { return Component.translatable("container.reactorZirnox"); }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ZirnoxMenu(id, inventory, this, data);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("heat", heat); tag.putInt("pressure", pressure); tag.putBoolean("on", on);
        tag.put("steam", steam.writeToNBT(registries, new CompoundTag()));
        tag.put("co2", carbonDioxide.writeToNBT(registries, new CompoundTag()));
        tag.put("water", water.writeToNBT(registries, new CompoundTag()));
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        heat = tag.getInt("heat"); pressure = tag.getInt("pressure"); on = tag.getBoolean("on");
        if (tag.contains("steam")) steam.readFromNBT(registries, tag.getCompound("steam"));
        if (tag.contains("co2")) carbonDioxide.readFromNBT(registries, tag.getCompound("co2"));
        if (tag.contains("water")) water.readFromNBT(registries, tag.getCompound("water"));
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag(); tag.putInt("heat", heat); tag.putInt("pressure", pressure);
        tag.putBoolean("on", on); tag.putInt("steam", steam.getFluidAmount());
        tag.putInt("co2", carbonDioxide.getFluidAmount()); tag.putInt("water", water.getFluidAmount()); return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        heat = tag.getInt("heat"); pressure = tag.getInt("pressure"); on = tag.getBoolean("on");
        clientTank(steam, ModFluids.SUPERHOTSTEAM.get(), tag.getInt("steam"));
        clientTank(carbonDioxide, ModFluids.CARBONDIOXIDE.get(), tag.getInt("co2"));
        clientTank(water, Fluids.WATER, tag.getInt("water"));
    }
    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) { ItemStack stack = ContainerHelper.removeItem(items, slot, count);
        if (!stack.isEmpty()) setChanged(); return stack; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack); if (stack.getCount() > getMaxStackSize())
        stack.setCount(getMaxStackSize()); setChanged(); }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this
            && player.distanceToSqr(worldPosition.getCenter()) <= 128D; }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public int[] getSlotsForFace(Direction side) { return FUEL_AUTOMATION; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot < FUEL_SLOTS && stack.getItem() instanceof ZirnoxRodItem;
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot < FUEL_SLOTS && !(stack.getItem() instanceof ZirnoxRodItem);
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot < FUEL_SLOTS) return stack.getItem() instanceof ZirnoxRodItem;
        if (slot == CO2_OUTPUT || slot == WATER_OUTPUT) return false;
        if (InfiniteFluidBarrelItem.is(stack)) return true;
        IFluidHandlerItem handler = stack.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) return false;
        net.minecraft.world.level.material.Fluid desired = slot == CO2_INPUT ? ModFluids.CARBONDIOXIDE.get() : Fluids.WATER;
        for (int i = 0; i < handler.getTanks(); i++) if (!handler.getFluidInTank(i).isEmpty()
                && handler.getFluidInTank(i).getFluid().isSame(desired)) return true;
        return false;
    }
}
