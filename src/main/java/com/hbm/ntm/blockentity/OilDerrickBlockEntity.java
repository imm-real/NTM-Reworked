package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.OilDerrickBlock;
import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.energy.HeReceiver;
import com.hbm.ntm.explosion.ChargeExplosion;
import com.hbm.ntm.inventory.OilDerrickMenu;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.item.InfiniteFluidBarrelItem;
import com.hbm.ntm.item.MachineUpgradeItem;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.worldgen.LegacyWorldgenHeights;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/** Oil Derrick. Turns geology and electricity into buckets. */
public final class OilDerrickBlockEntity extends BlockEntity implements Container, MenuProvider, HeReceiver {
    public static final int BATTERY = 0;
    public static final int OIL_CONTAINER_INPUT = 1;
    public static final int OIL_CONTAINER_OUTPUT = 2;
    public static final int GAS_CONTAINER_INPUT = 3;
    public static final int GAS_CONTAINER_OUTPUT = 4;
    public static final int UPGRADE_START = 5;
    public static final int UPGRADE_END = 8;
    public static final int SLOT_COUNT = 8;
    public static final int TANK_CAPACITY = 64_000;
    public static final int DRILL_DEPTH = 5;
    public static final int TRACE_DEPTH = 64;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final FluidTank oil = new FluidTank(TANK_CAPACITY,
            stack -> stack.getFluid().isSame(ModFluids.OIL.get())) {
        @Override protected void onContentsChanged() { OilDerrickBlockEntity.this.setChanged(); }
    };
    private final FluidTank gas = new FluidTank(TANK_CAPACITY,
            stack -> stack.getFluid().isSame(ModFluids.GAS.get())) {
        @Override protected void onContentsChanged() { OilDerrickBlockEntity.this.setChanged(); }
    };
    private final IFluidHandler outputHandler = new IFluidHandler() {
        @Override public int getTanks() { return 2; }
        @Override public FluidStack getFluidInTank(int tank) { return tank == 0 ? oil.getFluid() : gas.getFluid(); }
        @Override public int getTankCapacity(int tank) { return TANK_CAPACITY; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }
        @Override public int fill(FluidStack resource, FluidAction action) { return 0; }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.getFluid().isSame(ModFluids.OIL.get())) return oil.drain(resource, action);
            if (resource.getFluid().isSame(ModFluids.GAS.get())) return gas.drain(resource, action);
            return FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            return oil.isEmpty() ? gas.drain(maxDrain, action) : oil.drain(maxDrain, action);
        }
    };
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int) power;
                case 1 -> (int) (power >>> 32);
                case 2 -> (int) maxPower();
                case 3 -> (int) (maxPower() >>> 32);
                case 4 -> indicator;
                case 5 -> oil.getFluidAmount();
                case 6 -> gas.getFluidAmount();
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> power = power & 0xFFFFFFFF00000000L | value & 0xFFFFFFFFL;
                case 1 -> power = power & 0xFFFFFFFFL | (long) value << 32;
                case 4 -> indicator = value;
                default -> { }
            }
        }
        @Override public int getCount() { return 7; }
    };
    private final Set<BlockPos> trace = new HashSet<>();

    private Component customName;
    private long power;
    private int indicator;
    private boolean fluidExplosionTriggered;
    private long lastPower = Long.MIN_VALUE;
    private int lastOil = Integer.MIN_VALUE;
    private int lastGas = Integer.MIN_VALUE;
    private int lastIndicator = Integer.MIN_VALUE;

    public OilDerrickBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_WELL.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, OilDerrickBlockEntity derrick) {
        if (!level.isClientSide) derrick.serverTick((ServerLevel) level, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        updateConnections(level, pos);
        unloadTankIntoItem(OIL_CONTAINER_INPUT, OIL_CONTAINER_OUTPUT, oil);
        unloadTankIntoItem(GAS_CONTAINER_INPUT, GAS_CONTAINER_OUTPUT, gas);

        int speedLevel = upgradeLevel(MachineUpgradeItem.Type.SPEED);
        int energyLevel = upgradeLevel(MachineUpgradeItem.Type.POWER);
        int overdriveLevel = upgradeLevel(MachineUpgradeItem.Type.OVERDRIVE);
        int afterburnLevel = upgradeLevel(MachineUpgradeItem.Type.AFTERBURN);

        burnGas(afterburnLevel);
        dischargeBattery();
        pushFluids(level, pos);

        int required = effectivePowerRequirement(speedLevel, energyLevel, overdriveLevel);
        int delay = effectiveDelay(speedLevel, energyLevel, overdriveLevel);
        if (power >= required && oil.getFluidAmount() < TANK_CAPACITY && gas.getFluidAmount() < TANK_CAPACITY) {
            power -= required;
            if (level.getGameTime() % delay == 0L) {
                indicator = 0;
                runDrillCycle(level, pos);
            }
        } else {
            indicator = 2;
        }
        syncIfChanged(level, pos, state);
    }

    private void updateConnections(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) trySubscribe(level, pos.relative(direction), direction);
    }

    private void unloadTankIntoItem(int inputSlot, int outputSlot, FluidTank tank) {
        ItemStack input = items.get(inputSlot);
        if (input.isEmpty() || tank.isEmpty()) return;
        if (InfiniteFluidBarrelItem.is(input)) {
            if (InfiniteFluidBarrelItem.discardTank(tank) > 0) setChanged();
            return;
        }
        FluidActionResult simulated = FluidUtil.tryFillContainer(input.copyWithCount(1), tank,
                Integer.MAX_VALUE, null, false);
        if (!simulated.isSuccess() || !canMerge(items.get(outputSlot), simulated.getResult())) return;
        FluidActionResult executed = FluidUtil.tryFillContainer(input.copyWithCount(1), tank,
                Integer.MAX_VALUE, null, true);
        if (!executed.isSuccess()) return;
        input.shrink(1);
        if (input.isEmpty()) items.set(inputSlot, ItemStack.EMPTY);
        mergeOutput(outputSlot, executed.getResult());
        setChanged();
    }

    private static boolean canMerge(ItemStack target, ItemStack addition) {
        if (addition.isEmpty()) return false;
        return target.isEmpty() || ItemStack.isSameItemSameComponents(target, addition)
                && target.getCount() + addition.getCount() <= target.getMaxStackSize();
    }

    private void mergeOutput(int slot, ItemStack addition) {
        ItemStack target = items.get(slot);
        if (target.isEmpty()) items.set(slot, addition.copy());
        else target.grow(addition.getCount());
    }

    private void burnGas(int level) {
        int toBurn = Math.min(gas.getFluidAmount(), level * 10);
        if (toBurn <= 0) return;
        gas.drain(toBurn, IFluidHandler.FluidAction.EXECUTE);
        power = Math.min(maxPower(), power + toBurn * 5L);
    }

    private void dischargeBattery() {
        ItemStack stack = items.get(BATTERY);
        if (!(stack.getItem() instanceof HeBatteryItem battery)) return;
        long amount = Math.min(Math.min(maxPower() - power, battery.getDischargeRate(stack)), battery.getCharge(stack));
        if (amount > 0L) {
            battery.discharge(stack, amount);
            power += amount;
        }
    }

    private void pushFluids(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos target = pos.relative(direction);
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                    target, direction.getOpposite());
            if (handler == null) continue;
            pushTank(oil, handler);
            pushTank(gas, handler);
        }
    }

    private static void pushTank(FluidTank tank, IFluidHandler handler) {
        if (tank.isEmpty()) return;
        int accepted = handler.fill(tank.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
        if (accepted > 0) tank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
    }

    private void runDrillCycle(ServerLevel level, BlockPos pos) {
        int drillDepth = LegacyWorldgenHeights.aboveBottom(level, DRILL_DEPTH);
        for (int y = pos.getY() - 1; y >= drillDepth; y--) {
            BlockPos target = new BlockPos(pos.getX(), y, pos.getZ());
            if (!level.getBlockState(target).is(ModBlocks.OIL_PIPE.get())) {
                if (!trySuck(level, target)) tryDrill(level, target);
                break;
            }
            if (y == drillDepth) indicator = 1;
        }
    }

    private void tryDrill(ServerLevel level, BlockPos target) {
        BlockState state = level.getBlockState(target);
        if (state.getBlock().getExplosionResistance() < 1_000F) {
            if (isAsbestosDrillSource(state)) emitAsbestosDrillingGas(level);
            // TODO dense radon, because asbestos was not enough
            level.setBlock(target, ModBlocks.OIL_PIPE.get().defaultBlockState(), Block.UPDATE_ALL);
        } else {
            indicator = 2;
        }
    }

    private static boolean isAsbestosDrillSource(BlockState state) {
        // Metadata was forgotten here, so Chrysotile gets away clean.
        return state.is(ModBlocks.legacy("ore_asbestos").get())
                || state.is(ModBlocks.legacy("ore_gneiss_asbestos").get());
    }

    private void emitAsbestosDrillingGas(ServerLevel level) {
        BlockPos top = worldPosition.above(10);
        // j/j and k/k: three diagonals tried three times. Quality drilling.
        for (int j = -1; j <= 1; j++) for (int k = -1; k <= 1; k++) {
            if (level.getBlockState(top.offset(j, 0, j)).canBeReplaced()) {
                level.setBlock(top.offset(k, 0, k),
                        ModBlocks.legacy("gas_asbestos").get().defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private boolean trySuck(ServerLevel level, BlockPos target) {
        BlockState state = level.getBlockState(target);
        if (!state.is(ModBlocks.ORE_OIL.get()) && !state.is(ModBlocks.ORE_OIL_EMPTY.get())) return false;
        trace.clear();
        return suckRecursive(level, target, 0);
    }

    private boolean suckRecursive(ServerLevel level, BlockPos target, int layer) {
        BlockPos immutable = target.immutable();
        if (!trace.add(immutable) || layer > TRACE_DEPTH) return false;
        BlockState state = level.getBlockState(target);
        if (state.is(ModBlocks.ORE_OIL.get())) {
            onSuck(level, target);
            return true;
        }
        if (!state.is(ModBlocks.ORE_OIL_EMPTY.get())) return false;
        Direction[] directions = Direction.values().clone();
        shuffle(directions, level.random);
        for (Direction direction : directions) {
            if (suckRecursive(level, target.relative(direction), layer + 1)) return true;
        }
        return false;
    }

    private static void shuffle(Direction[] directions, RandomSource random) {
        for (int i = directions.length - 1; i > 0; i--) {
            int swap = random.nextInt(i + 1);
            Direction value = directions[i];
            directions[i] = directions[swap];
            directions[swap] = value;
        }
    }

    private void onSuck(ServerLevel level, BlockPos deposit) {
        level.playSound(null, worldPosition, SoundEvents.GENERIC_SWIM, SoundSource.BLOCKS, 2.0F, 0.5F);
        oil.fill(new FluidStack(ModFluids.OIL.get(), HbmConfig.DERRICK_OIL_PER_DEPOSIT.get()),
                IFluidHandler.FluidAction.EXECUTE);
        int minimum = Math.min(HbmConfig.DERRICK_GAS_PER_DEPOSIT_MIN.get(),
                HbmConfig.DERRICK_GAS_PER_DEPOSIT_MAX.get());
        int maximum = Math.max(HbmConfig.DERRICK_GAS_PER_DEPOSIT_MIN.get(),
                HbmConfig.DERRICK_GAS_PER_DEPOSIT_MAX.get());
        int gasAmount = minimum + (maximum == minimum ? 0 : level.random.nextInt(maximum - minimum + 1));
        gas.fill(new FluidStack(ModFluids.GAS.get(), gasAmount), IFluidHandler.FluidAction.EXECUTE);
        if (level.random.nextDouble() < HbmConfig.DERRICK_DRAIN_CHANCE.get()) {
            level.setBlock(deposit, ModBlocks.ORE_OIL_EMPTY.get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private int upgradeLevel(MachineUpgradeItem.Type type) {
        int value = 0;
        for (int slot = UPGRADE_START; slot < UPGRADE_END; slot++) {
            if (items.get(slot).getItem() instanceof MachineUpgradeItem upgrade && upgrade.type() == type) {
                value += upgrade.level();
            }
        }
        return Math.min(value, 3);
    }

    public static int effectivePowerRequirement(int speed, int energy, int overdrive) {
        int base = HbmConfig.DERRICK_CONSUMPTION.get();
        return Math.max((base + base / 4 * speed - base / 4 * energy) * (overdrive + 1), 0);
    }

    public static int effectiveDelay(int speed, int energy, int overdrive) {
        int base = HbmConfig.DERRICK_DELAY.get();
        return Math.max((base - base / 4 * speed + base / 10 * energy) / (overdrive + 1), 1);
    }

    private void syncIfChanged(ServerLevel level, BlockPos pos, BlockState state) {
        if (power != lastPower || oil.getFluidAmount() != lastOil || gas.getFluidAmount() != lastGas
                || indicator != lastIndicator || level.getGameTime() % 20L == 0L) {
            lastPower = power;
            lastOil = oil.getFluidAmount();
            lastGas = gas.getFluidAmount();
            lastIndicator = indicator;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
        setChanged();
    }

    public long maxPower() { return HbmConfig.DERRICK_POWER_CAPACITY.get(); }
    public FluidTank oilTank() { return oil; }
    public FluidTank gasTank() { return gas; }
    public IFluidHandler fluidHandler(@Nullable Direction side) { return outputHandler; }
    public int indicator() { return indicator; }

    public void detonateStoredFluids() {
        if (fluidExplosionTriggered || oil.isEmpty() && gas.isEmpty() || !(level instanceof ServerLevel server)) return;
        fluidExplosionTriggered = true;
        oil.setFluid(FluidStack.EMPTY);
        gas.setFluid(FluidStack.EMPTY);
        setChanged();
        ChargeExplosion.detonateNoDrop(server, worldPosition, 15F, 24);
    }

    public ItemStack machineDrop() {
        ItemStack stack = new ItemStack(ModItems.MACHINE_WELL_ITEM.get());
        if (power == 0L && oil.isEmpty() && gas.isEmpty()) return stack;
        CompoundTag data = new CompoundTag();
        data.putLong(OilDerrickBlock.POWER, power);
        data.putInt(OilDerrickBlock.OIL, oil.getFluidAmount());
        data.putInt(OilDerrickBlock.GAS, gas.getFluidAmount());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    public void restoreFromItem(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains(OilDerrickBlock.POWER)) return;
        power = Math.min(Math.max(data.getLong(OilDerrickBlock.POWER), 0L), maxPower());
        int storedOil = Math.clamp(data.getInt(OilDerrickBlock.OIL), 0, TANK_CAPACITY);
        int storedGas = Math.clamp(data.getInt(OilDerrickBlock.GAS), 0, TANK_CAPACITY);
        oil.setFluid(storedOil == 0 ? FluidStack.EMPTY : new FluidStack(ModFluids.OIL.get(), storedOil));
        gas.setFluid(storedGas == 0 ? FluidStack.EMPTY : new FluidStack(ModFluids.GAS.get(), storedGas));
        setChanged();
    }

    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.oilWell");
    }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new OilDerrickMenu(id, inventory, this, data);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putLong("power", power);
        tag.putInt("indicator", indicator);
        tag.put("oil", oil.writeToNBT(registries, new CompoundTag()));
        tag.put("gas", gas.writeToNBT(registries, new CompoundTag()));
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        power = Math.min(Math.max(tag.getLong("power"), 0L), maxPower());
        indicator = tag.getInt("indicator");
        if (tag.contains("oil")) oil.readFromNBT(registries, tag.getCompound("oil"));
        if (tag.contains("gas")) gas.readFromNBT(registries, tag.getCompound("gas"));
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("power", power);
        tag.putInt("indicator", indicator);
        tag.put("oil", oil.writeToNBT(registries, new CompoundTag()));
        tag.put("gas", gas.writeToNBT(registries, new CompoundTag()));
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        power = tag.getLong("power");
        indicator = tag.getInt("indicator");
        if (tag.contains("oil")) oil.readFromNBT(registries, tag.getCompound("oil"));
        if (tag.contains("gas")) gas.readFromNBT(registries, tag.getCompound("gas"));
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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
        if (level != null && !level.isClientSide && slot >= UPGRADE_START && slot < UPGRADE_END
                && stack.getItem() instanceof MachineUpgradeItem) {
            level.playSound(null, worldPosition.above(), ModSounds.UPGRADE_PLUG.get(), SoundSource.BLOCKS, 1F, 1F);
        }
        setChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getCenter()) <= 128D;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == BATTERY) return stack.getItem() instanceof HeBatteryItem;
        if (slot == OIL_CONTAINER_INPUT) return canFillWith(stack, ModFluids.OIL.get());
        if (slot == GAS_CONTAINER_INPUT) return canFillWith(stack, ModFluids.GAS.get());
        if (slot >= UPGRADE_START && slot < UPGRADE_END) return stack.getItem() instanceof MachineUpgradeItem;
        return false;
    }

    private static boolean canFillWith(ItemStack stack, net.minecraft.world.level.material.Fluid fluid) {
        IFluidHandlerItem handler = stack.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
        return handler != null && handler.fill(new FluidStack(fluid, 1_000),
                IFluidHandler.FluidAction.SIMULATE) == 1_000;
    }

    @Override public long getPower() { return power; }
    @Override public void setPower(long power) { this.power = Math.clamp(power, 0L, maxPower()); setChanged(); }
    @Override public long getMaxPower() { return maxPower(); }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }

    public void runDrillCycleForTest(ServerLevel level) { runDrillCycle(level, worldPosition); }
    public void addFluidsForTest(int oilAmount, int gasAmount) {
        oil.fill(new FluidStack(ModFluids.OIL.get(), oilAmount), IFluidHandler.FluidAction.EXECUTE);
        gas.fill(new FluidStack(ModFluids.GAS.get(), gasAmount), IFluidHandler.FluidAction.EXECUTE);
    }
    public void burnGasForTest(int level) { burnGas(level); }
}
