package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.AssemblyMachineBlock;
import com.hbm.ntm.energy.HeReceiver;
import com.hbm.ntm.inventory.AssemblyMachineMenu;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.item.BlueprintItem;
import com.hbm.ntm.item.MachineUpgradeItem;
import com.hbm.ntm.recipe.AssemblyRecipe;
import com.hbm.ntm.recipe.AssemblyRecipes;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public final class AssemblyMachineBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, HeReceiver {
    public static final int BATTERY = 0;
    public static final int BLUEPRINT = 1;
    public static final int UPGRADE_START = 2;
    public static final int UPGRADE_END = 4;
    public static final int INPUT_START = 4;
    public static final int INPUT_END = 16;
    public static final int OUTPUT = 16;
    public static final int SLOT_COUNT = 17;
    public static final long MIN_POWER = 100_000L;
    public static final int BASE_TANK_CAPACITY = 4_000;
    private static final int[] AUTOMATION_SLOTS = createAutomationSlots();

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final FluidTank inputTank = new FluidTank(BASE_TANK_CAPACITY) {
        @Override protected void onContentsChanged() { AssemblyMachineBlockEntity.this.setChanged(); }
    };
    private final FluidTank outputTank = new FluidTank(BASE_TANK_CAPACITY) {
        @Override protected void onContentsChanged() { AssemblyMachineBlockEntity.this.setChanged(); }
    };
    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return 2; }
        @Override public FluidStack getFluidInTank(int tank) { return tank == 0 ? inputTank.getFluid() : outputTank.getFluid(); }
        @Override public int getTankCapacity(int tank) { return tank == 0 ? inputTank.getCapacity() : outputTank.getCapacity(); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return tank == 0 && inputTank.isFluidValid(stack); }
        @Override public int fill(FluidStack stack, FluidAction action) { return inputTank.fill(stack, action); }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) { return outputTank.drain(stack, action); }
        @Override public FluidStack drain(int amount, FluidAction action) { return outputTank.drain(amount, action); }
    };
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int) Math.round(progress * 1_000_000D);
                case 1 -> (int) power;
                case 2 -> (int) (power >>> 32);
                case 3 -> (int) maxPower;
                case 4 -> (int) (maxPower >>> 32);
                case 5 -> active ? 1 : 0;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = value / 1_000_000D;
                case 1 -> power = power & 0xFFFFFFFF00000000L | value & 0xFFFFFFFFL;
                case 2 -> power = power & 0xFFFFFFFFL | (long) value << 32;
                case 3 -> maxPower = maxPower & 0xFFFFFFFF00000000L | value & 0xFFFFFFFFL;
                case 4 -> maxPower = maxPower & 0xFFFFFFFFL | (long) value << 32;
                case 5 -> active = value != 0;
                default -> { }
            }
        }
        @Override public int getCount() { return 6; }
    };

    private Component customName;
    private long power;
    private long maxPower = MIN_POWER;
    private double progress;
    private ResourceLocation recipeId;
    private boolean restrictedMode;
    private boolean active;
    private long lastSyncedPower = Long.MIN_VALUE;
    private double lastSyncedProgress = Double.NaN;
    private boolean lastSyncedActive;
    private final AssemblerArm[] arms = {new AssemblerArm(), new AssemblerArm()};
    private double previousRing;
    private double ring;
    private double ringSpeed;
    private double ringTarget;
    private int ringDelay;
    private boolean frame;

    public AssemblyMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_ASSEMBLY_MACHINE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AssemblyMachineBlockEntity machine) {
        if (level.isClientSide) machine.clientTick();
        else machine.serverTick((ServerLevel) level, pos, state);
    }

    private void clientTick() {
        if (level == null) return;
        if (level.getGameTime() % 20L == 0L) frame = !level.getBlockState(worldPosition.above(3)).isAir();
        for (AssemblerArm arm : arms) {
            arm.copyPrevious();
            if (active) arm.updateActive(); else arm.returnToIdle();
            if (arm.struckThisTick()) {
                level.playLocalSound(worldPosition, level.random.nextBoolean()
                                ? com.hbm.ntm.registry.ModSounds.ASSEMBLER_STRIKE_1.get()
                                : com.hbm.ntm.registry.ModSounds.ASSEMBLER_STRIKE_2.get(),
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 1F, false);
            }
        }
        previousRing = ring;
        if (!active) return;
        if (ring != ringTarget) {
            double delta = Math.abs(ringTarget - ring);
            if (delta <= ringSpeed) ring = ringTarget;
            if (ringTarget > ring) ring += ringSpeed;
            if (ringTarget < ring) ring -= ringSpeed;
            if (ringTarget == ring) {
                double wrap = ringTarget >= 360D ? -360D : 360D;
                ringTarget += wrap; ring += wrap; previousRing += wrap;
                ringDelay = 20 + level.random.nextInt(21);
            }
        } else {
            // Zero means choose the next target now, not next tick.
            if (ringDelay > 0) ringDelay--;
            if (ringDelay <= 0) {
                ringTarget += (level.random.nextDouble() * 2D - 1D) * 135D;
                ringSpeed = 10D + level.random.nextDouble() * 5D;
                level.playLocalSound(worldPosition, com.hbm.ntm.registry.ModSounds.ASSEMBLER_START.get(),
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.25F,
                        1.25F + level.random.nextFloat() * 0.25F, false);
            }
        }
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        AssemblyRecipe recipe = selectedRecipe();
        maxPower = capacityFor(recipe, power, maxPower);
        dischargeBattery();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Direction clockwise = direction.getClockWise();
            for (int offset = -1; offset <= 1; offset++) {
                BlockPos connection = pos.relative(direction, 2).relative(clockwise, offset);
                trySubscribe(level, connection, direction);
                if (!outputTank.isEmpty()) {
                    IFluidHandler handler = level.getCapability(
                            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                            connection, direction.getOpposite());
                    if (handler != null) {
                        FluidStack available = outputTank.getFluid().copy();
                        int accepted = handler.fill(available, IFluidHandler.FluidAction.EXECUTE);
                        if (accepted > 0) outputTank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
                    }
                }
            }
        }

        boolean switched = false;
        if (recipe != null && !recipe.pools().isEmpty()
                && !recipe.pools().contains(BlueprintItem.pool(items.get(BLUEPRINT)))) {
            recipeId = null;
            recipe = null;
            progress = 0D;
        }
        if (recipe != null && recipe.autoswitch().isPresent() && !items.get(INPUT_START).isEmpty()) {
            for (AssemblyRecipe candidate : AssemblyRecipes.all()) {
                if (!candidate.id().equals(recipe.id())
                        && candidate.autoswitch().equals(recipe.autoswitch())
                        && !candidate.inputs().isEmpty()
                        && candidate.inputs().getFirst().ingredient().test(items.get(INPUT_START))) {
                    recipeId = candidate.id();
                    recipe = candidate;
                    configureTanks(candidate);
                    progress = 0D;
                    switched = true;
                    break;
                }
            }
        }
        double speedMultiplier = speedMultiplier();
        double powerMultiplier = powerMultiplier();

        active = !switched && recipe != null && canProcess(recipe, powerMultiplier);
        if (active) {
            long consumption = powerMultiplier == 1D ? recipe.power() : (long) (recipe.power() * powerMultiplier);
            power -= consumption;
            progress += Math.min(speedMultiplier / recipe.duration(), 1D);
            if (progress >= 1D) {
                consumeInputs(recipe);
                produceOutputs(recipe);
                if (canProcess(recipe, powerMultiplier)) progress -= 1D;
                else progress = 0D;
            }
        } else {
            progress = 0D;
        }

        boolean sync = power != lastSyncedPower || progress != lastSyncedProgress || active != lastSyncedActive
                || level.getGameTime() % 20L == 0L;
        if (sync) {
            lastSyncedPower = power;
            lastSyncedProgress = progress;
            lastSyncedActive = active;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
        setChanged();
    }

    public static long capacityFor(@Nullable AssemblyRecipe recipe, long storedPower, long previousCapacity) {
        long capacity = previousCapacity <= 0L ? 1_000_000L : previousCapacity;
        if (recipe != null) capacity = recipe.power() * 100L;
        return Math.max(Math.max(storedPower, capacity), MIN_POWER);
    }

    private void dischargeBattery() {
        ItemStack stack = items.get(BATTERY);
        if (!(stack.getItem() instanceof HeBatteryItem battery)) return;
        long amount = Math.min(Math.min(maxPower - power, battery.getDischargeRate(stack)), battery.getCharge(stack));
        if (amount > 0L) { battery.discharge(stack, amount); power += amount; }
    }

    public double speedMultiplier() {
        double speed = 1D + upgradeLevel(MachineUpgradeItem.Type.SPEED) / 3D
                + upgradeLevel(MachineUpgradeItem.Type.OVERDRIVE);
        return restrictedMode ? speed * 0.25D : speed;
    }

    public double powerMultiplier() {
        return 1D - upgradeLevel(MachineUpgradeItem.Type.POWER) * 0.25D
                + upgradeLevel(MachineUpgradeItem.Type.SPEED)
                + upgradeLevel(MachineUpgradeItem.Type.OVERDRIVE) * 10D / 3D;
    }

    private int upgradeLevel(MachineUpgradeItem.Type type) {
        int level = 0;
        for (int slot = UPGRADE_START; slot < UPGRADE_END; slot++) {
            if (items.get(slot).getItem() instanceof MachineUpgradeItem upgrade && upgrade.type() == type) {
                level += upgrade.level();
            }
        }
        return Math.min(level, 3);
    }

    public boolean canProcess(AssemblyRecipe recipe, double powerMultiplier) {
        long consumption = powerMultiplier == 1D ? recipe.power() : (long) (recipe.power() * powerMultiplier);
        if (power < consumption) return false;
        for (int lane = 0; lane < recipe.inputs().size(); lane++) {
            if (!recipe.inputs().get(lane).matches(items.get(INPUT_START + lane))) return false;
        }
        if (recipe.fluidInput().isPresent()) {
            AssemblyRecipe.FluidIo fluid = recipe.fluidInput().get();
            if (!BuiltInRegistries.FLUID.getKey(inputTank.getFluid().getFluid()).equals(fluid.fluid())
                    || inputTank.getFluidAmount() < fluid.amount()) return false;
        }
        ItemStack result = items.get(OUTPUT);
        if (!result.isEmpty() && (!ItemStack.isSameItemSameComponents(result, recipe.output())
                || result.getCount() + recipe.output().getCount() > result.getMaxStackSize())) return false;
        if (recipe.fluidOutput().isPresent()) {
            AssemblyRecipe.FluidIo fluid = recipe.fluidOutput().get();
            if (!outputTank.isEmpty() && !BuiltInRegistries.FLUID.getKey(outputTank.getFluid().getFluid()).equals(fluid.fluid())) return false;
            if (outputTank.getFluidAmount() + fluid.amount() > outputTank.getCapacity()) return false;
        }
        return true;
    }

    private void consumeInputs(AssemblyRecipe recipe) {
        for (int lane = 0; lane < recipe.inputs().size(); lane++) removeItem(INPUT_START + lane, recipe.inputs().get(lane).count());
        recipe.fluidInput().ifPresent(fluid -> inputTank.drain(fluid.amount(), IFluidHandler.FluidAction.EXECUTE));
    }

    private void produceOutputs(AssemblyRecipe recipe) {
        ItemStack existing = items.get(OUTPUT);
        if (existing.isEmpty()) items.set(OUTPUT, recipe.output().copy());
        else existing.grow(recipe.output().getCount());
        recipe.fluidOutput().ifPresent(fluid -> outputTank.fill(new FluidStack(
                BuiltInRegistries.FLUID.get(fluid.fluid()), fluid.amount()), IFluidHandler.FluidAction.EXECUTE));
    }

    public boolean selectRecipe(ResourceLocation id, boolean restricted) {
        AssemblyRecipe recipe = AssemblyRecipes.get(id);
        if (recipe == null || !recipe.pools().isEmpty()
                && !recipe.pools().contains(BlueprintItem.pool(items.get(BLUEPRINT)))) return false;
        recipeId = id;
        restrictedMode = restricted;
        progress = 0D;
        configureTanks(recipe);
        setChanged();
        return true;
    }

    public void clearRecipe() {
        recipeId = null;
        restrictedMode = false;
        progress = 0D;
        active = false;
        setChanged();
    }

    private void configureTanks(AssemblyRecipe recipe) {
        int inputCapacity = recipe.fluidInput().map(f -> Math.max(BASE_TANK_CAPACITY, f.amount() * 2)).orElse(BASE_TANK_CAPACITY);
        int outputCapacity = recipe.fluidOutput().map(f -> Math.max(BASE_TANK_CAPACITY, f.amount() * 2)).orElse(BASE_TANK_CAPACITY);
        inputTank.setCapacity(Math.max(inputTank.getFluidAmount(), inputCapacity));
        outputTank.setCapacity(Math.max(outputTank.getFluidAmount(), outputCapacity));
        inputTank.setValidator(stack -> recipe.fluidInput().isPresent()
                && BuiltInRegistries.FLUID.getKey(stack.getFluid()).equals(recipe.fluidInput().get().fluid()));
    }

    @Nullable public AssemblyRecipe selectedRecipe() { return recipeId == null ? null : AssemblyRecipes.get(recipeId); }
    @Nullable public ResourceLocation recipeId() { return recipeId; }
    public double progress() { return progress; }
    public boolean active() { return active; }
    public FluidTank inputTank() { return inputTank; }
    public FluidTank outputTank() { return outputTank; }
    public IFluidHandler fluidHandler() { return fluidHandler; }
    public ContainerData data() { return data; }
    public AssemblerArm arm(int index) { return arms[index]; }
    public double interpolatedRing(float partialTick) { return previousRing + (ring - previousRing) * partialTick; }
    public boolean hasFrame() { return frame; }

    @Override public long getPower() { return power; }
    @Override public void setPower(long power) { this.power = Math.max(0L, Math.min(power, maxPower)); }
    @Override public long getMaxPower() { return maxPower; }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }

    public static final class AssemblerArm {
        private static final double[][] POSITIONS = {
                {45D, -15D, -5D}, {15D, 15D, -15D}, {25D, 10D, -15D},
                {30D, 0D, -10D}, {70D, -10D, -25D}
        };
        private final double[] angles = new double[4];
        private final double[] previous = new double[4];
        private final double[] target = new double[4];
        private final double[] speed = new double[4];
        private final java.util.Random random = new java.util.Random();
        private State state = State.ASSUME_POSITION;
        private int delay;
        private boolean struck;

        private void copyPrevious() { System.arraycopy(angles, 0, previous, 0, 4); struck = false; }
        private void updateActive() {
            Arrays.fill(speed, 15D); speed[3] = 0.5D;
            if (delay > 0) { delay--; return; }
            switch (state) {
                case ASSUME_POSITION -> {
                    if (move()) { delay = 2; state = State.EXTEND_STRIKER; target[3] = -0.75D; }
                }
                case EXTEND_STRIKER -> {
                    if (move()) { state = State.RETRACT_STRIKER; target[3] = 0D; }
                    struck = previous[3] != angles[3] && angles[3] == -0.75D;
                }
                case RETRACT_STRIKER -> {
                    if (move()) {
                        delay = 2 + random.nextInt(5);
                        double[] chosen = POSITIONS[random.nextInt(POSITIONS.length)];
                        System.arraycopy(chosen, 0, target, 0, 3);
                        state = State.ASSUME_POSITION;
                    }
                }
            }
        }
        private void returnToIdle() {
            Arrays.fill(target, 0D); Arrays.fill(speed, 3D); speed[3] = 0.25D;
            state = State.RETRACT_STRIKER; move();
        }
        private boolean move() {
            boolean moved = false;
            for (int i = 0; i < 4; i++) {
                if (angles[i] == target[i]) continue;
                moved = true;
                double delta = Math.abs(angles[i] - target[i]);
                if (delta <= speed[i]) angles[i] = target[i];
                else angles[i] += angles[i] < target[i] ? speed[i] : -speed[i];
            }
            return !moved;
        }
        public double value(int index, float partialTick) {
            return previous[index] + (angles[index] - previous[index]) * partialTick;
        }
        private boolean struckThisTick() { return struck; }
        private enum State { ASSUME_POSITION, EXTEND_STRIKER, RETRACT_STRIKER }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putLong("power", power);
        tag.putLong("maxPower", maxPower);
        tag.putDouble("progress", progress);
        tag.putBoolean("restrictedMode", restrictedMode);
        if (recipeId != null) tag.putString("recipe", recipeId.toString());
        tag.put("inputTank", inputTank.writeToNBT(registries, new CompoundTag()));
        tag.put("outputTank", outputTank.writeToNBT(registries, new CompoundTag()));
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        power = tag.getLong("power");
        maxPower = Math.max(MIN_POWER, tag.getLong("maxPower"));
        progress = tag.getDouble("progress");
        restrictedMode = tag.getBoolean("restrictedMode");
        recipeId = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
        if (tag.contains("inputTank")) inputTank.readFromNBT(registries, tag.getCompound("inputTank"));
        if (tag.contains("outputTank")) outputTank.readFromNBT(registries, tag.getCompound("outputTank"));
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("power", power); tag.putLong("maxPower", maxPower); tag.putDouble("progress", progress);
        tag.putBoolean("active", active);
        if (recipeId != null) tag.putString("recipe", recipeId.toString());
        tag.put("inputTank", inputTank.writeToNBT(registries, new CompoundTag()));
        tag.put("outputTank", outputTank.writeToNBT(registries, new CompoundTag()));
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        boolean wasActive = active;
        power = tag.getLong("power"); maxPower = tag.getLong("maxPower"); progress = tag.getDouble("progress");
        active = tag.getBoolean("active");
        if (wasActive && !active && level != null) {
            level.playLocalSound(worldPosition, com.hbm.ntm.registry.ModSounds.ASSEMBLER_STOP.get(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.25F, 1.5F, false);
        }
        recipeId = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
        if (tag.contains("inputTank")) inputTank.readFromNBT(registries, tag.getCompound("inputTank"));
        if (tag.contains("outputTank")) outputTank.readFromNBT(registries, tag.getCompound("outputTank"));
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        // loadAdditional only knows save data and drops the active flag.
        // Runtime packets take the chunk-tag route so animation and sound survive.
        handleUpdateTag(packet.getTag(), registries);
    }

    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.machineAssemblyMachine");
    }
    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AssemblyMachineMenu(id, inventory, this, data);
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count); if (!removed.isEmpty()) setChanged(); return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack); if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize()); setChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getCenter()) <= 128D;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == BATTERY) return true;
        if (slot == BLUEPRINT) return stack.is(ModItems.BLUEPRINTS.get());
        if (slot >= UPGRADE_START && slot < UPGRADE_END) return stack.getItem() instanceof MachineUpgradeItem;
        if (slot >= INPUT_START && slot < INPUT_END) return isValidForLane(slot - INPUT_START, stack);
        return false;
    }

    public boolean isValidForLane(int lane, ItemStack stack) {
        AssemblyRecipe recipe = selectedRecipe();
        if (recipe == null || lane >= recipe.inputs().size()) return false;
        if (recipe.inputs().get(lane).ingredient().test(stack)) return true;
        if (lane == 0 && recipe.autoswitch().isPresent()) {
            for (AssemblyRecipe candidate : AssemblyRecipes.all()) {
                if (candidate.autoswitch().equals(recipe.autoswitch()) && !candidate.inputs().isEmpty()
                        && candidate.inputs().getFirst().ingredient().test(stack)) return true;
            }
        }
        return false;
    }

    public boolean isClogged(int slot) {
        return slot >= INPUT_START && slot < INPUT_END && !items.get(slot).isEmpty()
                && !isValidForLane(slot - INPUT_START, items.get(slot));
    }

    @Override public int[] getSlotsForFace(Direction side) { return AUTOMATION_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot >= INPUT_START && slot < INPUT_END && canPlaceItem(slot, stack);
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == OUTPUT || isClogged(slot);
    }

    private static int[] createAutomationSlots() {
        int[] slots = new int[13]; for (int i = 0; i < slots.length; i++) slots[i] = INPUT_START + i; return slots;
    }
}
