package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.BlastFurnaceBlock;
import com.hbm.ntm.inventory.BlastFurnaceMenu;
import com.hbm.ntm.pollution.PollutionData;
import com.hbm.ntm.recipe.BlastFurnaceRecipes;
import com.hbm.ntm.recipe.BlastFurnaceRecipes.BlastFurnaceRecipe;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.thermal.FireboxFuel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/** Fuel, progress, and recipe loop for the Steel-producing Blast Furnace. */
public final class BlastFurnaceBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SLOT_COUNT = 5;
    public static final int FUEL = 0;
    public static final int INPUT_FIRST = 1;
    public static final int INPUT_SECOND = 2;
    public static final int OUTPUT_FIRST = 3;
    public static final int OUTPUT_SECOND = 4;
    public static final int FUEL_COAL = 1_600;
    public static final int FUEL_RATE = 800;
    public static final int MAX_FUEL = 38_400;
    public static final int AIR_CAPACITY = 4_000;
    public static final int FLUE_CAPACITY = 1_000;
    public static final int FLUE_PER_OPERATION = 100;
    /** Source FLUE release trait: SOOT_PER_SECOND (1/25) * 0.005 * 25 per spilled mB. */
    public static final float FLUE_SOOT_PER_MB = 0.005F;
    private static final int[] AUTOMATION_SLOTS = {INPUT_FIRST, INPUT_SECOND, FUEL, OUTPUT_FIRST, OUTPUT_SECOND};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final FluidTank air = new FluidTank(AIR_CAPACITY,
            stack -> !stack.isEmpty() && stack.is(ModFluids.AIRBLAST.get())) {
        @Override protected void onContentsChanged() { BlastFurnaceBlockEntity.this.setChanged(); }
    };
    private final FluidTank flue = new FluidTank(FLUE_CAPACITY,
            stack -> !stack.isEmpty() && stack.is(ModFluids.FLUE.get())) {
        @Override protected void onContentsChanged() { BlastFurnaceBlockEntity.this.setChanged(); }
    };
    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return 2; }
        @Override public FluidStack getFluidInTank(int tank) {
            return tank == 0 ? air.getFluid() : tank == 1 ? flue.getFluid() : FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int tank) {
            return tank == 0 ? AIR_CAPACITY : tank == 1 ? FLUE_CAPACITY : 0;
        }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && stack.is(ModFluids.AIRBLAST.get());
        }
        @Override public int fill(FluidStack resource, FluidAction action) { return air.fill(resource, action); }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            return flue.drain(resource, action);
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return flue.drain(maxDrain, action); }
    };
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int) Math.round(progress * 1_000_000D);
                case 1 -> (int) Math.round(speed * 1_000D);
                case 2 -> fuel;
                case 3 -> air.getFluidAmount();
                case 4 -> flue.getFluidAmount();
                case 5 -> progressing ? 1 : 0;
                default -> 0;
            };
        }

        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = value / 1_000_000D;
                case 1 -> speed = value / 1_000D;
                case 2 -> fuel = value;
                case 3 -> air.setFluid(value <= 0 ? FluidStack.EMPTY
                        : new FluidStack(ModFluids.AIRBLAST.get(), value));
                case 4 -> flue.setFluid(value <= 0 ? FluidStack.EMPTY
                        : new FluidStack(ModFluids.FLUE.get(), value));
                case 5 -> progressing = value != 0;
                default -> { }
            }
        }

        @Override public int getCount() { return 6; }
    };

    private Component customName;
    private double progress;
    private double speed;
    private int fuel;
    private boolean progressing;
    private int lastProgress = Integer.MIN_VALUE;
    private int lastSpeed = Integer.MIN_VALUE;
    private int lastFuel = Integer.MIN_VALUE;
    private int lastAir = Integer.MIN_VALUE;
    private int lastFlue = Integer.MIN_VALUE;
    private boolean lastProgressing;

    public BlastFurnaceBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_BLAST_FURNACE.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, BlastFurnaceBlockEntity furnace) {
        if (level.isClientSide) furnace.clientTick(level, position);
        else furnace.serverTick((ServerLevel) level, position, state);
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        pushFlue(level, position, state);
        loadFuel();
        speed = 0D;
        BlastFurnaceRecipe recipe = BlastFurnaceRecipes.find(items.get(INPUT_FIRST), items.get(INPUT_SECOND));
        if (recipe != null && canProcess(recipe)) {
            speed = Mth.clamp(0.5D + air.getFluidAmount() * 8D / AIR_CAPACITY, 0.5D, 5D);
            progressing = true;
            progress += speed / recipe.duration();
            if (progress >= 1D) {
                process(level, position, recipe);
                progress = 0D;
            }
            if (level.random.nextInt(10) == 0) {
                level.playSound(null, position, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS,
                        1.0F, 0.5F + level.random.nextFloat() * 0.25F);
            }
        } else {
            progressing = false;
            progress = 0D;
        }

        if (!air.isEmpty()) {
            int remaining = (int) (air.getFluidAmount() * 0.95D);
            air.setFluid(remaining <= 0 ? FluidStack.EMPTY
                    : new FluidStack(ModFluids.AIRBLAST.get(), remaining));
        }
        syncIfChanged(level, position, state);
        setChanged();
    }

    private void loadFuel() {
        ItemStack stack = items.get(FUEL);
        if (stack.isEmpty()) return;
        int value = burnTime(stack);
        if (value <= 0 || value > MAX_FUEL - fuel) return;
        fuel += value;
        stack.shrink(1);
        if (stack.isEmpty()) items.set(FUEL, ItemStack.EMPTY);
    }

    public static int burnTime(ItemStack stack) {
        if (stack.isEmpty() || stack.hasCraftingRemainingItem()
                || !FireboxFuel.isBlastFurnaceFuel(stack)) return 0;
        return FireboxFuel.rawBurnTime(stack);
    }

    public boolean canProcess(BlastFurnaceRecipe recipe) {
        if (recipe == null || fuel < FUEL_RATE
                || !BlastFurnaceRecipes.inputsMatch(recipe, items.get(INPUT_FIRST), items.get(INPUT_SECOND))) {
            return false;
        }
        return canOutput(OUTPUT_FIRST, recipe.primary()) && canOutput(OUTPUT_SECOND, recipe.secondary());
    }

    private boolean canOutput(int slot, ItemStack output) {
        if (output.isEmpty()) return true;
        ItemStack existing = items.get(slot);
        return existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, output)
                && existing.getCount() + output.getCount() <= existing.getMaxStackSize();
    }

    private void process(ServerLevel level, BlockPos position, BlastFurnaceRecipe recipe) {
        addOutput(OUTPUT_FIRST, recipe.primary());
        addOutput(OUTPUT_SECOND, recipe.secondary());
        ItemStack first = items.get(INPUT_FIRST);
        ItemStack second = items.get(INPUT_SECOND);
        if (recipe.first().matches(first) && recipe.second().matches(second)) {
            consume(INPUT_FIRST, recipe.first().count());
            consume(INPUT_SECOND, recipe.second().count());
        } else {
            consume(INPUT_FIRST, recipe.second().count());
            consume(INPUT_SECOND, recipe.first().count());
        }
        fuel -= FUEL_RATE;
        int accepted = flue.fill(new FluidStack(ModFluids.FLUE.get(), FLUE_PER_OPERATION),
                IFluidHandler.FluidAction.EXECUTE);
        int overflow = FLUE_PER_OPERATION - accepted;
        if (overflow > 0) {
            PollutionData.get(level).increment(position, PollutionData.Type.SOOT,
                    overflow * FLUE_SOOT_PER_MB);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, position.getX() + 0.5D,
                    position.getY() + 7D, position.getZ() + 0.5D,
                    Math.max(1, overflow / 20), 0.2D, 0.2D, 0.2D, 0.01D);
        }
    }

    private void addOutput(int slot, ItemStack output) {
        if (output.isEmpty()) return;
        ItemStack existing = items.get(slot);
        if (existing.isEmpty()) items.set(slot, output.copy());
        else existing.grow(output.getCount());
    }

    private void consume(int slot, int count) {
        ItemStack stack = items.get(slot);
        stack.shrink(count);
        if (stack.isEmpty()) items.set(slot, ItemStack.EMPTY);
    }

    private void pushFlue(ServerLevel level, BlockPos core, BlockState state) {
        if (flue.isEmpty()) return;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            pushTo(level, core.relative(direction, 2), direction);
        }
        Direction facing = state.getValue(BlastFurnaceBlock.FACING);
        pushTo(level, core.relative(facing, 2).above(3), facing);
        pushTo(level, core.relative(facing, 2).above(5), facing);
        pushTo(level, core.above(7), Direction.UP);
    }

    private void pushTo(ServerLevel level, BlockPos target, Direction outward) {
        if (flue.isEmpty()) return;
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                target, outward.getOpposite());
        if (handler == null) return;
        int accepted = handler.fill(flue.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
        if (accepted > 0) flue.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
    }

    private void clientTick(Level level, BlockPos position) {
        if (!progressing || !level.getBlockState(position.above(7)).isAir()) return;
        if ((level.getGameTime() & 1L) == 0L) {
            level.addParticle(ParticleTypes.LAVA,
                    position.getX() + 0.25D + level.random.nextDouble() * 0.5D,
                    position.getY() + 7.25D,
                    position.getZ() + 0.25D + level.random.nextDouble() * 0.5D,
                    0D, 0D, 0D);
            if (flue.getFluidAmount() >= 100) {
                level.addParticle(ParticleTypes.LARGE_SMOKE,
                        position.getX() + 0.5D + (level.random.nextDouble() - 0.5D) * 0.25D,
                        position.getY() + 7D,
                        position.getZ() + 0.5D + (level.random.nextDouble() - 0.5D) * 0.25D,
                        0D, 0.1D, 0D);
            }
        }
    }

    private void syncIfChanged(ServerLevel level, BlockPos position, BlockState state) {
        int progressValue = (int) Math.round(progress * 1_000_000D);
        int speedValue = (int) Math.round(speed * 1_000D);
        if (progressValue != lastProgress || speedValue != lastSpeed || fuel != lastFuel
                || air.getFluidAmount() != lastAir || flue.getFluidAmount() != lastFlue
                || progressing != lastProgressing || level.getGameTime() % 20L == 0L) {
            lastProgress = progressValue;
            lastSpeed = speedValue;
            lastFuel = fuel;
            lastAir = air.getFluidAmount();
            lastFlue = flue.getFluidAmount();
            lastProgressing = progressing;
            level.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public double progress() { return progress; }
    public double speed() { return speed; }
    public int fuel() { return fuel; }
    public boolean progressing() { return progressing; }
    public FluidTank airTank() { return air; }
    public FluidTank flueTank() { return flue; }
    public IFluidHandler fluidHandler() { return fluidHandler; }
    public ContainerData data() { return data; }

    public void setFuelForTest(int value) { fuel = Mth.clamp(value, 0, MAX_FUEL); }
    public void setProgressForTest(double value) { progress = Mth.clamp(value, 0D, 1D); }

    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.blastFurnace");
    }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new BlastFurnaceMenu(id, inventory, this, data);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putDouble("progress", progress);
        tag.putDouble("speed", speed);
        tag.putInt("fuel", fuel);
        tag.putBoolean("progressing", progressing);
        tag.put("air", air.writeToNBT(registries, new CompoundTag()));
        tag.put("flue", flue.writeToNBT(registries, new CompoundTag()));
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        progress = tag.getDouble("progress");
        speed = tag.getDouble("speed");
        fuel = Mth.clamp(tag.getInt("fuel"), 0, MAX_FUEL);
        progressing = tag.getBoolean("progressing");
        if (tag.contains("air")) air.readFromNBT(registries, tag.getCompound("air"));
        if (tag.contains("flue")) flue.readFromNBT(registries, tag.getCompound("flue"));
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("progress", progress);
        tag.putDouble("speed", speed);
        tag.putInt("fuel", fuel);
        tag.putBoolean("progressing", progressing);
        tag.put("air", air.writeToNBT(registries, new CompoundTag()));
        tag.put("flue", flue.writeToNBT(registries, new CompoundTag()));
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        progress = tag.getDouble("progress");
        speed = tag.getDouble("speed");
        fuel = tag.getInt("fuel");
        progressing = tag.getBoolean("progressing");
        if (tag.contains("air")) air.readFromNBT(registries, tag.getCompound("air"));
        if (tag.contains("flue")) flue.readFromNBT(registries, tag.getCompound("flue"));
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() {
        for (ItemStack stack : items) if (!stack.isEmpty()) return false;
        return true;
    }
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
        return slot == FUEL ? burnTime(stack) > 0 : slot == INPUT_FIRST || slot == INPUT_SECOND;
    }
    @Override public int[] getSlotsForFace(Direction side) { return AUTOMATION_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        if (slot == FUEL) return burnTime(stack) > 0;
        if (slot != INPUT_FIRST && slot != INPUT_SECOND || !BlastFurnaceRecipes.isValidInput(stack)) return false;
        int other = slot == INPUT_FIRST ? INPUT_SECOND : INPUT_FIRST;
        return items.get(other).isEmpty() || !ItemStack.isSameItemSameComponents(items.get(other), stack);
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= OUTPUT_FIRST && slot <= OUTPUT_SECOND;
    }
}
